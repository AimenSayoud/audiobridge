import CoreAudio
import Foundation

struct StreamFormat: Equatable {
    var sampleRate: Int = 48000
    var channels: Int = 2
    var blockFrames: Int = 480      // 10 ms at 48 kHz

    var bytesPerFrame: Int { channels * 2 }
    var blockBytes: Int { blockFrames * bytesPerFrame }
}

/// Anything that can hand us interleaved 16-bit PCM at a known rate.
protocol AudioSource: AnyObject {
    var format: StreamFormat { get }
    /// Emits exactly `format.blockBytes` per call.
    var onBlock: ((Data) -> Void)? { get set }
    /// Peak of the most recent block, 0...1, for the level meter.
    var onLevel: ((Float) -> Void)? { get set }
    /// Sources that finish starting asynchronously report failure here rather
    /// than by throwing (ScreenCaptureKit cannot answer synchronously).
    var onFailure: ((Error) -> Void)? { get set }
    func start() throws
    func stop()
}

enum CaptureSourceKind: String, CaseIterable, Identifiable {
    case system
    case device

    var id: String { rawValue }

    var title: String {
        switch self {
        case .system: return "Screen Recording"
        case .device: return "BlackHole"
        }
    }

    var symbol: String {
        switch self {
        case .system: return "rectangle.dashed.badge.record"
        case .device: return "mic"
        }
    }

    /// What it runs on and what macOS will ask for — the two things that
    /// actually differ between them from the user's side.
    var summary: String {
        switch self {
        case .system:
            return "ScreenCaptureKit reads the system mix directly. No BlackHole needed, but macOS "
                + "files it under Screen Recording and shows the recording indicator while it runs."
        case .device:
            return "AVAudioEngine reads the BlackHole input device. Needs only the Microphone "
                + "permission; route your Mac's output into BlackHole for it to hear anything."
        }
    }
}

struct AudioInputDevice: Identifiable, Hashable {
    let id: AudioDeviceID
    let name: String
    let inputChannels: Int
    let sampleRate: Int

    var isBlackHole: Bool { name.localizedCaseInsensitiveContains("blackhole") }
}

enum AudioDevices {

    static func inputDevices() -> [AudioInputDevice] {
        var address = AudioObjectPropertyAddress(
            mSelector: kAudioHardwarePropertyDevices,
            mScope: kAudioObjectPropertyScopeGlobal,
            mElement: kAudioObjectPropertyElementMain
        )
        var size: UInt32 = 0
        guard AudioObjectGetPropertyDataSize(
            AudioObjectID(kAudioObjectSystemObject), &address, 0, nil, &size) == noErr else { return [] }

        let count = Int(size) / MemoryLayout<AudioDeviceID>.size
        var ids = [AudioDeviceID](repeating: 0, count: count)
        guard AudioObjectGetPropertyData(
            AudioObjectID(kAudioObjectSystemObject), &address, 0, nil, &size, &ids) == noErr else { return [] }

        return ids.compactMap { id in
            let channels = inputChannelCount(id)
            guard channels > 0 else { return nil }
            return AudioInputDevice(id: id, name: name(of: id) ?? "device \(id)",
                                    inputChannels: channels, sampleRate: nominalRate(id))
        }
    }

    /// The device the app should use when the user has not chosen: BlackHole if
    /// it is installed, because that is what the Mac's output is routed into.
    static func preferredInput() -> AudioInputDevice? {
        let devices = inputDevices()
        return devices.first(where: \.isBlackHole) ?? devices.first
    }

    static func name(of id: AudioDeviceID) -> String? {
        var address = AudioObjectPropertyAddress(
            mSelector: kAudioObjectPropertyName,
            mScope: kAudioObjectPropertyScopeGlobal,
            mElement: kAudioObjectPropertyElementMain
        )
        var value: Unmanaged<CFString>?
        var size = UInt32(MemoryLayout<Unmanaged<CFString>?>.size)
        guard AudioObjectGetPropertyData(id, &address, 0, nil, &size, &value) == noErr,
              let value else { return nil }
        return value.takeRetainedValue() as String
    }

    static func inputChannelCount(_ id: AudioDeviceID) -> Int {
        var address = AudioObjectPropertyAddress(
            mSelector: kAudioDevicePropertyStreamConfiguration,
            mScope: kAudioObjectPropertyScopeInput,
            mElement: kAudioObjectPropertyElementMain
        )
        var size: UInt32 = 0
        guard AudioObjectGetPropertyDataSize(id, &address, 0, nil, &size) == noErr, size > 0 else { return 0 }

        let raw = UnsafeMutableRawPointer.allocate(byteCount: Int(size), alignment: MemoryLayout<AudioBufferList>.alignment)
        defer { raw.deallocate() }
        guard AudioObjectGetPropertyData(id, &address, 0, nil, &size, raw) == noErr else { return 0 }

        let list = UnsafeMutableAudioBufferListPointer(raw.assumingMemoryBound(to: AudioBufferList.self))
        return list.reduce(0) { $0 + Int($1.mNumberChannels) }
    }

    static func nominalRate(_ id: AudioDeviceID) -> Int {
        var address = AudioObjectPropertyAddress(
            mSelector: kAudioDevicePropertyNominalSampleRate,
            mScope: kAudioObjectPropertyScopeGlobal,
            mElement: kAudioObjectPropertyElementMain
        )
        var rate: Float64 = 0
        var size = UInt32(MemoryLayout<Float64>.size)
        guard AudioObjectGetPropertyData(id, &address, 0, nil, &size, &rate) == noErr else { return 48000 }
        return Int(rate.rounded())
    }
}
