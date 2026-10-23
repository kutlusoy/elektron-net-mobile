package org.elektronnet.mobile.ui.node

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import org.elektronnet.mobile.node.PeerAddress
import org.elektronnet.mobile.ui.tools.Hint
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import org.elektronnet.mobile.node.LogSanitizer
import org.elektronnet.mobile.ui.theme.ElektronMono
import org.elektronnet.mobile.ui.tools.shareText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.elektronnet.mobile.R
import org.elektronnet.mobile.node.NetworkPorts
import org.elektronnet.mobile.node.RelayReach
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
    val network = NodeHost.peerVersion.let { NodeHost.store?.loadNetwork() } ?: NetworkSettings()
    val tiles = NetworkTiles.build(
        network = network,
        ports = NodeHost.networkPorts,
        torState = services?.torState ?: ServiceState.Stopped,
        torAvailable = services?.torAvailable ?: false,
        i2pState = services?.i2pState ?: ServiceState.Stopped,
        i2pAvailable = services?.i2pAvailable ?: false,
        local = telemetry?.localAddresses.orEmpty(),
        peerNetworks = NodeHost.latest?.peerNetworks.orEmpty(),
        torLog = services?.torOutput().orEmpty(),
    )
    tiles.forEach { tile ->
        NetworkTileCard(tile, network.peers.filter { if (tile.net == ConnNet.Tor) PeerAddress.isOnion(it) else PeerAddress.isI2p(it) })
    }
}

@Composable
private fun NetworkTileCard(tile: NetworkTile, peers: List<String>) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showLog by remember { mutableStateOf(false) }
    var qrFor by remember { mutableStateOf<String?>(null) }
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(connColor(tile.net))
                Text(stringResource(if (tile.net == ConnNet.Tor) R.string.net_tor_title else R.string.net_i2p_title), style = MaterialTheme.typography.titleMedium)
            }
            tile.rows.forEach { row ->
                InfoRow(stringResource(labelText(row.label)), valueText(row.value), mono = isMono(row.value))
                val address = (row.value as? TileValue.Text)?.text?.takeIf { row.label == TileLabel.Address }
                if (address != null) {
                    Row {
                        TextButton(onClick = { clipboard.setText(AnnotatedString(address)) }) { Text(stringResource(R.string.action_copy)) }
                        TextButton(onClick = { shareText(context, address) }) { Text(stringResource(R.string.action_share)) }
                        TextButton(onClick = { qrFor = address }) { Text(stringResource(R.string.net_peer_qr)) }
                    }
                }
            }
            if (tile.net == ConnNet.I2p && tile.rows.any { it.label == TileLabel.Address && it.value == TileValue.Waiting }) {
                Text(stringResource(R.string.net_tile_i2p_wait), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PeerBlock(tile.net, peers)
            TextButton(onClick = { showLog = !showLog }) { Text(stringResource(if (showLog) R.string.net_tile_log_hide else R.string.net_tile_log_show)) }
            if (showLog) {
                val services = NodeHost.networkServices
                val lines = (if (tile.net == ConnNet.Tor) services?.torOutput() else services?.i2pOutput()).orEmpty().takeLast(LOG_LINES)
                val clean = LogSanitizer.clean(lines)
                Text(clean, style = MaterialTheme.typography.bodySmall, fontFamily = ElektronMono)
                Row {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(clean)) }) { Text(stringResource(R.string.action_copy)) }
                    TextButton(onClick = { shareText(context, clean) }) { Text(stringResource(R.string.action_share)) }
                }
            }
        }
    }
    qrFor?.let { address ->
        AlertDialog(
            onDismissRequest = { qrFor = null },
            title = { Text(stringResource(if (tile.net == ConnNet.Tor) R.string.net_tor_title else R.string.net_i2p_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // upper case lets the QR code use the compact alphanumeric mode, the scanner lower-cases it again
                    org.elektronnet.mobile.ui.receive.QrView(address.uppercase())
                    Text(address, fontFamily = ElektronMono, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { qrFor = null }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

private const val LOG_LINES = 30

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
    TileLabel.Bootstrap -> R.string.net_tile_bootstrap
    TileLabel.Reachable -> R.string.net_tile_reach
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
    is TileValue.Reach -> stringResource(
        when (value.reach) {
            RelayReach.Reachable -> R.string.yes
            RelayReach.Unreachable -> R.string.net_reach_no
            RelayReach.Checking -> R.string.net_reach_checking
            RelayReach.Unknown -> R.string.net_reach_unknown
        },
    )
    TileValue.Waiting -> stringResource(R.string.net_tile_waiting)
}

@Composable
private fun PeerBlock(net: ConnNet, peers: List<String>) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    val result = NodeHost.peerResult
    fun add(value: String) {
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { NodeHost.addPeer(value) }
            if (outcome == NodeHost.PeerResult.Added || outcome == NodeHost.PeerResult.Saved) text = ""
        }
    }
    Text(stringResource(R.string.net_peers), style = MaterialTheme.typography.labelLarge)
    if (peers.isEmpty()) {
        Hint(stringResource(R.string.net_peer_none))
    } else {
        peers.forEach { peer ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(PeerAddress.shown(peer), fontFamily = ElektronMono, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { scope.launch(Dispatchers.IO) { NodeHost.removePeer(peer) } }) { Text(stringResource(R.string.net_peer_remove)) }
            }
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; NodeHost.peerResult = null },
        label = { Text(stringResource(R.string.net_peer_field)) },
        isError = result == NodeHost.PeerResult.Invalid,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = text.isNotBlank(), onClick = { add(text) }) { Text(stringResource(R.string.net_peer_add)) }
        OutlinedButton(onClick = { NodeHost.peerScanRequested = true }) { Text(stringResource(R.string.net_peer_scan)) }
    }
    result?.let {
        Text(
            stringResource(
                when (it) {
                    NodeHost.PeerResult.Added -> R.string.net_peer_added
                    NodeHost.PeerResult.Saved -> R.string.net_peer_saved
                    NodeHost.PeerResult.Exists -> R.string.net_peer_exists
                    NodeHost.PeerResult.Invalid -> R.string.net_peer_invalid
                },
            ),
            color = if (it == NodeHost.PeerResult.Invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
    Hint(stringResource(R.string.net_peer_hint))
}
