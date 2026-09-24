import AVFoundation
import CoreAudio
import Foundation

enum CaptureError: LocalizedError {
    case noAudioUnit
    case deviceRejected(OSStatus)
    case unusableFormat(String)

    var errorDescription: String? {
        switch self {
        case .noAudioUnit: return "the input node has no audio unit"
        case .deviceRejected(let status): return "Core Audio refused the device (status \(status))"
        case .unusableFormat(let detail): return "unusable capture format: \(detail)"
        }
    }
}

/// Captures from a specific input device — normally BlackHole, which the Mac's
/// output is routed into.
///
/// Packets are emitted at a fixed size rather than whatever the hardware hands
/// over: Core Audio delivers whatever block it likes (often 512 or 4096 frames,
/// and not always the same one twice), and a sink's jitter buffer is much
/// happier with a steady packet cadence than with the truth.
final class DeviceCapture: AudioSource {

    private(set) var format: StreamFormat
    var onBlock: ((Data) -> Void)?
    var onLevel: ((Float) -> Void)?
    var onFailure: ((Error) -> Void)?

    private let engine = AVAudioEngine()
    private let deviceID: AudioDeviceID
    private var accumulator = Data()
    private var running = false

    init(deviceID: AudioDeviceID, blockFrames: Int = 480) {
        self.deviceID = deviceID
        self.format = StreamFormat(sampleRate: AudioDevices.nominalRate(deviceID),
                                   channels: 2, blockFrames: blockFrames)
    }

    func start() throws {
        guard !running else { return }

        // Must happen before the format is read: until the device is set, the
        // input node still describes the system default input.
        guard let unit = engine.inputNode.audioUnit else { throw CaptureError.noAudioUnit }
        var id = deviceID
        let status = AudioUnitSetProperty(
            unit, kAudioOutputUnitProperty_CurrentDevice, kAudioUnitScope_Global, 0,
            &id, UInt32(MemoryLayout<AudioDeviceID>.size)
        )
        guard status == noErr else { throw CaptureError.deviceRejected(status) }

        let hardware = engine.inputNode.outputFormat(forBus: 0)
        guard hardware.channelCount > 0, hardware.sampleRate > 0 else {
            throw CaptureError.unusableFormat("\(hardware.channelCount)ch @ \(hardware.sampleRate)Hz")
        }

        let channels = min(2, Int(hardware.channelCount))
        format = StreamFormat(sampleRate: Int(hardware.sampleRate.rounded()),
                              channels: channels, blockFrames: format.blockFrames)
        accumulator.removeAll(keepingCapacity: true)

        engine.inputNode.installTap(onBus: 0, bufferSize: 1024, format: hardware) { [weak self] buffer, _ in
            self?.consume(buffer)
        }
        engine.prepare()
        try engine.start()
        running = true
        NSLog("[audio] capturing %@ at %d Hz, %dch",
              AudioDevices.name(of: deviceID) ?? "device", format.sampleRate, format.channels)
    }

    func stop() {
        guard running else { return }
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        running = false
        accumulator.removeAll(keepingCapacity: false)
    }

    private func consume(_ buffer: AVAudioPCMBuffer) {
        guard let channelData = buffer.floatChannelData else { return }
        let frames = Int(buffer.frameLength)
        guard frames > 0 else { return }

        let sourceChannels = Int(buffer.format.channelCount)
        let wanted = format.channels
        var peak: Float = 0

        var pcm = Data(count: frames * wanted * 2)
        pcm.withUnsafeMutableBytes { raw in
            let out = raw.bindMemory(to: Int16.self)
            for frame in 0..<frames {
                for channel in 0..<wanted {
                    // Mono source feeding a stereo stream: duplicate rather
                    // than emit silence down one side.
                    let source = channel < sourceChannels ? channel : 0
                    let sample = channelData[source][frame]
                    let magnitude = abs(sample)
                    if magnitude > peak { peak = magnitude }
                    let clamped = max(-1.0, min(1.0, sample))
                    out[frame * wanted + channel] = Int16(clamped * 32767.0)
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
