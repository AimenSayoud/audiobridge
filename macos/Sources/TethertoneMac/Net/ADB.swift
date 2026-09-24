import Foundation

/// Optional USB path. `adb reverse` makes the phone's own localhost:port reach
/// this Mac, which is lower latency than Wi-Fi and immune to congestion.
enum ADB {

    /// adb is not on a GUI app's PATH — launchd gives it a minimal one — so the
    /// usual install locations are checked directly.
    static let searchPaths = [
        "/usr/local/bin/adb",
        "/opt/homebrew/bin/adb",
        "\(NSHomeDirectory())/Library/Android/sdk/platform-tools/adb",
    ]

    static var executable: String? {
        searchPaths.first { FileManager.default.isExecutableFile(atPath: $0) }
    }

    @discardableResult
    private static func run(_ arguments: [String], timeout: TimeInterval = 10) -> (status: Int32, output: String)? {
        guard let tool = executable else { return nil }
        let process = Process()
        process.executableURL = URL(fileURLWithPath: tool)
        process.arguments = arguments
        let pipe = Pipe()
        process.standardOutput = pipe
        process.standardError = pipe
        do { try process.run() } catch { return nil }

        let deadline = Date().addingTimeInterval(timeout)
        while process.isRunning && Date() < deadline { usleep(50_000) }
        if process.isRunning { process.terminate(); return nil }

        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        return (process.terminationStatus, String(decoding: data, as: UTF8.self))
    }

    static func attachedDevices() -> [String] {
        guard let result = run(["devices"]) else { return [] }
        return result.output
            .split(separator: "\n")
            .dropFirst()
            .compactMap { line in
                let parts = line.split(separator: "\t")
                guard parts.count == 2, parts[1].trimmingCharacters(in: .whitespaces) == "device" else { return nil }
                return String(parts[0])
            }
    }

    @discardableResult
    static func reverse(port: UInt16, serial: String?) -> Bool {
        var arguments: [String] = []
        if let serial { arguments += ["-s", serial] }
        arguments += ["reverse", "tcp:\(port)", "tcp:\(port)"]
        return run(arguments)?.status == 0
    }

    static func removeReverse(port: UInt16, serial: String?) {
        var arguments: [String] = []
        if let serial { arguments += ["-s", serial] }
        arguments += ["reverse", "--remove", "tcp:\(port)"]
        run(arguments)
    }
}
