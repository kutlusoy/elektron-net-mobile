package org.elektronnet.mobile.ui.node

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.elektronnet.mobile.R
import org.elektronnet.mobile.service.NodeNotification
import org.elektronnet.mobile.ui.HeroCard
import org.elektronnet.mobile.ui.NodeChipRow
import org.elektronnet.mobile.ui.model.HomeUiModel
import org.elektronnet.mobile.ui.model.NodeChip
import org.elektronnet.mobile.ui.model.NodeDetails
import org.elektronnet.mobile.ui.model.NodeTabModel
import org.elektronnet.mobile.ui.model.NodeTabState

@Composable
fun NodeScreen(
    model: NodeTabModel,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
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
        model.details?.let { details ->
            TextButton(onClick = { showDetails = !showDetails }) {
                Text(stringResource(if (showDetails) R.string.node_details_hide else R.string.node_details))
            }
            if (showDetails) DetailsList(details)
        }
    }
}

@Composable
private fun DetailsList(details: NodeDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailRow(stringResource(R.string.detail_chain), details.chain)
        HorizontalDivider()
        DetailRow(stringResource(R.string.detail_blocks), stringResource(R.string.detail_blocks_value, details.blocks, details.headers))
        HorizontalDivider()
        DetailRow(stringResource(R.string.detail_peers), details.peers.toString())
        details.pruneHeight?.let {
            HorizontalDivider()
            DetailRow(stringResource(R.string.detail_pruned), it.toString())
        }
        HorizontalDivider()
        DetailRow(stringResource(R.string.detail_version), details.version)
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
            else -> R.string.note_problem_unknown
        },
    )
    else -> null
}
