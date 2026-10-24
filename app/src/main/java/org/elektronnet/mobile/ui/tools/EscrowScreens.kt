package org.elektronnet.mobile.ui.tools

import org.elektronnet.mobile.ui.SecretField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.elektronnet.mobile.R
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.ui.SecureWindow
import org.elektronnet.mobile.ui.model.HistoryModel
import org.elektronnet.mobile.ui.receive.QrView
import org.elektronnet.mobile.ui.scan.QrScannerScreen
import org.elektronnet.mobile.wallet.Amount
import org.elektronnet.mobile.wallet.Balances
import org.elektronnet.mobile.wallet.Cosigner
import org.elektronnet.mobile.wallet.EscrowException
import org.elektronnet.mobile.wallet.EscrowFailure
import org.elektronnet.mobile.wallet.EscrowManager
import org.elektronnet.mobile.wallet.EscrowPhase
import org.elektronnet.mobile.wallet.EscrowRecord
import org.elektronnet.mobile.wallet.TxItem
import org.elektronnet.mobile.ui.theme.ElektronMono

private val KEY_TOKEN = Regex("""(\[[0-9a-fA-F]{8}(?:/\d+['h])*])?[xt]pub[1-9A-HJ-NP-Za-km-z]{100,115}""")

fun parseCosigners(text: String): List<Cosigner> = text.lines().mapNotNull { line ->
    val match = KEY_TOKEN.find(line) ?: return@mapNotNull null
    val label = line.substring(0, match.range.first).trim().trimEnd(':', '=', '-').trim()
    Cosigner(label, match.value)
}

@Composable
fun EscrowListScreen(onBack: () -> Unit, onOpen: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<EscrowRecord>>(emptyList()) }
    var creating by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload, NodeHost.activeWallet) {
        records = withContext(Dispatchers.IO) { NodeHost.escrowManager()?.list().orEmpty() }
    }
    ToolPage(stringResource(R.string.escrow_title), onBack) {
        Hint(stringResource(R.string.escrow_intro))
        val owner = NodeHost.wallets.firstOrNull { it.name == NodeHost.activeWallet }
        Text(stringResource(R.string.escrow_of_wallet, owner?.label?.ifBlank { null } ?: NodeHost.activeWallet), color = MaterialTheme.colorScheme.primary)
        if (records.isEmpty()) Text(stringResource(R.string.escrow_none))
        records.forEach { r ->
            OutlinedButton(onClick = { onOpen(r.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(r.name)
                    Text(
                        if (r.phase == EscrowPhase.Active) stringResource(R.string.escrow_threshold, r.required, r.total) else stringResource(R.string.escrow_setup),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.escrow_new)) }
        OutlinedButton(onClick = { restoring = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.escrow_restore)) }
    }
    if (creating) {
        EscrowDialog(
            title = stringResource(R.string.escrow_new),
            fields = listOf(R.string.escrow_name, R.string.escrow_my_role),
            onDismiss = { creating = false },
            onConfirm = { values, pass, report ->
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { (NodeHost.escrowManager() ?: throw EscrowException(EscrowFailure.NodeNotReady)).createKey(values[0], values[1], pass) }
                    }
                    result.onSuccess { creating = false; reload++; onOpen(it.id) }
                    result.onFailure { report(escrowErrorText((it as? EscrowException)?.failure ?: EscrowFailure.Unknown)) }
                }
            },
        )
    }
    if (restoring) {
        EscrowDialog(
            title = stringResource(R.string.escrow_restore),
            fields = listOf(R.string.escrow_name, R.string.escrow_descriptor_field),
            onDismiss = { restoring = false },
            onConfirm = { values, pass, report ->
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val m = NodeHost.escrowManager() ?: throw EscrowException(EscrowFailure.NodeNotReady)
                            val descriptor = Regex("""wsh\(sortedmulti\([^\s]+""").find(values[1])?.value ?: values[1]
                            val labels = parseCosigners(values[1]).associate { it.key to it.label }
                            m.restore(values[0], descriptor, labels, pass)
                        }
                    }
                    result.onSuccess { restoring = false; reload++; onOpen(it.id) }
                    result.onFailure { report(escrowErrorText((it as? EscrowException)?.failure ?: EscrowFailure.Unknown)) }
                }
            },
        )
    }
}

