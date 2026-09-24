package dev.audiobridge.app

import android.content.Context
import dev.audiobridge.shared.BridgeConfig
import dev.audiobridge.shared.Pairing
import dev.audiobridge.shared.PairingInfo

/**
 * Remembers the last pairing so the QR is a one-time thing, not a ritual.
 * Stored as the URI the server emitted, so there is exactly one parser.
 */
class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("audiobridge", Context.MODE_PRIVATE)

    var pairing: PairingInfo?
        get() = prefs.getString(KEY_PAIRING, null)?.let(Pairing::parse)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_PAIRING) else putString(KEY_PAIRING, value.toUri())
        }.apply()

    /** Jitter-buffer target. Lower is tighter; too low and the buffer starves. */
    var bufferMs: Int
        get() = prefs.getInt(KEY_BUFFER, DEFAULT_BUFFER_MS)
        set(value) = prefs.edit().putInt(KEY_BUFFER, value.coerceIn(MIN_BUFFER_MS, MAX_BUFFER_MS)).apply()

    var autoReconnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO, value).apply()

    var preferLowLatency: Boolean
        get() = prefs.getBoolean(KEY_LOW_LATENCY, false)
        set(value) = prefs.edit().putBoolean(KEY_LOW_LATENCY, value).apply()

    var volume: Float
        get() = prefs.getFloat(KEY_VOLUME, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_VOLUME, value.coerceIn(0f, 1f)).apply()

    fun toConfig() = BridgeConfig(
        targetBufferMs = bufferMs,
        autoReconnect = autoReconnect,
        preferLowLatency = preferLowLatency,
        volume = volume,
    )

    companion object {
        private const val KEY_PAIRING = "pairing_uri"
        private const val KEY_BUFFER = "buffer_ms"
        private const val KEY_AUTO = "auto_connect"
        private const val KEY_LOW_LATENCY = "prefer_low_latency"
        private const val KEY_VOLUME = "volume"

        const val DEFAULT_BUFFER_MS = 90
        const val MIN_BUFFER_MS = 20
        const val MAX_BUFFER_MS = 250
    }
}
