package org.elektronnet.mobile.ui.node

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.elektronnet.mobile.ui.model.NodeHealth
import org.elektronnet.mobile.ui.model.NodeHealthRules
import org.elektronnet.mobile.ui.model.NodeLogPhase
import org.elektronnet.mobile.ui.model.NodePhase
import org.elektronnet.mobile.ui.model.SyncEta
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
import org.elektronnet.mobile.ui.InfoRow
import org.elektronnet.mobile.ui.StatTile
import org.elektronnet.mobile.ui.model.HomeUiModel
import org.elektronnet.mobile.ui.model.NodeChip
import org.elektronnet.mobile.ui.model.PruneSchedule
import org.elektronnet.mobile.ui.model.TelemetryFormat
import org.elektronnet.mobile.ui.model.NodeDetails
import org.elektronnet.mobile.ui.model.NodeTabModel
import org.elektronnet.mobile.ui.model.NodeTabState

@Composable
fun NodeScreen(
    model: NodeTabModel,
    peers: List<NodeHost.PeerRow> = emptyList(),
    telemetry: org.elektronnet.mobile.node.Telemetry? = null,
    showGeoCredit: Boolean = false,
    onOpenConsole: () -> Unit = {},
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLog by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var log by remember { mutableStateOf(emptyList<String>()) }
    val watching = showLog || model.state == NodeTabState.Starting
    LaunchedEffect(watching) {
        while (watching) {
            log = withContext(Dispatchers.IO) { NodeHost.logTail(200) }
            delay(2_000)
        }
    }
    var diag by remember { mutableStateOf<NodeHost.Diagnostics?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var noPeersSince by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            diag = withContext(Dispatchers.IO) { NodeHost.diagnostics() }
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val peerCount = model.details?.peers
    LaunchedEffect(peerCount) { noPeersSince = if (peerCount == 0L) System.currentTimeMillis() else null }
    val running = model.details != null
    val starting = model.state == NodeTabState.Starting
    val health = NodeHealthRules.assess(
        starting = starting,
        running = running,
        elapsedMs = diag?.let { if (it.startedAt > 0) now - it.startedAt else 0 } ?: 0,
        silentMs = diag?.let { if (it.lastLogAt > 0) now - it.lastLogAt else 0 } ?: 0,
        peers = peerCount ?: -1,
        noPeersForMs = noPeersSince?.let { now - it } ?: 0,
    )
    val phase = NodeLogPhase.of(log)
    val eta = remember { SyncEta() }
    val details = model.details
    LaunchedEffect(details?.blocks) { details?.let { eta.add(System.currentTimeMillis(), it.blocks) } }
    val etaText = details?.let { eta.remainingMs((it.headers - it.blocks).coerceAtLeast(0)) }?.let { etaLabel(it) }
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
            ProgressBlock(model, phase, etaText)
            if (starting || running) {
                DiagnosticsBlock(
                    starting = starting,
                    peers = peerCount,
                    elapsed = diag?.takeIf { it.startedAt > 0 }?.let { now - it.startedAt },
                    sinceLog = diag?.takeIf { it.lastLogAt > 0 }?.let { now - it.lastLogAt },
                    warmup = diag?.warmup,
                    rpcProblem = diag?.rpcProblem,
                    health = health,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (model.canPause) {
                OutlinedButton(onClick = { confirm = Confirm.Pause }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_pause)) }
            }
            if (model.canResume) {
                Button(onClick = onResume, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_resume)) }
            }
            if (model.canStart) {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_start)) }
            }
            if (model.canStop) {
                OutlinedButton(onClick = { confirm = Confirm.Stop }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_stop)) }
            }
        }
        confirm?.let { pending ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirm = null },
                title = { Text(stringResource(if (pending == Confirm.Stop) R.string.confirm_stop_title else R.string.confirm_pause_title)) },
                text = { Text(stringResource(if (pending == Confirm.Stop) R.string.confirm_stop_body else R.string.confirm_pause_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirm = null
                        if (pending == Confirm.Stop) onStop() else onPause()
                    }) { Text(stringResource(if (pending == Confirm.Stop) R.string.action_stop else R.string.action_pause)) }
                },
                dismissButton = { OutlinedButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
        Text(stringResource(R.string.node_explainer), color = MaterialTheme.colorScheme.onSurfaceVariant)
        model.details?.let { StatGrid(it) }
        LogSection(showLog, { showLog = it }, log)
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.node_expert_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.node_expert_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onOpenConsole) { Text(stringResource(R.string.node_expert_open)) }
            }
        }
        MaintenanceCard(onRestart = onStart)
        if (model.details != null) {
            TelemetrySection(model.details, telemetry)
            PeerSection(peers, showGeoCredit)
        }
    }
}

