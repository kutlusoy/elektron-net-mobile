@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package org.elektronnet.mobile.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import org.elektronnet.mobile.R
import org.elektronnet.mobile.ui.model.HistoryDay
import org.elektronnet.mobile.ui.model.HistoryFilter
import org.elektronnet.mobile.ui.model.HistoryModel
import org.elektronnet.mobile.ui.model.HistoryRow
import org.elektronnet.mobile.ui.wallets.walletTitle
import org.elektronnet.mobile.wallet.OwnWallet
import org.elektronnet.mobile.wallet.TxItem

private sealed interface Entry {
    data class Header(val day: HistoryDay) : Entry
    data class Item(val row: HistoryRow) : Entry
}

@Composable
fun HistoryScreen(wallet: OwnWallet?, items: List<TxItem>?, onOpenTx: (String) -> Unit, modifier: Modifier = Modifier) {
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.All.name) }
    var query by rememberSaveable { mutableStateOf("") }
    val selected = HistoryFilter.valueOf(filter)
    val shown = remember(items, selected, query) { HistoryModel.filter(items.orEmpty(), selected, query) }
    var page by rememberSaveable { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(filter, query) { page = 0 }
    val pages = HistoryModel.pageCount(shown.size)
    val current = page.coerceIn(0, pages - 1)
    val pageItems = remember(shown, current) { HistoryModel.page(shown, current) }
    val entries = remember(pageItems) {
        HistoryModel.days(pageItems, ZoneId.systemDefault()).flatMap { day -> listOf<Entry>(Entry.Header(day)) + day.rows.map { Entry.Item(it) } }
    }
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.history_title, walletTitle(wallet)), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.history_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryFilter.entries.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { filter = option.name },
                    label = { Text(stringResource(filterLabel(option))) },
                )
            }
        }
        if (items != null) Text(stringResource(R.string.history_count, shown.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            items == null -> Text(stringResource(R.string.history_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            items.isEmpty() -> Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            shown.isEmpty() -> Text(stringResource(R.string.history_none_match), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(entries) { entry ->
                    when (entry) {
                        is Entry.Header -> Text(
                            dayTitle(entry.day.date),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                        )
                        is Entry.Item -> HistoryRowView(entry.row) { onOpenTx(entry.row.txid) }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.history_limit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
        }
        if (pages > 1) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                androidx.compose.material3.OutlinedButton(onClick = { page = current - 1 }, enabled = current > 0) { Text(stringResource(R.string.history_prev)) }
                Text(stringResource(R.string.history_page, current + 1, pages), style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.OutlinedButton(onClick = { page = current + 1 }, enabled = current < pages - 1) { Text(stringResource(R.string.history_next)) }
            }
        }
    }
}

private fun filterLabel(filter: HistoryFilter): Int = when (filter) {
    HistoryFilter.All -> R.string.history_filter_all
    HistoryFilter.Received -> R.string.history_filter_received
    HistoryFilter.Sent -> R.string.history_filter_sent
}
