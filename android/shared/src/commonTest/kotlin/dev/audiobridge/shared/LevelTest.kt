package dev.audiobridge.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LevelTest {

    private fun pcm(vararg samples: Int): ByteArray {
        val out = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, sample ->
            out[i * 2] = (sample and 0xFF).toByte()
            out[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return out
    }

    @Test
    fun silenceIsZero() {
        assertEquals(0f, pcmPeakLe16(pcm(0, 0, 0, 0, 0, 0, 0, 0), stride = 1))
    }

    @Test
    fun fullScaleReadsAsFull() {
        // Two's complement is asymmetric: -32768 normalises to exactly 1.0,
        // the positive maximum 32767 to 0.99997. Both are "full scale".
        assertEquals(1f, pcmPeakLe16(pcm(-32768), stride = 1))
        assertTrue(pcmPeakLe16(pcm(32767), stride = 1) > 0.9999f)
    }

    @Test
    fun negativePeaksCount() {
        // A waveform that only swings negative must still read as loud.
        val peak = pcmPeakLe16(pcm(0, -30000, 0, -30000), stride = 1)
        assertTrue(peak > 0.9f, "got $peak")
    }

    @Test
    fun strideSkipsSamplesButStaysInRange() {
        val loud = pcmPeakLe16(pcm(32767, 0, 0, 0, 32767, 0, 0, 0), stride = 4)
        assertTrue(loud > 0.9999f, "got $loud")
        val everyValue = pcmPeakLe16(pcm(100, 200, 300, 400), stride = 1)
        assertTrue(everyValue in 0f..1f)
    }

    @Test
    fun respectsTheLengthArgument() {
        // Packets are read into a buffer that may be longer than the payload;
        // stale bytes past `length` must not show up on the meter.
        val buffer = pcm(0, 0, 32767, 32767)
        assertEquals(0f, pcmPeakLe16(buffer, length = 4, stride = 1))
    }
}
