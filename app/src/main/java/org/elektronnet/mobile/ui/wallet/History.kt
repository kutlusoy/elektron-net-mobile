package org.elektronnet.mobile.ui.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.ui.model.SendInput
import org.elektronnet.mobile.wallet.BumpException
import org.elektronnet.mobile.wallet.BumpFailure
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.elektronnet.mobile.R
import org.elektronnet.mobile.ui.model.HistoryDay
import org.elektronnet.mobile.ui.model.HistoryRow
import org.elektronnet.mobile.wallet.Amount
import org.elektronnet.mobile.wallet.TxItem
import org.elektronnet.mobile.wallet.TxKind
import org.elektronnet.mobile.wallet.TxStatus
import org.elektronnet.mobile.ui.theme.ElektronMono

@Composable
internal fun HistoryDaySection(day: HistoryDay, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(dayTitle(day.date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        day.rows.forEach { row -> HistoryRowView(row) { onOpen(row.txid) } }
    }
}

@Composable
private fun dayTitle(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.day_today)
        today.minusDays(1) -> stringResource(R.string.day_yesterday)
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

@Composable
private fun HistoryRowView(row: HistoryRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (row.incoming) "↓" else "↑", style = MaterialTheme.typography.titleLarge)
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(kindLabel(row.kind)))
            val detail = listOfNotNull(row.subtitle, statusWord(row.status, row.confirmations)).joinToString("  ")
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("${row.amountText} ${stringResource(R.string.balance_unit)}", fontFamily = ElektronMono)
    }
}

@Composable
private fun statusWord(status: TxStatus, confirmations: Int): String? = when (status) {
    TxStatus.Pending -> stringResource(R.string.tx_pending)
    TxStatus.Confirming -> stringResource(R.string.tx_confirming, confirmations, TxItem.CONFIRMED_AT)
    TxStatus.Confirmed -> null
    TxStatus.Conflicted -> stringResource(R.string.tx_conflicted)
    TxStatus.Abandoned -> stringResource(R.string.tx_abandoned)
}

private fun kindLabel(kind: TxKind): Int = when (kind) {
    TxKind.Received -> R.string.tx_received
    TxKind.Sent -> R.string.tx_sent
    TxKind.Moved -> R.string.tx_moved
    TxKind.Mined -> R.string.tx_mined
}

@Composable
fun TxDetailsScreen(item: TxItem, onClose: () -> Unit, onBumped: (String) -> Unit = {}) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_back)) }
            Text(stringResource(kindLabel(item.kind)), style = MaterialTheme.typography.titleLarge)
        }
        val status = when (item.status) {
            TxStatus.Pending -> stringResource(R.string.tx_status_pending)
            TxStatus.Confirming, TxStatus.Confirmed -> stringResource(R.string.tx_status_confirmed, item.confirmations)
            TxStatus.Conflicted -> stringResource(R.string.tx_conflicted)
            TxStatus.Abandoned -> stringResource(R.string.tx_abandoned)
        }
        Text(status)
        val sign = if (item.netLep > 0) "+" else if (item.netLep < 0) "-" else ""
        Text(
            "$sign${Amount.format(Math.abs(item.netLep))} ${stringResource(R.string.balance_unit)}",
            style = MaterialTheme.typography.displaySmall,
        )
        if (item.timeSeconds > 0) {
            val formatted = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .format(Instant.ofEpochSecond(item.timeSeconds).atZone(ZoneId.systemDefault()))
            DetailLine(stringResource(R.string.tx_date), formatted)
        }
        item.address?.let { DetailLine(stringResource(R.string.tx_address), it, mono = true) }
        item.feeLep?.let { DetailLine(stringResource(R.string.tx_fee), "${Amount.format(it)} ${stringResource(R.string.balance_unit)}") }
        DetailLine(stringResource(R.string.tx_id), item.txid, mono = true)
        OutlinedButton(onClick = { copy(context, item.txid) }) { Text(stringResource(R.string.action_copy)) }
        NodeHost.explorerTxUrl(item.txid)?.let { link ->
            OutlinedButton(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))) }) {
                Text(stringResource(R.string.tx_open_explorer))
            }
        }
        if (item.kind == TxKind.Sent && item.status == TxStatus.Pending) BumpSection(item.txid, onBumped)
    }
}

