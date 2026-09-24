import Foundation

struct PairingInfo {
    var hosts: [String]
    var port: Int
    var token: String
    var rate: Int
    var channels: Int
    var name: String

    var uri: String {
        var parts: [String] = hosts.map { "h=\(percentEncode($0))" }
        parts.append("p=\(port)")
        parts.append("t=\(percentEncode(token))")
        parts.append("r=\(rate)")
        parts.append("c=\(channels)")
        if !name.isEmpty { parts.append("n=\(percentEncode(name))") }
        return "tethertone://p?" + parts.joined(separator: "&")
    }
}

private let unreserved = CharacterSet(charactersIn:
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")

func percentEncode(_ value: String) -> String {
    value.addingPercentEncoding(withAllowedCharacters: unreserved) ?? value
}

enum Pairing {
    /// Outside the app's preferences on purpose: the headless mode and
    /// tools/probe.py read the same file, so one pairing covers all of them.
    static let configDirectory = FileManager.default.homeDirectoryForCurrentUser
        .appendingPathComponent(".tethertone")
    static let tokenURL = configDirectory.appendingPathComponent("token")

    static func loadToken(rotate: Bool = false) -> String {
        try? FileManager.default.createDirectory(
            at: configDirectory, withIntermediateDirectories: true,
            attributes: [.posixPermissions: 0o700]
        )
        if !rotate,
           let existing = try? String(contentsOf: tokenURL, encoding: .utf8) {
            let trimmed = existing.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty { return trimmed }
        }
        let token = Data(secureRandomBytes(16)).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
        try? token.write(to: tokenURL, atomically: true, encoding: .utf8)
        try? FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: tokenURL.path)
        return token
    }

    /// The result of SecRandomCopyBytes must never be ignored: on failure the
    /// buffer is left untouched, so discarding it silently produced a token of
    /// sixteen zero bytes — the same one, on every machine where it failed.
    static func secureRandomBytes(_ count: Int) -> [UInt8] {
        var bytes = [UInt8](repeating: 0, count: count)
        if SecRandomCopyBytes(kSecRandomDefault, count, &bytes) == errSecSuccess {
            return bytes
        }
        if let handle = FileHandle(forReadingAtPath: "/dev/urandom"),
           let data = try? handle.read(upToCount: count), data.count == count {
            handle.closeFile()
            return [UInt8](data)
        }
        // UUIDs are CSPRNG-backed on Apple platforms, so this is still random —
        // just a longer way round than the two preferred sources.
        var fallback: [UInt8] = []
        while fallback.count < count {
            withUnsafeBytes(of: UUID().uuid) { fallback.append(contentsOf: $0) }
        }
        return Array(fallback.prefix(count))
    }

    /// Constant-time comparison: the token is the only thing standing between
    /// the stream and everyone else on the network.
    static func tokenMatches(_ candidate: String, _ expected: String) -> Bool {
        let a = Array(candidate.utf8), b = Array(expected.utf8)
        guard a.count == b.count else { return false }
        var diff: UInt8 = 0
        for i in 0..<a.count { diff |= a[i] ^ b[i] }
        return diff == 0
    }

    /// Link-local plumbing a phone can never route through. Note that `utun`
    /// is NOT here: that is where Tailscale, WireGuard and friends live, and on
    /// a phone using mobile data a VPN address is the *only* one that can work.
    /// Filtering it out was quietly removing the one route that would have
    /// succeeded.
    private static let skippedPrefixes = ["lo", "awdl", "llw", "gif", "stf", "ap"]

    /// Tailscale and most mesh VPNs hand out addresses from the carrier-grade
    /// NAT range, which makes them easy to recognise and worth labelling.
    static func isMeshVPN(_ ip: String) -> Bool {
        let parts = ip.split(separator: ".").compactMap { Int($0) }
        guard parts.count == 4, parts[0] == 100 else { return false }
        return (64...127).contains(parts[1])
    }

    static func describe(_ ip: String) -> String {
        isMeshVPN(ip) ? "\(ip) (VPN — works from anywhere)" : ip
    }

    static func localIPv4Addresses() -> [String] {
        var addresses: [String] = []
        var vpnAddresses: [String] = []
        var head: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&head) == 0, let first = head else { return [] }
        defer { freeifaddrs(head) }

        var cursor: UnsafeMutablePointer<ifaddrs>? = first
        while let entry = cursor {
            defer { cursor = entry.pointee.ifa_next }
            guard let rawAddr = entry.pointee.ifa_addr,
                  rawAddr.pointee.sa_family == UInt8(AF_INET) else { continue }
            let flags = Int32(entry.pointee.ifa_flags)
            guard flags & IFF_UP != 0, flags & IFF_LOOPBACK == 0 else { continue }

            let name = String(cString: entry.pointee.ifa_name)
            if skippedPrefixes.contains(where: { name.hasPrefix($0) }) { continue }
            let isTunnel = name.hasPrefix("utun") || name.hasPrefix("ipsec")

            var buffer = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            guard getnameinfo(rawAddr, socklen_t(rawAddr.pointee.sa_len),
                              &buffer, socklen_t(buffer.count),
                              nil, 0, NI_NUMERICHOST) == 0 else { continue }
            let ip = String(cString: buffer)
            if ip.hasPrefix("127.") || ip.hasPrefix("169.254.") { continue }
            if isTunnel || isMeshVPN(ip) {
                if !vpnAddresses.contains(ip) { vpnAddresses.append(ip) }
            } else if !addresses.contains(ip) {
                addresses.append(ip)
            }
        }
        // Physical first, VPN after: on the same Wi-Fi the LAN address is
        // faster, and the client races them all anyway, so the VPN address only
        // wins when it is the only one that answers.
        return addresses + vpnAddresses
    }

    static var machineName: String {
        Host.current().localizedName ?? ProcessInfo.processInfo.hostName
    }
}
