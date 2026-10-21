package org.elektronnet.mobile.ui.model

import org.elektronnet.mobile.node.I2pSettings
import org.elektronnet.mobile.node.LocalAddress
import org.elektronnet.mobile.node.NetworkPorts
import org.elektronnet.mobile.node.NetworkSettings
import org.elektronnet.mobile.node.ServiceState
import org.elektronnet.mobile.node.Telemetry
import org.elektronnet.mobile.node.TorRole
import org.elektronnet.mobile.node.TorSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkTilesModelTest {
    private fun build(network: NetworkSettings, local: List<LocalAddress> = emptyList(), peers: Map<String, Int> = emptyMap(), tor: ServiceState = ServiceState.Running) =
        NetworkTiles.build(network, NetworkPorts(torSocks = 9060, torControl = 9061, i2pSam = 7666), tor, true, ServiceState.Starting, true, local, peers)

    @Test
    fun noTilesWhileBothAreOff() {
        assertTrue(build(NetworkSettings()).isEmpty())
    }

    @Test
    fun torTileShowsPortsAddressAndPeers() {
        val tiles = build(
            NetworkSettings(tor = TorSettings(enabled = true)),
            local = listOf(LocalAddress("abc.onion", 8333)),
            peers = mapOf("onion" to 2),
        )
        assertEquals(1, tiles.size)
        val rows = tiles.single().rows.associate { it.label to it.value }
        assertEquals(TileValue.Status(TileStatus.Running), rows[TileLabel.Status])
        assertEquals(TileValue.Text("abc.onion:8333"), rows[TileLabel.Address])
        assertEquals(TileValue.Count(2), rows[TileLabel.Peers])
        assertEquals(TileValue.Text("127.0.0.1:9060"), rows[TileLabel.Socks])
        assertEquals(TileValue.Text("127.0.0.1:9061"), rows[TileLabel.Control])
        assertTrue(TileLabel.RelayPort !in rows)
    }

    @Test
    fun theAddressIsWaitingUntilTheNodeKnowsIt() {
        val rows = build(NetworkSettings(tor = TorSettings(enabled = true))).single().rows.associate { it.label to it.value }
        assertEquals(TileValue.Waiting, rows[TileLabel.Address])
    }

    @Test
    fun relayAddsItsPortAndBandwidth() {
        val rows = build(NetworkSettings(tor = TorSettings(enabled = true, role = TorRole.Relay, orPort = 9101, bandwidthKbps = 512))).single().rows.associate { it.label to it.value }
        assertEquals(TileValue.Text("9101"), rows[TileLabel.RelayPort])
        assertEquals(TileValue.Kbps(512), rows[TileLabel.Bandwidth])
    }

    @Test
    fun i2pTileShowsTheSamBridgeAndSharing() {
        val tile = build(
            NetworkSettings(i2p = I2pSettings(enabled = true, transit = true, sharePercent = 40)),
            local = listOf(LocalAddress("xyz.b32.i2p", 0)),
            peers = mapOf("i2p" to 1),
        ).single()
        assertEquals(ConnNet.I2p, tile.net)
        val rows = tile.rows.associate { it.label to it.value }
        assertEquals(TileValue.Status(TileStatus.Starting), rows[TileLabel.Status])
        assertEquals(TileValue.Text("xyz.b32.i2p"), rows[TileLabel.Address])
        assertEquals(TileValue.Text("127.0.0.1:7666"), rows[TileLabel.Sam])
        assertTrue(tile.rows.any { it.value == TileValue.Percent(40) })
    }

    @Test
    fun missingBinaryAndFailureShowAsSuch() {
        assertEquals(TileStatus.Missing, NetworkTiles.status(ServiceState.Stopped, false))
        assertEquals(TileStatus.Failed, NetworkTiles.status(ServiceState.Failed(1, "x"), true))
        assertEquals(TileStatus.Idle, NetworkTiles.status(ServiceState.Stopped, true))
    }

    @Test
    fun telemetryReadsTheLocalAddresses() {
        val network = mapOf("localaddresses" to listOf(mapOf("address" to "abc.onion", "port" to 8333L, "score" to 4L), mapOf("address" to "x.b32.i2p", "port" to 0L)))
        val telemetry = Telemetry.parse(null, null, null, network)
        assertEquals(listOf(LocalAddress("abc.onion", 8333), LocalAddress("x.b32.i2p", 0)), telemetry.localAddresses)
        assertTrue(telemetry.localAddresses[0].isOnion && telemetry.localAddresses[1].isI2p)
        assertEquals("x.b32.i2p", telemetry.localAddresses[1].display())
    }

    @Test
    fun theTorTileShowsBootstrapAndRelayReachability() {
        val log = listOf("[notice] Bootstrapped 100% (done): Done", "[warn] Your server has not managed to confirm reachability for its ORPort(s) at x")
        val client = NetworkTiles.build(NetworkSettings(tor = TorSettings(enabled = true)), NetworkPorts(), ServiceState.Running, true, ServiceState.Stopped, true, emptyList(), emptyMap(), log).single().rows.associate { it.label to it.value }
        assertEquals(TileValue.Percent(100), client[TileLabel.Bootstrap])
        assertTrue(TileLabel.Reachable !in client)
        val relay = NetworkTiles.build(NetworkSettings(tor = TorSettings(enabled = true, role = TorRole.Relay)), NetworkPorts(), ServiceState.Running, true, ServiceState.Stopped, true, emptyList(), emptyMap(), log).single().rows.associate { it.label to it.value }
        assertEquals(TileValue.Reach(org.elektronnet.mobile.node.RelayReach.Unreachable), relay[TileLabel.Reachable])
        val noLog = NetworkTiles.build(NetworkSettings(tor = TorSettings(enabled = true)), NetworkPorts(), ServiceState.Starting, true, ServiceState.Stopped, true, emptyList(), emptyMap()).single().rows.associate { it.label to it.value }
        assertEquals(TileValue.Waiting, noLog[TileLabel.Bootstrap])
    }
}
