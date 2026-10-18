package org.elektronnet.mobile.ui.wallet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.elektronnet.mobile.R
import org.elektronnet.mobile.node.NodeFailure
import org.elektronnet.mobile.service.NodeNotification
import org.elektronnet.mobile.ui.HeroCard
import org.elektronnet.mobile.ui.NodeChipRow
import org.elektronnet.mobile.ui.model.BalanceUi
import org.elektronnet.mobile.ui.model.HistoryDay
import org.elektronnet.mobile.ui.model.HomeUiModel
import org.elektronnet.mobile.ui.model.SyncNote

@Composable
fun WalletScreen(
    model: HomeUiModel,
    balance: BalanceUi?,
    days: List<HistoryDay>,
    onOpenTx: (String) -> Unit,
    onOpenNode: () -> Unit,
    onReceive: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        NodeChipRow(model.chip, Modifier.clickable(onClick = onOpenNode))
        HeroCard {
            Text(stringResource(R.string.balance_label), color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                balance?.amountText != null ->
                    Text("${balance.amountText} ${stringResource(R.string.balance_unit)}", style = MaterialTheme.typography.displaySmall)
                balance?.waitingForSync == true ->
                    Text(stringResource(R.string.balance_waiting), style = MaterialTheme.typography.titleMedium)
                else -> Text(stringResource(R.string.balance_unavailable), style = MaterialTheme.typography.titleMedium)
            }
            balance?.pendingText?.let {
                Text(
                    stringResource(R.string.balance_pending, it, stringResource(R.string.balance_unit)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            model.syncNote?.let { note ->
                SyncNoteView(note)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onReceive, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_receive))
            }
            Button(onClick = onSend, enabled = model.canSend, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Text(stringResource(R.string.action_send), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (!model.canSend) {
            Text(
                stringResource(R.string.send_needs_sync),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (days.isEmpty()) {
            Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            days.forEach { day -> HistoryDaySection(day, onOpenTx) }
        }
        Text(
            stringResource(R.string.history_limit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SyncNoteView(note: SyncNote) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (note) {
        is SyncNote.CatchingUp -> {
            Text(stringResource(R.string.note_catching_up, note.percent), color = muted)
            LinearProgressIndicator(progress = { note.percent / 100f }, modifier = Modifier.fillMaxWidth())
        }
        SyncNote.Starting -> Text(stringResource(R.string.note_starting), color = muted)
        is SyncNote.Paused -> Text(
            stringResource(R.string.note_paused, stringResource(NodeNotification.reasonRes(note.reason))),
            color = muted,
        )
        SyncNote.Stopped -> Text(stringResource(R.string.note_stopped), color = muted)
        is SyncNote.Problem -> Text(
            stringResource(
                when (note.failure) {
                    NodeFailure.DiskFull -> R.string.note_problem_disk
                    NodeFailure.CorruptedDatabase -> R.string.note_problem_database
                    NodeFailure.AlreadyRunning -> R.string.note_problem_running
                    NodeFailure.Unknown -> R.string.note_problem_unknown
                },
            ),
            color = muted,
        )
    }
}
