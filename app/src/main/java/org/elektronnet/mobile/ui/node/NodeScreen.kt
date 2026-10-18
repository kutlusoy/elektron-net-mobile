package org.elektronnet.mobile.ui.node

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.elektronnet.mobile.R
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.service.NodeNotification
import org.elektronnet.mobile.ui.GlassCard
import org.elektronnet.mobile.ui.HeroCard
import org.elektronnet.mobile.ui.NodeChipRow
import org.elektronnet.mobile.ui.StatTile
import org.elektronnet.mobile.ui.model.HomeUiModel
import org.elektronnet.mobile.ui.model.NodeChip
import org.elektronnet.mobile.ui.model.NodeDetails
import org.elektronnet.mobile.ui.model.NodeTabModel
import org.elektronnet.mobile.ui.model.NodeTabState

@Composable
fun NodeScreen(
    model: NodeTabModel,
    peers: List<NodeHost.PeerRow> = emptyList(),
    showGeoCredit: Boolean = false,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeroCard {
            NodeChipRow(chipFor(model.state))
            Text(title(model), style = MaterialTheme.typography.titleLarge)
            hint(model)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            model.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)) }
            if (model.state == NodeTabState.CatchingUp && model.percent != null) {
                LinearProgressIndicator(progress = { model.percent / 100f }, modifier = Modifier.fillMaxWidth())
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (model.canPause) {
                OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_pause)) }
            }
            if (model.canResume) {
                Button(onClick = onResume, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_resume)) }
            }
            if (model.canStart) {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_start)) }
            }
            if (model.canStop) {
                OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_stop)) }
            }
        }
        Text(stringResource(R.string.node_explainer), color = MaterialTheme.colorScheme.onSurfaceVariant)
        model.details?.let { StatGrid(it) }
        if (model.details != null) PeerSection(peers, showGeoCredit)
    }
}

@Composable
private fun PeerSection(peers: List<NodeHost.PeerRow>, showGeoCredit: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.peers_title, peers.size), style = MaterialTheme.typography.titleLarge)
        if (peers.isEmpty()) Text(stringResource(R.string.peers_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
        peers.forEach { PeerCard(it) }
        if (showGeoCredit) Text(stringResource(R.string.peers_geo_credit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PeerCard(row: NodeHost.PeerRow) {
    val peer = row.peer
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(peer.address, fontFamily = org.elektronnet.mobile.ui.theme.ElektronMono, modifier = Modifier.weight(1f))
                Text(
                    stringResource(if (peer.inbound) R.string.peer_inbound else R.string.peer_outbound),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (peer.subversion.isNotEmpty()) Text(peer.subversion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            row.country?.let { Text("${it.flag} ${it.name}", color = MaterialTheme.colorScheme.primary) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                peer.pingMs?.let { Text(stringResource(R.string.peer_ping, it.toInt()), style = MaterialTheme.typography.bodySmall) }
                peer.startingHeight?.takeIf { it >= 0 }?.let { Text(stringResource(R.string.peer_height, it), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun StatGrid(details: NodeDetails) {
    val percent = if (details.headers > 0) "%.1f %%".format(details.blocks * 100.0 / details.headers) else "-"
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TileRow(
            stringResource(R.string.tile_blocks) to details.blocks.toString(),
            stringResource(R.string.tile_headers) to details.headers.toString(),
        )
        TileRow(
            stringResource(R.string.tile_peers) to details.peers.toString(),
            stringResource(R.string.tile_progress) to percent,
        )
        TileRow(
            stringResource(R.string.detail_chain) to details.chain,
            stringResource(R.string.detail_pruned) to (details.pruneHeight?.toString() ?: "-"),
        )
        StatTile(stringResource(R.string.detail_version), details.version, Modifier.fillMaxWidth())
    }
}

@Composable
private fun TileRow(left: Pair<String, String>, right: Pair<String, String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile(left.first, left.second, Modifier.weight(1f))
        StatTile(right.first, right.second, Modifier.weight(1f))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

private fun chipFor(state: NodeTabState): NodeChip = when (state) {
    NodeTabState.UpToDate -> NodeChip.Synced
    NodeTabState.CatchingUp, NodeTabState.Starting -> NodeChip.Syncing
    NodeTabState.Paused -> NodeChip.Paused
    NodeTabState.Stopping, NodeTabState.Stopped -> NodeChip.Stopped
    NodeTabState.Problem -> NodeChip.Problem
}

@Composable
private fun title(model: NodeTabModel): String = when (model.state) {
    NodeTabState.UpToDate -> stringResource(R.string.node_title_up_to_date)
    NodeTabState.CatchingUp -> stringResource(R.string.node_title_catching_up, model.percent ?: 0)
    NodeTabState.Paused -> stringResource(R.string.node_title_paused)
    NodeTabState.Starting -> stringResource(R.string.node_title_starting)
    NodeTabState.Stopping -> stringResource(R.string.node_title_stopping)
    NodeTabState.Stopped -> stringResource(R.string.node_title_stopped)
    NodeTabState.Problem -> stringResource(R.string.node_title_problem)
}

@Composable
private fun hint(model: NodeTabModel): String? = when (model.state) {
    NodeTabState.UpToDate -> stringResource(R.string.node_hint_up_to_date)
    NodeTabState.Paused -> stringResource(R.string.node_hint_paused, stringResource(NodeNotification.reasonRes(model.reason)))
    NodeTabState.Stopped -> stringResource(R.string.node_hint_stopped)
    NodeTabState.Problem -> stringResource(
        when (model.failure) {
            org.elektronnet.mobile.node.NodeFailure.DiskFull -> R.string.note_problem_disk
            org.elektronnet.mobile.node.NodeFailure.CorruptedDatabase -> R.string.note_problem_database
            org.elektronnet.mobile.node.NodeFailure.AlreadyRunning -> R.string.note_problem_running
            org.elektronnet.mobile.node.NodeFailure.StartFailed -> R.string.note_problem_start
            else -> R.string.note_problem_unknown
        },
    )
    else -> null
}
