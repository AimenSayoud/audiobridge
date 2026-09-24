import Foundation

/// Conformance checks for the Swift half of the protocol.
///
/// XCTest ships with Xcode, not the Command Line Tools, so there is no
/// `swift test` here. These run inside the shipping binary instead
/// (`TethertoneMac --selftest`) and deliberately assert the *same vectors* as
/// the Kotlin tests in `ProtocolTest.kt`, `PairingTest.kt` and
/// `TransportRouteTest.kt` — two independent implementations of one wire
/// format drift apart silently otherwise.
enum SelfTest {

    private static var failures: [String] = []

    private static func check(_ condition: Bool, _ name: String, _ detail: String = "") {
        if condition {
            print("  ok   \(name)")
        } else {
            print("  FAIL \(name)\(detail.isEmpty ? "" : " — \(detail)")")
            failures.append(name)
        }
    }

    private static func equal<T: Equatable>(_ actual: T, _ expected: T, _ name: String) {
        check(actual == expected, name, "got \(actual), expected \(expected)")
    }

    static func run() -> Never {
        failures.removeAll()
        print("Tethertone protocol self-test\n")

        print("frame header")
        // Fixed bytes, not a round trip: this is the interop contract.
        equal([UInt8](encodeHeader(type: Proto.ping, length: 8, seq: 0x01020304)),
              [1, 0, 0, 8, 0x01, 0x02, 0x03, 0x04], "header is big-endian")
        let decoded = decodeHeader(encodeHeader(type: Proto.audio, length: 1920, seq: 65_537))!
        equal(decoded.type, Proto.audio, "type round-trips")
        equal(decoded.length, 1920, "length round-trips")
        equal(decoded.seq, 65_537, "seq round-trips")
        equal(decodeHeader(encodeHeader(type: Proto.audio, length: 0, seq: 0xFFFFFFFF))!.seq,
              0xFFFFFFFF, "maximum sequence survives")
        check(decodeHeader(Data([1, 2, 3])) == nil, "short header is rejected")

        print("\nbig-endian u64")
        equal([UInt8](putU64BE(0x0102030405060708)),
              [1, 2, 3, 4, 5, 6, 7, 8], "u64 is big-endian")

        print("\npairing URI")
        let info = PairingInfo(hosts: ["127.0.0.1", "10.8.22.255"], port: 45678,
                               token: "tok en+/=", rate: 48000, channels: 2,
                               name: "Studio Mac's")
        let uri = info.uri
        check(uri.hasPrefix("tethertone://p?"), "uri scheme")
        check(uri.contains("h=127.0.0.1") && uri.contains("h=10.8.22.255"), "every host is included")
        check(uri.contains("t=tok%20en%2B%2F%3D"), "token is percent-encoded", uri)
        check(uri.contains("n=Studio%20Mac%27s"), "name is percent-encoded", uri)
        check(!uri.contains("h=127.0.0.1&h=10.8.22.255&p=45678&t=tok en"), "no raw spaces")

        print("\nroute classification")
        equal(classifyHost("127.0.0.1"), .usb, "loopback is the cable")
        equal(classifyHost("localhost"), .usb, "localhost is the cable")
        equal(classifyHost("10.8.22.255"), .lan, "10/8 is local")
        equal(classifyHost("192.168.1.4"), .lan, "192.168/16 is local")
        equal(classifyHost("172.16.0.9"), .lan, "172.16/12 is local")
        equal(classifyHost("172.15.0.1"), .internet, "172.15 is outside the private block")
        equal(classifyHost("172.32.0.1"), .internet, "172.32 is outside the private block")
        equal(classifyHost("100.90.1.2"), .vpn, "100.64/10 is a mesh VPN")
        equal(classifyHost("100.63.0.1"), .internet, "100.63 is public")
        equal(classifyHost("100.128.0.1"), .internet, "100.128 is public")
        equal(classifyHost("mac.tail1234.ts.net"), .internet, "hostnames are internet")

        print("\ntoken")
        let a = Pairing.secureRandomBytes(16)
        let b = Pairing.secureRandomBytes(16)
        equal(a.count, 16, "token is 16 bytes")
        check(a != b, "two tokens differ")
        check(a.contains { $0 != 0 }, "token is not all zeros")
        check(Pairing.tokenMatches("abc", "abc"), "matching tokens compare equal")
        check(!Pairing.tokenMatches("abc", "abd"), "different tokens do not")
        check(!Pairing.tokenMatches("abc", "abcd"), "different lengths do not")

        print("\naddress enumeration")
        let addresses = Pairing.localIPv4Addresses()
        check(!addresses.contains { $0.hasPrefix("127.") }, "loopback is not advertised")
        check(!addresses.contains { $0.hasPrefix("169.254.") }, "link-local is not advertised")
        let vpn = addresses.filter(Pairing.isMeshVPN)
        check(vpn.allSatisfy { addresses.suffix(vpn.count).contains($0) },
              "VPN addresses come last")

        print("")
        if failures.isEmpty {
            print("PASS — all checks green")
            exit(0)
        }
        print("FAIL — \(failures.count) check(s): \(failures.joined(separator: ", "))")
        exit(1)
    }
}