@Composable
private fun DiagnosticsBlock(starting: Boolean, peers: Long?, elapsed: Long?, sinceLog: Long?, warmup: String?, rpcProblem: String?, health: NodeHealth) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val small = MaterialTheme.typography.bodySmall
    if (peers != null) {
        Text(
            if (peers > 0) stringResource(R.string.diag_peers, peers) else stringResource(R.string.diag_no_peers),
            color = if (peers > 0) MaterialTheme.colorScheme.primary else muted,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
    }
    if (starting && !warmup.isNullOrBlank()) Text(warmup, color = muted, style = small)
    if (starting && !rpcProblem.isNullOrBlank()) Text(stringResource(R.string.diag_rpc_problem, rpcProblem), color = MaterialTheme.colorScheme.error, style = small)
    if (elapsed != null && starting) Text(stringResource(R.string.diag_elapsed, NodeHealthRules.clock(elapsed)), color = muted, style = small)
    if (sinceLog != null) Text(stringResource(R.string.diag_last_log, NodeHealthRules.clock(sinceLog)), color = muted, style = small)
    val warning = when (health) {
        NodeHealth.Silent -> R.string.health_silent
        NodeHealth.SlowStart -> R.string.health_slow_start
        NodeHealth.NoPeers -> R.string.health_no_peers
        NodeHealth.Ok -> null
    }
    warning?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = small) }
}

@Composable
private fun ProgressBlock(model: NodeTabModel, phase: NodePhase, etaText: String?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (model.state) {
        NodeTabState.Starting -> {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(stringResource(phaseText(phase)), color = muted, style = MaterialTheme.typography.bodySmall)
        }
        NodeTabState.CatchingUp -> {
            val details = model.details
            if (model.percent != null && details != null && details.headers > 0) {
                LinearProgressIndicator(progress = { model.percent / 100f }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.sync_blocks_of, details.blocks, details.headers), color = muted, style = MaterialTheme.typography.bodySmall)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.phase_headers), color = muted, style = MaterialTheme.typography.bodySmall)
            }
            etaText?.let { Text(it, color = muted, style = MaterialTheme.typography.bodySmall) }
        }
        else -> Unit
    }
}

private fun phaseText(phase: NodePhase): Int = when (phase) {
    NodePhase.LoadingIndex -> R.string.phase_index
    NodePhase.VerifyingBlocks -> R.string.phase_verify
    NodePhase.LoadingWallet -> R.string.phase_wallet
    NodePhase.StartingNetwork -> R.string.phase_network
    NodePhase.SyncingHeaders -> R.string.phase_headers
    NodePhase.Other -> R.string.phase_starting
}

@Composable
private fun etaLabel(ms: Long): String {
    val minutes = ms / 60_000
    val text = when {
        minutes < 1 -> stringResource(R.string.eta_under_minute)
        minutes < 90 -> stringResource(R.string.eta_minutes, minutes)
        else -> stringResource(R.string.eta_hours, minutes / 60, minutes % 60)
    }
    return stringResource(R.string.eta_left, text)
}

