import AVFoundation
import CoreGraphics
import Foundation
import ScreenCaptureKit

enum SystemCaptureError: LocalizedError {
    case noDisplay
    case permissionDenied(String)

    var errorDescription: String? {
        switch self {
        case .noDisplay:
            return "No display available to capture audio from"
        case .permissionDenied(let detail):
            return "Screen Recording permission is required for system audio. \(detail)"
        }
    }
}

/// Captures what the Mac is playing, with no virtual audio driver involved.
///
/// This is the reason the Mac side is native. ScreenCaptureKit will hand over
/// the system audio mix directly, so there is no BlackHole to install, no
/// Multi-Output Device to build in Audio MIDI Setup, and no danger of the user
/// wondering why their speakers went silent. The cost is that macOS classes it
/// as screen recording, so it needs that permission even though no pixels are
/// ever looked at.
final class SystemAudioCapture: NSObject, AudioSource, SCStreamOutput, SCStreamDelegate {

    private(set) var format = StreamFormat(sampleRate: 48000, channels: 2, blockFrames: 480)
    var onBlock: ((Data) -> Void)?
    var onLevel: ((Float) -> Void)?
    var onFailure: ((Error) -> Void)?

    private let queue = DispatchQueue(label: "dev.audiobridge.systemcapture")
    private var stream: SCStream?
    private var accumulator = Data()
    private var starting = false

    /// Whether macOS will currently allow screen capture for this app.
    ///
    /// Asked through CoreGraphics rather than inferred from a ScreenCaptureKit
    /// failure: TCC records a code-signing requirement beside the permission,
    /// so re-signing the app invalidates a grant that still reads as "allowed"
    /// in System Settings. Only the preflight call knows the real answer.
    static var isAuthorised: Bool { CGPreflightScreenCaptureAccess() }

    func start() throws {
        guard stream == nil, !starting else { return }

        if !CGPreflightScreenCaptureAccess() {
            // Shows the system prompt the first time. Once macOS has recorded a
            // decision it returns immediately and the user has to go to
            // Settings, so the error below has to explain that.
            if !CGRequestScreenCaptureAccess() {
                throw SystemCaptureError.permissionDenied(
                    "If AudioBridge is already switched on there, switch it off and on again — "
                    + "macOS ties the permission to the app's signature, and updating the app "
                    + "invalidates it while still showing it as allowed."
                )
            }
        }

        starting = true
        accumulator.removeAll(keepingCapacity: true)

        Task { [weak self] in
            guard let self else { return }
            do {
                try await self.beginCapture()
            } catch {
                self.starting = false
                self.onFailure?(Self.describe(error))
            }
        }
    }

    private func beginCapture() async throws {
        let content = try await SCShareableContent.excludingDesktopWindows(
            false, onScreenWindowsOnly: false
        )
        guard let display = content.displays.first else { throw SystemCaptureError.noDisplay }

        let configuration = SCStreamConfiguration()
        configuration.capturesAudio = true
        configuration.sampleRate = format.sampleRate
        configuration.channelCount = format.channels
        // Never capture our own output: if the app is ever made to play audio,
        // including it here would be a feedback loop.
        configuration.excludesCurrentProcessAudio = true
        // Video cannot be switched off, only made irrelevant. A 2x2 frame twice
        // a second costs nothing and keeps the stream valid.
        configuration.width = 2
        configuration.height = 2
        configuration.minimumFrameInterval = CMTime(value: 1, timescale: 2)
        configuration.showsCursor = false

        let filter = SCContentFilter(display: display, excludingApplications: [], exceptingWindows: [])
        let stream = SCStream(filter: filter, configuration: configuration, delegate: self)
        try stream.addStreamOutput(self, type: .audio, sampleHandlerQueue: queue)
        try await stream.startCapture()

        self.stream = stream
        self.starting = false
        NSLog("[audio] capturing system audio at %d Hz, %dch", format.sampleRate, format.channels)
    }

    private static func describe(_ error: Error) -> Error {
        let text = error.localizedDescription
        if text.localizedCaseInsensitiveContains("declined")
            || text.localizedCaseInsensitiveContains("permission")
            || (error as NSError).domain == "com.apple.ScreenCaptureKit.SCStreamErrorDomain" {
            return SystemCaptureError.permissionDenied(
                "Enable AudioBridge in System Settings › Privacy & Security › Screen & System Audio "
                + "Recording, then press Start again. If it was working before an update, macOS ties "
                + "this permission to the app's signature and a new build resets it."
            )
        }
        return error
    }

    func stop() {
        starting = false
        guard let stream else { return }
        self.stream = nil
        Task { try? await stream.stopCapture() }
        accumulator.removeAll(keepingCapacity: false)
    }

    func stream(_ stream: SCStream, didStopWithError error: Error) {
        self.stream = nil
        onFailure?(Self.describe(error))
    }

    // MARK: sample handling

    func stream(_ stream: SCStream, didOutputSampleBuffer sampleBuffer: CMSampleBuffer,
                of outputType: SCStreamOutputType) {
        guard outputType == .audio, sampleBuffer.isValid else { return }
        try? sampleBuffer.withAudioBufferList { list, _ in
            consume(list)
        }
    }

    /// ScreenCaptureKit delivers planar Float32, one buffer per channel.
    private func consume(_ list: UnsafeMutableAudioBufferListPointer) {
        guard let firstBuffer = list.first,
              let firstData = firstBuffer.mData else { return }

        let frames = Int(firstBuffer.mDataByteSize) / MemoryLayout<Float32>.size
        guard frames > 0 else { return }

        let wanted = format.channels
        let planes: [UnsafePointer<Float32>] = (0..<wanted).map { channel in
            let source = channel < list.count ? channel : 0
            let data = list[source].mData ?? firstData
            return UnsafePointer(data.assumingMemoryBound(to: Float32.self))
        }

        var peak: Float = 0
        var pcm = Data(count: frames * wanted * 2)
        pcm.withUnsafeMutableBytes { raw in
            let out = raw.bindMemory(to: Int16.self)
            for frame in 0..<frames {
                for channel in 0..<wanted {
                    let sample = planes[channel][frame]
                    let magnitude = abs(sample)
                    if magnitude > peak { peak = magnitude }
                    out[frame * wanted + channel] = Int16(max(-1.0, min(1.0, sample)) * 32767.0)
                }
            }
        }

        accumulator.append(pcm)
        let blockBytes = format.blockBytes
        while accumulator.count >= blockBytes {
            let block = accumulator.prefix(blockBytes)
            accumulator.removeFirst(blockBytes)
            onBlock?(Data(block))
        }
        onLevel?(min(peak, 1.0))
    }
}