@Composable
private fun BumpSection(txid: String, onBumped: (String) -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var oldRate by remember { androidx.compose.runtime.mutableStateOf<java.math.BigDecimal?>(null) }
    var rateText by remember { androidx.compose.runtime.mutableStateOf("") }
    var passphrase by remember { androidx.compose.runtime.mutableStateOf("") }
    var open by remember { androidx.compose.runtime.mutableStateOf(false) }
    var busy by remember { androidx.compose.runtime.mutableStateOf(false) }
    var failure by remember { androidx.compose.runtime.mutableStateOf<BumpFailure?>(null) }
    var done by remember { androidx.compose.runtime.mutableStateOf(false) }
    val rate = rateText.trim().replace(',', '.').toBigDecimalOrNull()
    val valid = rate != null && oldRate?.let { rate > it } == true && rate <= java.math.BigDecimal(SendInput.MAX_CUSTOM_RATE)
    androidx.compose.runtime.LaunchedEffect(txid, open) {
        if (open && oldRate == null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { NodeHost.feeBumper()?.let { it.currentRate(txid) to it.suggestedRate(txid) } }.getOrNull()
            }
            if (loaded == null) {
                failure = BumpFailure.NotBumpable
            } else {
                oldRate = loaded.first
                rateText = loaded.second.toPlainString()
            }
        }
    }
    if (done) {
        Text(stringResource(R.string.bump_done), color = MaterialTheme.colorScheme.primary)
        return
    }
    if (!open) {
        Button(onClick = { open = true }) { Text(stringResource(R.string.bump_action)) }
        return
    }
    Text(stringResource(R.string.bump_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.bump_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    oldRate?.let { Text(stringResource(R.string.bump_current, it.toPlainString())) }
    androidx.compose.material3.OutlinedTextField(
        value = rateText,
        onValueChange = { rateText = it },
        label = { Text(stringResource(R.string.bump_new_rate)) },
        isError = rateText.isNotEmpty() && !valid,
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    androidx.compose.material3.OutlinedTextField(
        value = passphrase,
        onValueChange = { passphrase = it },
        label = { Text(stringResource(R.string.fr_pass_label)) },
        singleLine = true,
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    failure?.let { Text(stringResource(bumpError(it)), color = MaterialTheme.colorScheme.error) }
    Button(
        enabled = valid && passphrase.isNotEmpty() && !busy,
        onClick = {
            val pass = passphrase
            passphrase = ""
            failure = null
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { NodeHost.feeBumper()?.bump(txid, rate!!, pass) ?: throw BumpException(BumpFailure.NodeNotReady) }
                }
                busy = false
                result.onSuccess { done = true; onBumped(it) }
                result.onFailure { failure = (it as? BumpException)?.failure ?: BumpFailure.Unknown }
            }
        },
    ) { Text(stringResource(R.string.bump_confirm)) }
}

private fun bumpError(failure: BumpFailure): Int = when (failure) {
    BumpFailure.NotBumpable -> R.string.bump_err_not_bumpable
    BumpFailure.FeeTooLow -> R.string.bump_err_fee
    BumpFailure.WrongPassphrase -> R.string.send_err_passphrase
    BumpFailure.Mismatch -> R.string.send_err_mismatch
    BumpFailure.NodeNotReady -> R.string.fr_err_node
    BumpFailure.Rejected, BumpFailure.Unknown -> R.string.bump_err_unknown
}

@Composable
private fun DetailLine(label: String, value: String, mono: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = if (mono) ElektronMono else null)
    }
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("txid", text))
}
