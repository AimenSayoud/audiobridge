import Foundation

/// Mirrors `TransportRoute` in the Kotlin shared module — both sides must
/// classify an address the same way or they will describe the same connection
/// differently.
enum TransportRoute: String, CaseIterable, Identifiable {
    case usb, lan, vpn, internet

    var id: String { rawValue }

    var label: String {
        switch self {
        case .usb: return "USB cable"
        case .lan: return "Same Wi-Fi"
        case .vpn: return "VPN"
        case .internet: return "Internet"
        }
    }

    var detail: String {
        switch self {
        case .usb: return "Lowest latency, no mobile data, never drops."
        case .lan: return "Both devices on one network."
        case .vpn: return "Works anywhere, including mobile data."
        case .internet: return "Through a tunnel or a forwarded port."
        }
    }

    var symbol: String {
        switch self {
        case .usb: return "cable.connector"
        case .lan: return "wifi"
        case .vpn: return "lock.shield"
        case .internet: return "globe"
        }
    }

    /// Shown when the route is not on offer — the one thing to do about it.
    var missingAdvice: String {
        switch self {
        case .usb: return "Plug the phone in with USB debugging enabled."
        case .lan: return "No local address — check this Mac is on a network."
        case .vpn: return "Install Tailscale on both devices; its address is picked up automatically."
        case .internet: return "Add a tunnel hostname under Network."
        }
    }
}

/// `127.0.0.1` means the cable here: loopback only appears in a pairing code
/// after `adb reverse` has made the phone's own localhost reach this Mac.
func classifyHost(_ host: String) -> TransportRoute {
    let trimmed = host.trimmingCharacters(in: .whitespaces).lowercased()
    if trimmed == "localhost" || trimmed.hasPrefix("127.") { return .usb }

    let octets = trimmed.split(separator: ".").compactMap { Int($0) }
    guard octets.count == 4, octets.allSatisfy({ (0...255).contains($0) }) else {
        return .internet      // a hostname, so it was typed in or came from a tunnel
    }
    switch (octets[0], octets[1]) {
    case (100, 64...127): return .vpn
    case (10, _): return .lan
    case (192, 168): return .lan
    case (172, 16...31): return .lan
    case (169, 254): return .lan
    default: return .internet
    }
}
