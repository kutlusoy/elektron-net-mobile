package org.elektronnet.mobile.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import org.elektronnet.mobile.wallet.Amount
import org.elektronnet.mobile.wallet.PsbtStatus
import org.elektronnet.mobile.wallet.PsbtView
import org.elektronnet.mobile.wallet.PsbtWarning
import org.elektronnet.mobile.wallet.SendException
import org.elektronnet.mobile.wallet.SendFailure

@Composable
fun PsbtScreen(walletLabels: Map<String, String>, onBack: () -> Unit, initial: String = "") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(initial) }
    var other by remember { mutableStateOf("") }
    var view by remember { mutableStateOf<PsbtView?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var signWith by remember { mutableStateOf<String?>(null) }
    var txid by remember { mutableStateOf<String?>(null) }
    var confirmBroadcast by remember { mutableStateOf(false) }
    SecureWindow()

    fun run(block: () -> PsbtView?) {
        busy = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(block) }
            busy = false
            result.onSuccess { if (it != null) { view = it; text = it.psbt } }
            result.onFailure { error = psbtErrorText((it as? SendException)?.failure ?: SendFailure.Unknown) }
        }
    }

    fun inspect(psbt: String = text) = run {
        val tools = NodeHost.psbtTools() ?: throw SendException(SendFailure.NodeNotReady)
        tools.inspect(psbt)
    }

    ToolPage(stringResource(R.string.psbt_title), onBack) {
        Hint(stringResource(R.string.psbt_intro))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; view = null; txid = null },
            label = { Text(stringResource(R.string.psbt_field)) },
            minLines = 4,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { text = pasteText(context); view = null }) { Text(stringResource(R.string.send_paste)) }
            Button(onClick = { inspect() }, enabled = text.isNotBlank() && !busy) { Text(stringResource(R.string.psbt_check)) }
        }
        if (busy) CircularProgressIndicator()
        error?.let { ErrorText(stringResource(it)) }
        txid?.let {
            Text(stringResource(R.string.psbt_sent), style = MaterialTheme.typography.titleMedium)
            Text(it, fontFamily = FontFamily.Monospace)
            OutlinedButton(onClick = { copyText(context, "txid", it) }) { Text(stringResource(R.string.action_copy)) }
        }
        view?.let { v ->
            PsbtDetails(v, walletLabels)
            if (v.status == PsbtStatus.NeedsSignatures && v.signableBy.isNotEmpty()) {
                v.signableBy.forEach { wallet ->
                    OutlinedButton(onClick = { signWith = wallet }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.psbt_sign_with, walletLabels[wallet] ?: wallet))
                    }
                }
            }
            if (v.status != PsbtStatus.Final) {
                OutlinedTextField(
                    value = other,
                    onValueChange = { other = it },
                    label = { Text(stringResource(R.string.psbt_combine_field)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { other = pasteText(context) }) { Text(stringResource(R.string.send_paste)) }
                    OutlinedButton(
                        onClick = {
                            val second = other
                            other = ""
                            run {
                                val tools = NodeHost.psbtTools() ?: throw SendException(SendFailure.NodeNotReady)
                                tools.inspect(tools.combine(listOf(v.psbt, second)))
                            }
                        },
                        enabled = other.isNotBlank() && !busy,
                    ) { Text(stringResource(R.string.psbt_combine)) }
                }
            }
            if (v.status != PsbtStatus.NeedsSignatures) {
                Button(
                    onClick = { if (v.warnings.contains(PsbtWarning.HighFee)) confirmBroadcast = true else broadcast(scope, v.psbt, { busy = it }, { txid = it }, { error = it }) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                ) { Text(stringResource(R.string.psbt_broadcast)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { copyText(context, "psbt", v.psbt) }) { Text(stringResource(R.string.action_copy)) }
                OutlinedButton(onClick = { shareText(context, v.psbt) }) { Text(stringResource(R.string.action_share)) }
            }
        }
    }

    signWith?.let { wallet ->
        AlertDialog(
            onDismissRequest = { signWith = null; passphrase = "" },
            title = { Text(stringResource(R.string.psbt_sign_with, walletLabels[wallet] ?: wallet)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.send_confirm_body))
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text(stringResource(R.string.fr_pass_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = passphrase.isNotEmpty(),
                    onClick = {
                        val pass = passphrase
                        passphrase = ""
                        signWith = null
                        run {
                            val tools = NodeHost.psbtTools() ?: throw SendException(SendFailure.NodeNotReady)
                            tools.inspect(tools.sign(text, wallet, pass))
                        }
                    },
                ) { Text(stringResource(R.string.escrow_sign_action)) }
            },
            dismissButton = { TextButton(onClick = { signWith = null; passphrase = "" }) { Text(stringResource(R.string.action_back)) } },
        )
    }
    if (confirmBroadcast) {
        AlertDialog(
            onDismissRequest = { confirmBroadcast = false },
            text = { Text(stringResource(R.string.psbt_high_fee_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmBroadcast = false
                    broadcast(scope, view?.psbt.orEmpty(), { busy = it }, { txid = it }, { error = it })
                }) { Text(stringResource(R.string.psbt_broadcast)) }
            },
            dismissButton = { TextButton(onClick = { confirmBroadcast = false }) { Text(stringResource(R.string.action_back)) } },
        )
    }
}

