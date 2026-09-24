package dev.tethertone.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class TransportRouteTest {

    @Test
    fun loopbackIsTheCable() {
        // The server only puts loopback in a pairing code once adb reverse is
        // up, so from the phone's side it means the USB cable.
        assertEquals(TransportRoute.USB, classifyHost("127.0.0.1"))
        assertEquals(TransportRoute.USB, classifyHost("localhost"))
    }

    @Test
    fun privateRangesAreLocalNetwork() {
        assertEquals(TransportRoute.LAN, classifyHost("10.8.22.255"))
        assertEquals(TransportRoute.LAN, classifyHost("192.168.1.40"))
        assertEquals(TransportRoute.LAN, classifyHost("172.16.0.9"))
        assertEquals(TransportRoute.LAN, classifyHost("172.31.255.1"))
    }

    @Test
    fun addressesNextToThePrivateRangesAreNot() {
        // 172.15 and 172.32 sit just outside the private block.
        assertEquals(TransportRoute.INTERNET, classifyHost("172.15.0.1"))
        assertEquals(TransportRoute.INTERNET, classifyHost("172.32.0.1"))
        assertEquals(TransportRoute.INTERNET, classifyHost("11.0.0.1"))
    }

    @Test
    fun carrierGradeNatSpaceIsAMeshVPN() {
        assertEquals(TransportRoute.VPN, classifyHost("100.64.0.1"))
        assertEquals(TransportRoute.VPN, classifyHost("100.101.102.103"))
        assertEquals(TransportRoute.VPN, classifyHost("100.127.255.254"))
        // 100.63 and 100.128 are ordinary public space, not the VPN range.
        assertEquals(TransportRoute.INTERNET, classifyHost("100.63.0.1"))
        assertEquals(TransportRoute.INTERNET, classifyHost("100.128.0.1"))
    }

    @Test
    fun hostnamesAndPublicAddressesAreInternet() {
        assertEquals(TransportRoute.INTERNET, classifyHost("mac.tail1234.ts.net"))
        assertEquals(TransportRoute.INTERNET, classifyHost("0.tcp.eu.ngrok.io"))
        assertEquals(TransportRoute.INTERNET, classifyHost("150.254.159.67"))
    }

    @Test
    fun pairingRoutesKeepOrderAndDropDuplicates() {
        val info = PairingInfo(
            hosts = listOf("127.0.0.1", "10.8.22.255", "10.8.19.4", "100.90.1.2"),
            port = 45678, token = "x",
        )
        assertEquals(
            listOf(TransportRoute.USB, TransportRoute.LAN, TransportRoute.VPN),
            info.routes(),
        )
    }

    @Test
    fun onlyRemoteRoutesCostMobileData() {
        assertEquals(false, TransportRoute.USB.costsMobileData)
        assertEquals(false, TransportRoute.LAN.costsMobileData)
        assertEquals(true, TransportRoute.VPN.costsMobileData)
        assertEquals(true, TransportRoute.INTERNET.costsMobileData)
    }
}
