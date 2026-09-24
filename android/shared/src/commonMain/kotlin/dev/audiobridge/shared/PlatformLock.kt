package dev.audiobridge.shared

/**
 * `synchronized` is not in the common standard library, and the jitter buffer
 * is genuinely touched by two threads (network reader, playback loop), so it
 * needs a real one rather than a hopeful comment.
 */
expect class PlatformLock() {
    fun <T> withLock(block: () -> T): T
}