private fun broadcast(
    scope: kotlinx.coroutines.CoroutineScope,
    psbt: String,
    busy: (Boolean) -> Unit,
    done: (String) -> Unit,
    failed: (Int) -> Unit,
) {
    busy(true)
    scope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching { NodeHost.psbtTools()?.finalizeAndBroadcast(psbt) ?: throw SendException(SendFailure.NodeNotReady) }
        }
        busy(false)
        result.onSuccess(done)
        result.onFailure { failed(psbtErrorText((it as? SendException)?.failure ?: SendFailure.Unknown)) }
    }
}

@Composable
private fun PsbtDetails(v: PsbtView, labels: Map<String, String>) {
    val statusText = when (v.status) {
        PsbtStatus.NeedsSignatures -> stringResource(R.string.psbt_status_needs, v.signaturesHave, v.signaturesNeed ?: 0)
        PsbtStatus.ReadyToFinalize -> stringResource(R.string.psbt_status_ready)
        PsbtStatus.Final -> stringResource(R.string.psbt_status_final)
    }
    Text(statusText, style = MaterialTheme.typography.titleMedium)
    v.warnings.forEach { w ->
        ErrorText(
            stringResource(
                when (w) {
                    PsbtWarning.HighFee -> R.string.psbt_warn_fee
                    PsbtWarning.NoOwnedInputs -> R.string.psbt_warn_not_ours
                    PsbtWarning.UnknownAmounts -> R.string.psbt_warn_unknown
                },
            ),
        )
    }
    v.netByWallet.forEach { (wallet, net) ->
        val sign = if (net > 0) "+" else if (net < 0) "-" else ""
        Text(stringResource(R.string.psbt_net, labels[wallet] ?: wallet, sign + Amount.format(Math.abs(net)), stringResource(R.string.balance_unit)))
    }
    Text(stringResource(R.string.psbt_outputs), style = MaterialTheme.typography.labelLarge)
    v.outputs.forEach { out ->
        val owner = out.owner?.let { labels[it] ?: it }
        Column {
            Text(out.address?.let(HistoryModel::shorten) ?: stringResource(R.string.psbt_unknown_output), fontFamily = FontFamily.Monospace)
            Text(
                Amount.format(out.amountLep) + " " + stringResource(R.string.balance_unit) + (owner?.let { "  " + stringResource(R.string.psbt_output_own, it) } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    v.feeLep?.let {
        Text(stringResource(R.string.tx_fee) + ": " + Amount.format(it) + " " + stringResource(R.string.balance_unit) + (v.feeRate?.let { r -> "  (" + stringResource(R.string.send_fee_rate, r.toPlainString()) + ")" } ?: ""))
    }
}

private fun psbtErrorText(failure: SendFailure): Int = when (failure) {
    SendFailure.InvalidPsbt -> R.string.psbt_err_invalid
    SendFailure.NothingToSign -> R.string.psbt_err_nothing
    SendFailure.WrongPassphrase -> R.string.send_err_passphrase
    SendFailure.NodeNotReady -> R.string.fr_err_node
    SendFailure.Rejected -> R.string.send_err_rejected
    else -> R.string.send_err_unknown
}
