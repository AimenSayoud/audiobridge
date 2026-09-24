package dev.audiobridge.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JitterBufferTest {

    private val rate = 48000
    private val channels = 2
    private val bytesPerMs = rate * channels * 2 / 1000   // 192

    private fun buffer(targetMs: Int = 60, maxMs: Int = 240) =
        JitterBuffer(rate, channels, targetMs, maxMs)

    private fun block(ms: Int, fill: Byte = 7) = ByteArray(ms * bytesPerMs) { fill }

    @Test
    fun pullsBackWhatWasPushed() {
        val jb = buffer()
        jb.push(block(10))
        val out = ByteArray(10 * bytesPerMs)
        assertEquals(out.size, jb.pull(out, out.size))
        assertTrue(out.all { it == 7.toByte() })
        assertEquals(0, jb.underruns)
    }

    @Test
    fun spansChunkBoundaries() {
        val jb = buffer()
        jb.push(block(5, 1))
        jb.push(block(5, 2))
        val out = ByteArray(10 * bytesPerMs)
        jb.pull(out, out.size)
        assertEquals(1, out[0].toInt())
        assertEquals(2, out[out.size - 1].toInt())
        assertEquals(0, jb.bufferedBytes)
    }

    @Test
    fun underrunPadsWithSilenceRatherThanShortening() {
        val jb = buffer()
        jb.push(block(2))
        val out = ByteArray(10 * bytesPerMs) { -1 }
        val real = jb.pull(out, out.size)
        assertEquals(2 * bytesPerMs, real)
        assertTrue(out.drop(real).all { it == 0.toByte() }, "tail must be silence, not stale audio")
        assertEquals(1, jb.underruns)
    }

    @Test
    fun shedsAudioOnceTheCeilingIsHit() {
        val jb = buffer(targetMs = 60, maxMs = 100)
        repeat(30) { jb.push(block(10)) }     // 300 ms pushed into a 100 ms ceiling
        assertTrue(jb.bufferedMs <= 100, "buffered ${jb.bufferedMs}ms exceeded the ceiling")
        assertTrue(jb.overruns > 0)
    }

    @Test
    fun noSpeedCorrectionBeforeTheBufferHasBeenUsed() {
        val jb = buffer()
        jb.push(block(200))
        assertEquals(1.0f, jb.speedCorrection(), "startup must not be mistaken for drift")
    }

    private fun warmedUp(targetMs: Int = 60): JitterBuffer {
        val jb = buffer(targetMs)
        jb.push(block(targetMs))
        val out = ByteArray(targetMs * bytesPerMs)
        jb.pull(out, out.size)               // consume enough for corrections to arm
        return jb
    }

    @Test
    fun speedsUpWhenTooFullAndSlowsWhenTooEmpty() {
        val full = warmedUp()
        full.push(block(120))                // twice the target
        assertTrue(full.speedCorrection() > 1.0f, "a full buffer must play faster to drain")

        val empty = warmedUp()
        empty.push(block(5))                 // far under target
        assertTrue(empty.speedCorrection() < 1.0f, "a starved buffer must play slower to refill")
    }

    @Test
    fun correctionStaysInaudible() {
        val jb = warmedUp()
        jb.push(block(10_000))
        assertTrue(jb.speedCorrection() <= 1.0f + JitterBuffer.MAX_DEVIATION)
        val starved = warmedUp()
        assertTrue(starved.speedCorrection() >= 1.0f - JitterBuffer.MAX_DEVIATION)
    }

    @Test
    fun deadZoneStopsHunting() {
        val jb = warmedUp()
        jb.push(block(61))                   // 1 ms off target
        assertEquals(1.0f, jb.speedCorrection(), "must not chase noise around the setpoint")
    }
}

class JitterBufferTrimTest {
    private val rate = 48000
    private val channels = 2
    private val bytesPerMs = rate * channels * 2 / 1000

    private fun block(ms: Int, fill: Byte = 7) = ByteArray(ms * bytesPerMs) { fill }

    @Test
    fun trimLeavesExactlyTheTarget() {
        val jb = JitterBuffer(rate, channels, targetMs = 60, maxMs = 1000)
        repeat(10) { jb.push(block(30)) }        // 300 ms
        val shed = jb.trimTo(60)
        assertEquals(240 * bytesPerMs, shed)
        assertEquals(60, jb.bufferedMs)
    }

    @Test
    fun trimCanCutIntoTheMiddleOfAChunk() {
        val jb = JitterBuffer(rate, channels, targetMs = 10, maxMs = 1000)
        jb.push(block(100))                      // one big chunk, target far below it
        jb.trimTo(10)
        assertEquals(10, jb.bufferedMs, "must split a chunk rather than overshoot to zero")
    }

    @Test
    fun trimKeepsTheNewestAudio() {
        val jb = JitterBuffer(rate, channels, targetMs = 10, maxMs = 1000)
        jb.push(block(50, 1))
        jb.push(block(10, 2))
        jb.trimTo(10)
        val out = ByteArray(10 * bytesPerMs)
        jb.pull(out, out.size)
        assertTrue(out.all { it == 2.toByte() }, "trim must drop the stale front, not the fresh tail")
    }

    @Test
    fun trimIsANoOpWhenAlreadyUnderTarget() {
        val jb = JitterBuffer(rate, channels, targetMs = 60, maxMs = 1000)
        jb.push(block(20))
        assertEquals(0, jb.trimTo(60))
        assertEquals(20, jb.bufferedMs)
        assertEquals(0, jb.overruns)
    }
}
