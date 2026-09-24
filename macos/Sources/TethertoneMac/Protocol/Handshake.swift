import Foundation

struct Hello: Codable {
    let v: Int
    let role: String?
    let token: String
    let name: String
    let caps: [String]?
}

struct Welcome: Codable {
    let v: Int
    let ok: Bool
    var rate: Int = 48000
    var ch: Int = 2
    var fmt: String = "pcm_s16le"
    var block: Int = 480
    var name: String = ""
    var err: String? = nil

    static func reject(_ reason: String) -> Welcome {
        Welcome(v: Proto.version, ok: false, err: reason)
    }
}

/// Telemetry the sink reports back, shown per-client in the menu.
struct ClientStats: Codable {
    let bufMs: Int
    let under: Int
    let lost: Int
    let speed: Double
}
