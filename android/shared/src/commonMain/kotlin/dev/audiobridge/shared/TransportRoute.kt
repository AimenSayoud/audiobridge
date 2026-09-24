package dev.audiobridge.shared

/**
 * How a sink is reaching, or could reach, the server.
 *
 * The three routes behave very differently — a cable costs nothing and never
 * drops, Wi-Fi needs both devices on one network, and anything beyond that
 * needs a VPN or tunnel because neither a home router nor a mobile carrier will
 * accept an inbound connection. Telling them apart is what lets both apps say
 * something useful instead of "connecting…".
 */
enum class TransportRoute {
    USB,
    LAN,
    VPN,
    INTERNET;

    val label: String
        get() = when (this) {
            USB -> "USB cable"
            LAN -> "Same Wi-Fi"
            VPN -> "VPN"
            INTERNET -> "Internet"
        }

    val detail: String
        get() = when (this) {
            USB -> "Lowest latency, no data used, never drops."
            LAN -> "Both devices on one network."
            VPN -> "Works anywhere, including mobile data."
            INTERNET -> "Through a tunnel or forwarded port."
        }

    /** Uses mobile data, so bandwidth is worth mentioning. */
    val costsMobileData: Boolean
        get() = this == VPN || this == INTERNET
}

/**
 * `127.0.0.1` means the USB tunnel here: the server only ever puts loopback in
 * a pairing code after `adb reverse` has made the phone's own localhost reach
 * the Mac, so from the phone's side it is the cable.
 */
fun classifyHost(host: String): TransportRoute {
    val trimmed = host.trim().lowercase()
    if (trimmed == "localhost" || trimmed.startsWith("127.")) return TransportRoute.USB

    val octets = trimmed.split(".").mapNotNull { it.toIntOrNull() }
    if (octets.size != 4 || octets.any { it !in 0..255 }) {
        // A hostname, so it was typed in or came from a tunnel.
        return TransportRoute.INTERNET
    }
    val (a, b) = octets[0] to octets[1]
    return when {
        // Tailscale, ZeroTier and friends hand out carrier-grade NAT space.
        a == 100 && b in 64..127 -> TransportRoute.VPN
        a == 10 -> TransportRoute.LAN
        a == 192 && b == 168 -> TransportRoute.LAN
        a == 172 && b in 16..31 -> TransportRoute.LAN
        a == 169 && b == 254 -> TransportRoute.LAN
        else -> TransportRoute.INTERNET
    }
}

/** The distinct routes a pairing code offers, in the order they will be tried. */
fun PairingInfo.routes(): List<TransportRoute> =
    hosts.map(::classifyHost).distinct()
