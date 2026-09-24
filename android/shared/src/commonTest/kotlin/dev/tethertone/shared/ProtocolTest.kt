package dev.tethertone.shared

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProtocolTest {

    @Test
    fun headerRoundTrips() {
        val encoded = encodeHeader(Proto.AUDIO, 0, 1920, 65_537L)
        assertEquals(Proto.HEADER_SIZE, encoded.size)
        val decoded = decodeHeader(encoded)
        assertEquals(Proto.AUDIO, decoded.type)
        assertEquals(0, decoded.flags)
        assertEquals(1920, decoded.length)
        assertEquals(65_537L, decoded.seq)
    }

    @Test
    fun headerIsBigEndianOnTheWire() {
        // Fixed bytes, not a round trip: this is the interop contract with the macOS server.
        val encoded = encodeHeader(Proto.PING, 0, 8, 0x01020304L)
        assertContentEquals(
            byteArrayOf(1, 0, 0, 8, 0x01, 0x02, 0x03, 0x04),
            encoded,
        )
    }

    @Test
    fun maximumSequenceSurvives() {
        val decoded = decodeHeader(encodeHeader(Proto.AUDIO, 0, 0, 0xFFFFFFFFL))
        assertEquals(0xFFFFFFFFL, decoded.seq)
    }

    @Test
    fun oversizedPayloadIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            encodeHeader(Proto.AUDIO, 0, Proto.MAX_PAYLOAD + 1, 0)
        }
    }

    @Test
    fun sequenceGapWrapsAtThirtyTwoBits() {
        assertEquals(1L, seqGap(10, 11))
        assertEquals(5L, seqGap(0xFFFFFFFFL, 4))   // wrapped, not a 4-billion-frame loss
        assertEquals(0L, seqGap(7, 7))
    }

    @Test
    fun u64RoundTrips() {
        val value = 1_234_567_890_123L
        assertEquals(value, getU64Be(putU64Be(value)))
    }

    @Test
    fun pcmSamplesAreLittleEndianAndSigned() {
        // 0xFF 0x7F = 32767, 0x00 0x80 = -32768
        val bytes = byteArrayOf(0xFF.toByte(), 0x7F, 0x00, 0x80.toByte())
        assertEquals(32767, pcmLe16At(bytes, 0))
        assertEquals(-32768, pcmLe16At(bytes, 2))
    }
}
