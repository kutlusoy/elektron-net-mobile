package org.elektronnet.mobile.ui.model

import org.elektronnet.mobile.node.LocalAddress
import org.elektronnet.mobile.node.NetworkArgs
import org.elektronnet.mobile.node.NetworkPorts
import org.elektronnet.mobile.node.NetworkSettings
import org.elektronnet.mobile.node.ServiceState
import org.elektronnet.mobile.node.TorRole
import org.elektronnet.mobile.node.TorRouting

enum class TileStatus { Running, Starting, Failed, Idle, Missing }

enum class TileLabel { Status, Address, Peers, Socks, Control, Routing, Role, RelayPort, Bandwidth, Sam, Incoming, Sharing, Share }

sealed interface TileValue {
    data class Text(val text: String, val mono: Boolean = true) : TileValue
    data class Count(val count: Int) : TileValue
    data class Status(val status: TileStatus) : TileValue
    data class Flag(val on: Boolean) : TileValue
    data class Routing(val routing: TorRouting) : TileValue
    data class Role(val role: TorRole) : TileValue
    data class Kbps(val kbps: Int) : TileValue
    data class Percent(val percent: Int) : TileValue
    data object Waiting : TileValue
}

data class TileRow(val label: TileLabel, val value: TileValue)

data class NetworkTile(val net: ConnNet, val rows: List<TileRow>)

object NetworkTiles {
    fun status(state: ServiceState, available: Boolean): TileStatus = when {
        !available -> TileStatus.Missing
        state == ServiceState.Running -> TileStatus.Running
        state == ServiceState.Starting -> TileStatus.Starting
        state is ServiceState.Failed -> TileStatus.Failed
        else -> TileStatus.Idle
    }

    fun build(
        network: NetworkSettings,
        ports: NetworkPorts,
        torState: ServiceState,
        torAvailable: Boolean,
        i2pState: ServiceState,
        i2pAvailable: Boolean,
        local: List<LocalAddress>,
        peerNetworks: Map<String, Int>,
    ): List<NetworkTile> {
        val tiles = mutableListOf<NetworkTile>()
        val loopback = NetworkArgs.LOOPBACK
        if (network.tor.enabled) {
            val tor = network.tor.normalized()
            val rows = mutableListOf(
                TileRow(TileLabel.Status, TileValue.Status(status(torState, torAvailable))),
                TileRow(TileLabel.Address, local.firstOrNull { it.isOnion }?.let { TileValue.Text(it.display()) } ?: TileValue.Waiting),
                TileRow(TileLabel.Peers, TileValue.Count(peerNetworks[ConnNet.Tor.key] ?: 0)),
                TileRow(TileLabel.Socks, TileValue.Text("$loopback:${ports.torSocks}")),
                TileRow(TileLabel.Control, TileValue.Text("$loopback:${ports.torControl}")),
                TileRow(TileLabel.Routing, TileValue.Routing(tor.routing)),
                TileRow(TileLabel.Role, TileValue.Role(tor.role)),
            )
            if (tor.role != TorRole.Client) {
                rows.add(TileRow(TileLabel.RelayPort, TileValue.Text(tor.orPort.toString())))
                rows.add(TileRow(TileLabel.Bandwidth, TileValue.Kbps(tor.bandwidthKbps)))
            }
            tiles.add(NetworkTile(ConnNet.Tor, rows))
        }
        if (network.i2p.enabled) {
            val i2p = network.i2p.normalized()
            val rows = mutableListOf(
                TileRow(TileLabel.Status, TileValue.Status(status(i2pState, i2pAvailable))),
                TileRow(TileLabel.Address, local.firstOrNull { it.isI2p }?.let { TileValue.Text(it.display()) } ?: TileValue.Waiting),
                TileRow(TileLabel.Peers, TileValue.Count(peerNetworks[ConnNet.I2p.key] ?: 0)),
                TileRow(TileLabel.Sam, TileValue.Text("$loopback:${ports.i2pSam}")),
                TileRow(TileLabel.Incoming, TileValue.Flag(i2p.acceptIncoming)),
                TileRow(TileLabel.Sharing, TileValue.Flag(i2p.transit)),
                TileRow(TileLabel.Bandwidth, TileValue.Kbps(i2p.bandwidthKbps)),
            )
            if (i2p.transit) rows.add(TileRow(TileLabel.Share, TileValue.Percent(i2p.sharePercent)))
            tiles.add(NetworkTile(ConnNet.I2p, rows))
        }
        return tiles
    }
}
