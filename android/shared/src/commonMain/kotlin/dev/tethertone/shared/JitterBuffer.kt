package dev.tethertone.shared

import kotlin.math.abs

/**
 * Absorbs network jitter and, more importantly, the fact that the Mac's audio
 * clock and this device's audio clock are not the same clock.
 *
 * Even on a perfect network the two crystals differ by tens of parts per
 * million, so a buffer that is only corrected on underrun drifts one way until
 * it either starves or grows unbounded. [speedCorrection] returns a playback
 * rate a hair off 1.0, which the sink applies continuously; over a minute that
 * absorbs the drift without a single audible discontinuity. Dropping or
 * duplicating a packet — what the browser version had to do — is a click.
 *
 * [push] and [pull] really are called from two different threads (the network
 * reader and the playback loop), so every accessor takes [lock].
 */
class JitterBuffer(
    private val sampleRate: Int,
    private val channels: Int,
    val targetMs: Int = 60,
    val maxMs: Int = 240,
) {
    private val bytesPerFrame = channels * 2
    private val bytesPerMs = sampleRate * bytesPerFrame / 1000.0
    private val maxBytes = (maxMs * bytesPerMs).toInt()

    private val chunks = ArrayDeque<ByteArray>()
    private var headOffset = 0
    private var buffered = 0

    var underruns = 0
        private set
    var overruns = 0
        private set
    var pulledBytes = 0L
        private set

    private val lock = PlatformLock()

    val bufferedBytes: Int get() = lock.withLock { buffered }
    val bufferedMs: Int get() = lock.withLock { (buffered / bytesPerMs).toInt() }

    fun push(data: ByteArray) = lock.withLock {
        chunks.addLast(data)
        buffered += data.size
        // Hard ceiling: if the link burst-delivered more than we can ever
        // absorb, shed from the front. Speed correction handles the slow case.
        while (buffered > maxBytes && chunks.size > 1) {
            val head = chunks.removeFirst()
            buffered -= head.size - headOffset
            headOffset = 0
            overruns++
        }
    }

    /** Fills [dest] completely, padding with silence on underrun. Returns real bytes written. */
    fun pull(dest: ByteArray, length: Int): Int = lock.withLock {
        var written = 0
        while (written < length && chunks.isNotEmpty()) {
            val head = chunks.first()
            val available = head.size - headOffset
            val take = minOf(available, length - written)
            head.copyInto(dest, written, headOffset, headOffset + take)
            written += take
            headOffset += take
            buffered -= take
            if (headOffset == head.size) {
                chunks.removeFirst()
                headOffset = 0
            }
        }
        if (written < length) {
            dest.fill(0, written, length)
            underruns++
        }
        pulledBytes += written
        written
    }

    /**
     * Drop from the front until at most [maxMs] remains, returning the bytes shed.
     *
     * Two callers. One: the instant before playback starts, where the socket and
     * the server's queue have just emptied a burst into us and keeping it would
     * bake that burst into the latency for the whole session — and where
     * discarding is free, because nothing is playing yet. Two: the fallback
     * path on a sink that refuses [AudioSink.setSpeed], which has no other way
     * to give back latency it has accumulated. That one is an audible click,
     * which is why it is a last resort and not the primary mechanism.
     */
    fun trimTo(maxMs: Int, countAsOverrun: Boolean = true): Int = lock.withLock {
        val ceiling = (maxMs * bytesPerMs).toInt()
        var shed = 0
        while (buffered > ceiling && chunks.isNotEmpty()) {
            val head = chunks.first()
            val remaining = head.size - headOffset
            if (buffered - remaining < ceiling) {
                // Partial: advance into this chunk rather than overshooting past the target.
                val drop = buffered - ceiling
                headOffset += drop
                buffered -= drop
                shed += drop
                break
            }
            chunks.removeFirst()
            headOffset = 0
            buffered -= remaining
            shed += remaining
        }
        if (shed > 0 && countAsOverrun) overruns++
        shed
    }

    fun reset() = lock.withLock {
        chunks.clear()
        headOffset = 0
        buffered = 0
    }

    /**
     * Playback rate to ask the sink for, as a multiplier of 1.0.
     *
     * Proportional control on buffer fill, clamped to [MAX_DEVIATION]. 0.2% is
     * about 4 cents of pitch if the sink resamples naively — below the
     * threshold of hearing for music, and most sinks time-stretch instead.
     * Returns 1.0 while the buffer is still filling, so startup does not
     * trigger a correction against a buffer that was simply never full yet.
     */
    fun speedCorrection(): Float = lock.withLock {
        if (pulledBytes < bytesPerMs * targetMs) return@withLock 1.0f
        val errorMs = (buffered / bytesPerMs).toInt() - targetMs
        if (abs(errorMs) < DEAD_ZONE_MS) return@withLock 1.0f
        val normalised = (errorMs.toFloat() / targetMs).coerceIn(-1.0f, 1.0f)
        1.0f + normalised * MAX_DEVIATION
    }

    companion object {
        const val MAX_DEVIATION = 0.002f
        const val DEAD_ZONE_MS = 4
    }
}
