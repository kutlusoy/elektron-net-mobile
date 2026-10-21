package org.elektronnet.mobile.ui.model

import org.elektronnet.mobile.node.I2pSettings
import org.elektronnet.mobile.node.NetworkSettings
import org.elektronnet.mobile.node.TorSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionModelTest {
    @Test
    fun clearnetChipsAppearOnlyWithPeers() {
        val chips = ConnectionModel.chips(mapOf("ipv4" to 5, "ipv6" to 0), NetworkSettings())
        assertEquals(listOf(ConnChip(ConnNet.IPv4, 5)), chips)
    }

    @Test
    fun torAndI2pShowWhenSwitchedOnEvenWithoutPeers() {
        val network = NetworkSettings(TorSettings(enabled = true), I2pSettings(enabled = true))
        val chips = ConnectionModel.chips(mapOf("ipv4" to 3, "onion" to 2), network)
        assertEquals(listOf(ConnChip(ConnNet.IPv4, 3), ConnChip(ConnNet.Tor, 2), ConnChip(ConnNet.I2p, 0)), chips)
    }

    @Test
    fun peersOnTorShowEvenWhenTheSwitchIsOff() {
        assertEquals(listOf(ConnChip(ConnNet.Tor, 1)), ConnectionModel.chips(mapOf("onion" to 1), NetworkSettings()))
    }

    @Test
    fun countsGroupPeersByNetwork() {
        assertEquals(mapOf("ipv4" to 2, "i2p" to 1), ConnectionModel.count(listOf("ipv4", "i2p", "ipv4", "")))
    }
}
