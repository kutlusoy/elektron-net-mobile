package org.elektronnet.mobile.ui.node

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.elektronnet.mobile.R
import org.elektronnet.mobile.node.NetworkPorts
import org.elektronnet.mobile.node.NetworkSettings
import org.elektronnet.mobile.node.ServiceState
import org.elektronnet.mobile.node.Telemetry
import org.elektronnet.mobile.node.TorRole
import org.elektronnet.mobile.node.TorRouting
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.ui.GlassCard
import org.elektronnet.mobile.ui.InfoRow
import org.elektronnet.mobile.ui.StatusDot
import org.elektronnet.mobile.ui.connColor
import org.elektronnet.mobile.ui.model.ConnNet
import org.elektronnet.mobile.ui.model.NetworkTile
import org.elektronnet.mobile.ui.model.NetworkTiles
import org.elektronnet.mobile.ui.model.TileLabel
import org.elektronnet.mobile.ui.model.TileStatus
import org.elektronnet.mobile.ui.model.TileValue

@Composable
fun NetworkTilesSection(telemetry: Telemetry?) {
    val services = NodeHost.networkServices
    val tiles = NetworkTiles.build(
        network = NodeHost.store?.loadNetwork() ?: NetworkSettings(),
        ports = NodeHost.networkPorts,
        torState = services?.torState ?: ServiceState.Stopped,
        torAvailable = services?.torAvailable ?: false,
        i2pState = services?.i2pState ?: ServiceState.Stopped,
        i2pAvailable = services?.i2pAvailable ?: false,
        local = telemetry?.localAddresses.orEmpty(),
        peerNetworks = NodeHost.latest?.peerNetworks.orEmpty(),
    )
    tiles.forEach { NetworkTileCard(it) }
}

@Composable
private fun NetworkTileCard(tile: NetworkTile) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(connColor(tile.net))
                Text(stringResource(if (tile.net == ConnNet.Tor) R.string.net_tor_title else R.string.net_i2p_title), style = MaterialTheme.typography.titleMedium)
            }
            tile.rows.forEach { row -> InfoRow(stringResource(labelText(row.label)), valueText(row.value), mono = isMono(row.value)) }
        }
    }
}

private fun isMono(value: TileValue): Boolean = value is TileValue.Text && value.mono

private fun labelText(label: TileLabel): Int = when (label) {
    TileLabel.Status -> R.string.net_tile_status
    TileLabel.Address -> R.string.net_tile_address
    TileLabel.Peers -> R.string.net_tile_peers
    TileLabel.Socks -> R.string.net_tile_socks
    TileLabel.Control -> R.string.net_tile_control
    TileLabel.Routing -> R.string.net_tile_routing
    TileLabel.Role -> R.string.net_tile_role
    TileLabel.RelayPort -> R.string.net_tor_orport
    TileLabel.Bandwidth -> R.string.net_tor_bandwidth
    TileLabel.Sam -> R.string.net_tile_sam
    TileLabel.Incoming -> R.string.net_tile_incoming
    TileLabel.Sharing -> R.string.net_tile_sharing
    TileLabel.Share -> R.string.net_tile_share
}

@Composable
private fun valueText(value: TileValue): String = when (value) {
    is TileValue.Text -> value.text
    is TileValue.Count -> value.count.toString()
    is TileValue.Status -> stringResource(
        when (value.status) {
            TileStatus.Running -> R.string.net_status_running
            TileStatus.Starting -> R.string.net_status_starting
            TileStatus.Failed -> R.string.net_status_failed
            TileStatus.Idle -> R.string.net_status_idle
            TileStatus.Missing -> R.string.net_status_missing
        },
    )
    is TileValue.Flag -> stringResource(if (value.on) R.string.yes else R.string.no)
    is TileValue.Routing -> stringResource(if (value.routing == TorRouting.AllTraffic) R.string.net_tor_all else R.string.net_tor_additional)
    is TileValue.Role -> stringResource(
        when (value.role) {
            TorRole.Client -> R.string.net_tor_client
            TorRole.Relay -> R.string.net_tor_relay
            TorRole.Exit -> R.string.net_tor_exit
        },
    )
    is TileValue.Kbps -> stringResource(R.string.net_kbps, value.kbps)
    is TileValue.Percent -> stringResource(R.string.net_percent, value.percent)
    TileValue.Waiting -> stringResource(R.string.net_tile_waiting)
}