@Composable
private fun LogSection(show: Boolean, onShow: (Boolean) -> Unit, lines: List<String>) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.node_log_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.node_log_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Switch(checked = show, onCheckedChange = onShow)
        }
        if (show) {
            val scroll = rememberScrollState()
            LaunchedEffect(lines.size) { scroll.scrollTo(scroll.maxValue) }
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(scroll)) {
                        if (lines.isEmpty()) Text(stringResource(R.string.node_log_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        lines.forEach {
                            Text(it, fontFamily = org.elektronnet.mobile.ui.theme.ElektronMono, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                    if (lines.isNotEmpty()) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Row {
                            TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(org.elektronnet.mobile.node.LogSanitizer.clean(lines))) }) {
                                Text(stringResource(R.string.node_log_copy))
                            }
                            TextButton(onClick = { org.elektronnet.mobile.ui.tools.shareText(context, org.elektronnet.mobile.node.LogSanitizer.clean(lines)) }) {
                                Text(stringResource(R.string.node_log_share))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetrySection(details: NodeDetails, telemetry: org.elektronnet.mobile.node.Telemetry?) {
    val dash = "-"
    val schedule = PruneSchedule.of(details.blocks, System.currentTimeMillis())
    val date = schedule.estimatedMs?.let { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(it)) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.telemetry_title), style = MaterialTheme.typography.titleLarge)
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.telemetry_chain_card), style = MaterialTheme.typography.titleMedium)
                TelemetryRow(stringResource(R.string.detail_chain), details.chain)
                TelemetryRow(stringResource(R.string.telemetry_difficulty), telemetry?.difficulty?.let(TelemetryFormat::difficulty) ?: dash)
                TelemetryRow(stringResource(R.string.telemetry_hashrate), telemetry?.hashPerSecond?.let(TelemetryFormat::hashRate) ?: dash)
                TelemetryRow(
                    stringResource(R.string.telemetry_mempool),
                    if (telemetry?.mempoolTx != null) stringResource(R.string.telemetry_mempool_value, telemetry.mempoolTx, TelemetryFormat.bytes(telemetry.mempoolBytes ?: 0L)) else dash,
                )
                TelemetryRow(stringResource(R.string.telemetry_storage), telemetry?.sizeOnDisk?.let(TelemetryFormat::bytes) ?: dash)
            }
        }
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.net_card), style = MaterialTheme.typography.titleMedium)
                TelemetryRow(stringResource(R.string.net_type), stringResource(R.string.net_type_value))
                TelemetryRow(stringResource(R.string.detail_version), details.version)
                TelemetryRow(stringResource(R.string.net_protocol), telemetry?.protocolVersion?.toString() ?: dash)
                TelemetryRow(
                    stringResource(R.string.detail_peers),
                    if (telemetry?.connectionsIn != null) stringResource(R.string.net_connections_value, details.peers, telemetry.connectionsIn, telemetry.connectionsOut ?: 0L) else details.peers.toString(),
                )
                TelemetryRow(stringResource(R.string.net_active), telemetry?.networkActive?.let { stringResource(if (it) R.string.yes else R.string.no) } ?: dash)
                TelemetryRow(stringResource(R.string.net_ibd), telemetry?.initialBlockDownload?.let { stringResource(if (it) R.string.yes else R.string.no) } ?: dash)
                TelemetryRow(stringResource(R.string.net_p2p), org.elektronnet.mobile.ChainConfig.p2pPort.toString())
                TelemetryRow(stringResource(R.string.net_rpc), "127.0.0.1:" + org.elektronnet.mobile.ChainConfig.rpcPort)
            }
        }
        telemetry?.schedule?.let { ChainEventsCard(it, telemetry.difficulty) }
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.telemetry_prune_card), style = MaterialTheme.typography.titleMedium)
                TelemetryRow(stringResource(R.string.telemetry_keep), schedule.keepBlocks.toString())
                TelemetryRow(stringResource(R.string.detail_pruned), details.pruneHeight?.toString() ?: dash)
                if (date != null) {
                    TelemetryRow(stringResource(R.string.telemetry_remaining), schedule.remainingBlocks.toString())
                    TelemetryRow(stringResource(R.string.telemetry_estimate), "~$date")
                    Text(stringResource(R.string.telemetry_estimate_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.telemetry_pruning_active), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) = InfoRow(label, value)

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
    var open by rememberSaveable(peer.id) { mutableStateOf(false) }
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clickable { open = !open }) {
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
                (peer.syncedBlocks?.takeIf { it >= 0 } ?: peer.startingHeight?.takeIf { it >= 0 })?.let { Text(stringResource(R.string.peer_height, it), style = MaterialTheme.typography.bodySmall) }
            }
            if (open) {
                androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                val dash = "-"
                TelemetryRow(stringResource(R.string.peer_id), peer.id.toString())
                TelemetryRow(stringResource(R.string.peer_network), peer.network.ifEmpty { dash })
                row.country?.let { TelemetryRow(stringResource(R.string.peer_country), "${it.flag} ${it.name} (${it.code})") }
                TelemetryRow(stringResource(R.string.peer_connection_type), peer.connectionType.ifEmpty { dash })
                TelemetryRow(stringResource(R.string.peer_transport), peer.transport.ifEmpty { dash })
                TelemetryRow(stringResource(R.string.net_protocol), peer.version?.toString() ?: dash)
                TelemetryRow(stringResource(R.string.peer_synced_headers), peer.syncedHeaders?.takeIf { it >= 0 }?.toString() ?: dash)
                TelemetryRow(stringResource(R.string.peer_synced_blocks), peer.syncedBlocks?.takeIf { it >= 0 }?.toString() ?: dash)
                TelemetryRow(stringResource(R.string.peer_min_ping), peer.minPingMs?.let { stringResource(R.string.peer_ms, it.toInt()) } ?: dash)
                TelemetryRow(stringResource(R.string.peer_received), peer.bytesRecv?.let(TelemetryFormat::bytes) ?: dash)
                TelemetryRow(stringResource(R.string.peer_sent), peer.bytesSent?.let(TelemetryFormat::bytes) ?: dash)
                TelemetryRow(stringResource(R.string.peer_time_offset), peer.timeOffset?.let { "$it s" } ?: dash)
                if (peer.localBind.isNotEmpty()) TelemetryRow(stringResource(R.string.peer_local_bind), peer.localBind)
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
private fun DetailRow(label: String, value: String) = InfoRow(label, value)

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

@Composable
private fun MaintenanceCard(onRestart: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.node_reset_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.node_reset_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { confirming = true }) { Text(stringResource(R.string.node_reset_action)) }
            if (failed) Text(stringResource(R.string.node_reset_failed), color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirming) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.node_reset_title)) },
            text = { Text(stringResource(R.string.node_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    failed = !NodeHost.resetChainData()
                    if (!failed) onRestart()
                }) { Text(stringResource(R.string.node_reset_action), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { OutlinedButton(onClick = { confirming = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ChainEventsCard(schedule: org.elektronnet.mobile.node.ChainSchedule, difficulty: Double?) {
    val dash = "-"
    val unit = stringResource(R.string.balance_unit)
    val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
    val number = java.text.NumberFormat.getIntegerInstance()
    fun day(ms: Long) = date.format(java.util.Date(ms))
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.events_title), style = MaterialTheme.typography.titleMedium)
            TelemetryRow(stringResource(R.string.events_halving), "~" + day(schedule.halvingAtMs))
            TelemetryRow(stringResource(R.string.events_to_halving), number.format(schedule.blocksToHalving))
            TelemetryRow(
                stringResource(R.string.events_reward),
                org.elektronnet.mobile.wallet.Amount.format(schedule.rewardNowLep) + " \u2192 " + org.elektronnet.mobile.wallet.Amount.format(schedule.rewardNextLep) + " " + unit,
            )
            TelemetryRow(stringResource(R.string.events_epoch), number.format(schedule.epochBlocksMined) + " / " + number.format(schedule.epochLength))
            TelemetryRow(stringResource(R.string.events_avg_block), schedule.avgBlockSeconds?.let { String.format(java.util.Locale.getDefault(), "%.1f s", it) } ?: dash)
            TelemetryRow(stringResource(R.string.events_retarget), "~" + day(schedule.retargetAtMs))
            TelemetryRow(stringResource(R.string.events_to_retarget), number.format(schedule.retargetBlocksLeft))
            val projected = schedule.projectedDifficulty
            val factor = schedule.difficultyFactor
            TelemetryRow(
                stringResource(R.string.events_projection),
                if (projected != null && factor != null) TelemetryFormat.difficulty(projected) + String.format(java.util.Locale.getDefault(), " (%+.1f%%)", (factor - 1) * 100) else dash,
            )
        }
    }
}

private enum class Confirm { Stop, Pause }
