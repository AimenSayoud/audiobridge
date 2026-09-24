package dev.audiobridge.shared

/**
 * What a QR code carries: every address the server believes it is reachable on,
 * plus the token that proves we are allowed to listen.
 *
 * The host list is ordered by the server's own preference (USB loopback first
 * when the cable is plugged in), and [BridgeClient] races it in that order.
 */
data class PairingInfo(
    val hosts: List<String>,
    val port: Int,
    val token: String,
    val rate: Int = 48000,
    val channels: Int = 2,
    val name: String = "",
) {
    fun toUri(): String = buildString {
        append("audiobridge://p?")
        val parts = mutableListOf<String>()
        hosts.forEach { parts += "h=" + percentEncode(it) }
        parts += "p=$port"
        parts += "t=" + percentEncode(token)
        parts += "r=$rate"
        parts += "c=$channels"
        if (name.isNotEmpty()) parts += "n=" + percentEncode(name)
        append(parts.joinToString("&"))
    }
}

object Pairing {
    const val SCHEME = "audiobridge"

    /**
     * Accepts the QR payload, or a bare `host` / `host:port` typed by hand.
     * Returns null rather than throwing: this runs on every camera frame.
     */
    fun parse(text: String): PairingInfo? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return if (trimmed.startsWith("$SCHEME://", ignoreCase = true)) parseUri(trimmed)
        else parseHostPort(trimmed)
    }

    private fun parseUri(uri: String): PairingInfo? {
        val query = uri.substringAfter('?', "")
        if (query.isEmpty()) return null
        val hosts = mutableListOf<String>()
        var port = 45678
        var token = ""
        var rate = 48000
        var channels = 2
        var name = ""
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val key = pair.substringBefore('=')
            val value = percentDecode(pair.substringAfter('=', ""))
            when (key) {
                "h" -> if (value.isNotEmpty()) hosts += value
                "p" -> port = value.toIntOrNull() ?: return null
                "t" -> token = value
                "r" -> rate = value.toIntOrNull() ?: rate
                "c" -> channels = value.toIntOrNull() ?: channels
                "n" -> name = value
            }
        }
        if (hosts.isEmpty() || port !in 1..65535) return null
        return PairingInfo(hosts, port, token, rate, channels, name)
    }

    private fun parseHostPort(text: String): PairingInfo? {
        // Reject anything with a space or scheme separator: it is not a host.
        if (text.any { it.isWhitespace() } || text.contains("//")) return null
        val host = text.substringBefore(':')
        val port = if (text.contains(':')) text.substringAfter(':').toIntOrNull() ?: return null else 45678
        if (host.isEmpty() || port !in 1..65535) return null
        return PairingInfo(listOf(host), port, token = "")
    }
}

private const val HEX = "0123456789ABCDEF"

internal fun percentEncode(value: String): String {
    val sb = StringBuilder(value.length)
    for (byte in value.encodeToByteArray()) {
        val b = byte.toInt() and 0xFF
        val c = b.toChar()
        if (c.isLetterOrDigit() && b < 0x80 || c in "-._~") sb.append(c)
        else sb.append('%').append(HEX[b shr 4]).append(HEX[b and 0x0F])
    }
    return sb.toString()
}

internal fun percentDecode(value: String): String {
    if ('%' !in value && '+' !in value) return value
    val out = ArrayList<Byte>(value.length)
    var i = 0
    while (i < value.length) {
        when {
            value[i] == '%' && i + 2 < value.length -> {
                val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex == null) { out += value[i].code.toByte(); i++ }
                else { out += hex.toByte(); i += 3 }
            }
            value[i] == '+' -> { out += ' '.code.toByte(); i++ }
            else -> {
                for (b in value[i].toString().encodeToByteArray()) out += b
                i++
            }
        }
    }
    return out.toByteArray().decodeToString()
}
