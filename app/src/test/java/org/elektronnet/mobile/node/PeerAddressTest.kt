package org.elektronnet.mobile.node

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerAddressTest {
    private val onion = "a".repeat(56) + ".onion"
    private val i2p = "b".repeat(52) + ".b32.i2p"

    @Test
    fun acceptsOnionAndI2pAddressesAndNothingElse() {
        assertEquals(onion, PeerAddress.parse(onion))
        assertEquals("$onion:8333", PeerAddress.parse("  ${onion.uppercase()}:8333 "))
        assertEquals("$i2p:0", PeerAddress.parse(i2p))
        assertEquals("$i2p:0", PeerAddress.parse("$i2p:1234"))
        assertEquals(i2p, PeerAddress.shown("$i2p:0"))
        assertNull(PeerAddress.parse("example.com"))
        assertNull(PeerAddress.parse("1.2.3.4:8333"))
        assertNull(PeerAddress.parse("a".repeat(55) + ".onion"))
        assertNull(PeerAddress.parse("$onion:0"))
        assertNull(PeerAddress.parse("$onion:70000"))
        assertNull(PeerAddress.parse("$onion -connect=evil"))
        assertNull(PeerAddress.parse(""))
        assertTrue(PeerAddress.isOnion(onion) && !PeerAddress.isI2p(onion))
        assertTrue(PeerAddress.isI2p("$i2p:0") && !PeerAddress.isOnion(i2p))
    }

    @Test
    fun peersGoIntoTheNodeArgumentsWithoutTriggeringARestart() {
        val tor = TorSettings(enabled = true)
        val withPeers = NetworkSettings(tor, I2pSettings(enabled = true), listOf(onion, "$i2p:1", "junk", onion))
        val without = NetworkSettings(tor, I2pSettings(enabled = true))
        val config = NodeConfig(File("elektrond"), File("data"), Chain.Regtest, 18443, 18444, network = withPeers)
        val args = config.arguments()
        assertTrue("-addnode=$onion" in args)
        assertTrue("-addnode=$i2p:0" in args)
        assertEquals(2, args.count { it.startsWith("-addnode=") })
        assertEquals(config.networkArgs(), config.copy(network = without).networkArgs())
        assertFalse(config.copy(network = NetworkSettings(TorSettings(enabled = false), I2pSettings(enabled = true), listOf(onion))).arguments().any { it.startsWith("-addnode=") })
    }
}
