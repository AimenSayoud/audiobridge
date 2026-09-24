package dev.audiobridge.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PairingTest {

    @Test
    fun parsesWhatTheServerEncodes() {
        val uri = "audiobridge://p?h=127.0.0.1&h=10.8.22.255&p=45678&t=152NF7gTPqUCj3P8BbcJ_Q" +
            "&r=48000&c=2&n=Studio-MacBook-Pro"
        val info = Pairing.parse(uri)!!
        assertEquals(listOf("127.0.0.1", "10.8.22.255"), info.hosts)
        assertEquals(45678, info.port)
        assertEquals("152NF7gTPqUCj3P8BbcJ_Q", info.token)
        assertEquals(48000, info.rate)
        assertEquals(2, info.channels)
        assertEquals("Studio-MacBook-Pro", info.name)
    }

    @Test
    fun hostOrderIsPreserved() {
        // The server puts USB loopback first on purpose; the racer relies on it.
        val info = Pairing.parse("audiobridge://p?h=127.0.0.1&h=192.168.1.5&p=1&t=x")!!
        assertEquals("127.0.0.1", info.hosts.first())
    }

    @Test
    fun percentEncodedNamesSurvive() {
        val original = PairingInfo(listOf("10.0.0.2"), 45678, "tok en+/=", name = "Studio Mac's")
        val parsed = Pairing.parse(original.toUri())!!
        assertEquals(original.token, parsed.token)
        assertEquals(original.name, parsed.name)
        assertEquals(original.hosts, parsed.hosts)
    }

    @Test
    fun acceptsBareHostAndHostPort() {
        assertEquals(listOf("192.168.1.9"), Pairing.parse("192.168.1.9")!!.hosts)
        assertEquals(45678, Pairing.parse("192.168.1.9")!!.port)
        assertEquals(9999, Pairing.parse("192.168.1.9:9999")!!.port)
    }

    @Test
    fun rejectsRubbish() {
        // The scanner feeds this every camera frame, so nulls, not exceptions.
        assertNull(Pairing.parse(""))
        assertNull(Pairing.parse("   "))
        assertNull(Pairing.parse("https://example.com/hello"))
        assertNull(Pairing.parse("audiobridge://p?"))
        assertNull(Pairing.parse("audiobridge://p?h=1.2.3.4&p=99999&t=x"))
        assertNull(Pairing.parse("some random qr code text"))
    }
}