@Composable
private fun EscrowDialog(
    title: String,
    fields: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>, String, (Int) -> Unit) -> Unit,
) {
    val values = remember { mutableStateOf(List(fields.size) { "" }) }
    var passphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    SecureWindow()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.forEachIndexed { i, label ->
                    OutlinedTextField(
                        value = values.value[i],
                        onValueChange = { v -> values.value = values.value.toMutableList().also { it[i] = v }; error = null },
                        label = { Text(stringResource(label)) },
                        minLines = if (label == R.string.escrow_descriptor_field) 3 else 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SecretField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.fr_pass_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { ErrorText(stringResource(it)) }
            }
        },
        confirmButton = {
            TextButton(enabled = passphrase.isNotEmpty() && values.value[0].isNotBlank(), onClick = {
                val pass = passphrase
                passphrase = ""
                onConfirm(values.value, pass) { error = it }
            }) { Text(stringResource(R.string.escrow_continue)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_back)) } },
    )
}

@Composable
fun EscrowDetailScreen(id: Int, onBack: () -> Unit, onPay: (EscrowRecord) -> Unit, onPsbt: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var record by remember { mutableStateOf<EscrowRecord?>(null) }
    var address by remember { mutableStateOf<String?>(null) }
    var balances by remember { mutableStateOf<Balances?>(null) }
    var items by remember { mutableStateOf<List<TxItem>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    var keysText by remember { mutableStateOf("") }
    var required by remember { mutableStateOf(2) }
    var passphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }

    LaunchedEffect(id, reload) {
        withContext(Dispatchers.IO) {
            val m = NodeHost.escrowManager() ?: return@withContext
            val r = runCatching { m.get(id) }.getOrNull() ?: return@withContext
            record = r
            if (r.phase == EscrowPhase.Active) {
                address = runCatching { m.address(id) }.getOrNull()
                runCatching { m.reader(id) }.getOrNull()?.let { reader ->
                    balances = runCatching { reader.balances() }.getOrNull()
                    items = runCatching { reader.transactions(20) }.getOrDefault(emptyList())
                }
            }
        }
    }
    SecureWindow()
    if (scanning) {
        QrScannerScreen(
            onResult = { keysText = (keysText.trim() + "\n" + it).trim(); scanning = false },
            onClose = { scanning = false },
        )
        return
    }
    val r = record
    ToolPage(r?.name ?: stringResource(R.string.escrow_title), onBack) {
        if (r == null) {
            CircularProgressIndicator()
            return@ToolPage
        }
        error?.let { ErrorText(stringResource(it)) }
        if (r.phase == EscrowPhase.KeyOnly) {
            Text(stringResource(R.string.escrow_step_share), style = MaterialTheme.typography.titleMedium)
            Hint(stringResource(R.string.escrow_share_hint))
            val shareLine = r.myLabel + ": " + r.myKey
            Text(shareLine, fontFamily = ElektronMono, style = MaterialTheme.typography.bodySmall)
            QrView(shareLine)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { copyText(context, "escrow key", shareLine) }) { Text(stringResource(R.string.action_copy)) }
                Button(onClick = { shareText(context, shareLine) }) { Text(stringResource(R.string.action_share)) }
            }
            Text(stringResource(R.string.escrow_step_collect), style = MaterialTheme.typography.titleMedium)
            Hint(stringResource(R.string.escrow_collect_hint))
            OutlinedTextField(
                value = keysText,
                onValueChange = { keysText = it; error = null },
                label = { Text(stringResource(R.string.escrow_keys_field)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { keysText = pasteText(context) }) { Text(stringResource(R.string.send_paste)) }
                OutlinedButton(onClick = { scanning = true }) { Text(stringResource(R.string.scan_button)) }
            }
            val cosigners = parseCosigners(keysText)
            val total = cosigners.size + 1
            Text(stringResource(R.string.escrow_keys_found, cosigners.size))
            if (total >= 2) {
                val effective = required.coerceIn(1, total)
                Text(stringResource(R.string.escrow_threshold_label), style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { required = (effective - 1).coerceAtLeast(1) }) { Text("-") }
                    Text(stringResource(R.string.escrow_threshold, effective, total), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { required = (effective + 1).coerceAtMost(total) }) { Text("+") }
                }
                if (effective == 1) Text(stringResource(R.string.escrow_threshold_one), color = MaterialTheme.colorScheme.error)
                SecretField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.fr_pass_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = passphrase.isNotEmpty() && !busy,
                    onClick = {
                        val pass = passphrase
                        passphrase = ""
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { (NodeHost.escrowManager() ?: throw EscrowException(EscrowFailure.NodeNotReady)).activate(id, effective, cosigners, pass) }
                            }
                            busy = false
                            result.onSuccess { error = null; reload++ }
                            result.onFailure { error = escrowErrorText((it as? EscrowException)?.failure ?: EscrowFailure.Unknown) }
                        }
                    },
                ) { Text(stringResource(R.string.escrow_activate)) }
            }
        } else {
            Text(stringResource(R.string.escrow_threshold, r.required, r.total), style = MaterialTheme.typography.titleMedium)
            Text(r.myLabel + " (" + stringResource(R.string.escrow_you) + ")")
            r.cosigners.forEach { Text(it.label.ifEmpty { "-" }) }
            val checksum = r.descriptor?.substringAfter('#').orEmpty()
            Text(stringResource(R.string.escrow_code, checksum), fontFamily = ElektronMono)
            Hint(stringResource(R.string.escrow_code_hint))
            balances?.let {
                Text(Amount.display(it.trustedLep) + " " + Amount.unitSymbol(), style = MaterialTheme.typography.headlineMedium)
                val pending = it.pendingLep + it.immatureLep
                if (pending != 0L) Hint(stringResource(R.string.balance_pending, Amount.display(Math.abs(pending)), Amount.unitSymbol()))
            }
            address?.let { a ->
                Text(stringResource(R.string.escrow_deposit), style = MaterialTheme.typography.labelLarge)
                QrView(a.uppercase())
                Text(org.elektronnet.mobile.wallet.AddressFormat.grouped(a), fontFamily = ElektronMono)
                Hint(stringResource(R.string.escrow_address_hint))
                OutlinedButton(onClick = { copyText(context, "address", a) }) { Text(stringResource(R.string.action_copy)) }
            }
            Button(onClick = { onPay(r) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.escrow_pay)) }
            OutlinedButton(onClick = onPsbt, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.escrow_sign_request)) }
            items.forEach { tx ->
                val row = HistoryModel.row(tx)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(HistoryModel.shorten(tx.txid), fontFamily = ElektronMono, style = MaterialTheme.typography.bodySmall)
                    Text(row.amountText + " " + Amount.unitSymbol(), fontFamily = ElektronMono)
                }
            }
            OutlinedButton(onClick = {
                scope.launch {
                    val text = withContext(Dispatchers.IO) { runCatching { NodeHost.escrowManager()?.backupText(id) }.getOrNull() }
                    if (text != null) shareText(context, text)
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.escrow_backup)) }
            Hint(stringResource(R.string.escrow_backup_hint))
        }
        TextButton(onClick = { confirmRemove = true }) { Text(stringResource(R.string.escrow_remove), color = MaterialTheme.colorScheme.error) }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            text = { Text(stringResource(R.string.escrow_remove_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { NodeHost.escrowManager()?.removeEscrow(id, force = false) } }
                        result.onSuccess { onBack() }
                        result.onFailure { error = escrowErrorText((it as? EscrowException)?.failure ?: EscrowFailure.Unknown) }
                    }
                }) { Text(stringResource(R.string.escrow_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.action_back)) } },
        )
    }
}

private fun escrowErrorText(failure: EscrowFailure): Int = when (failure) {
    EscrowFailure.NameMissing -> R.string.escrow_err_name
    EscrowFailure.WalletMissing -> R.string.escrow_err_wallet
    EscrowFailure.WrongPassphrase -> R.string.send_err_passphrase
    EscrowFailure.InvalidKey -> R.string.escrow_err_key
    EscrowFailure.DuplicateKey -> R.string.escrow_err_duplicate
    EscrowFailure.WrongNetwork -> R.string.escrow_err_network
    EscrowFailure.BadThreshold -> R.string.escrow_err_threshold
    EscrowFailure.TooManyKeys -> R.string.escrow_err_many
    EscrowFailure.InvalidDescriptor -> R.string.escrow_err_descriptor
    EscrowFailure.NotOurKey -> R.string.escrow_err_not_ours
    EscrowFailure.NotKeyOnly, EscrowFailure.NotActive -> R.string.escrow_err_state
    EscrowFailure.HasFunds -> R.string.escrow_err_funds
    EscrowFailure.ImportFailed -> R.string.escrow_err_import
    EscrowFailure.NodeNotReady -> R.string.fr_err_node
    EscrowFailure.Unknown -> R.string.send_err_unknown
}
