package org.elektronnet.mobile.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.elektronnet.mobile.R
import org.elektronnet.mobile.runtime.Headline
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.ui.SecureWindow
import org.elektronnet.mobile.ui.scan.QrScannerScreen
import org.elektronnet.mobile.wallet.Amount
import org.elektronnet.mobile.wallet.SweepEngine
import org.elektronnet.mobile.wallet.SweepException
import org.elektronnet.mobile.wallet.SweepFailure
import org.elektronnet.mobile.wallet.SweepPlan
import org.elektronnet.mobile.wallet.SweepScan

private enum class Phase { Input, Scanning, Found, Review, Working, Done }

@Composable
fun SweepScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(Phase.Input) }
    var keysText by remember { mutableStateOf("") }
    var scan by remember { mutableStateOf<SweepScan?>(null) }
    var plan by remember { mutableStateOf<SweepPlan?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }
    var txid by remember { mutableStateOf<String?>(null) }
    val synced = NodeHost.latest?.headline is Headline.Synchronized
    var scanning by remember { mutableStateOf(false) }
    SecureWindow()

    fun fail(t: Throwable) {
        error = sweepErrorText((t as? SweepException)?.failure ?: SweepFailure.Unknown)
    }

    if (scanning) {
        QrScannerScreen(
            onResult = { keysText = (keysText.trim() + "\n" + it).trim(); scanning = false },
            onClose = { scanning = false },
        )
        return
    }

    ToolPage(stringResource(R.string.sweep_title), onBack) {
        Hint(stringResource(R.string.sweep_intro))
        error?.let { ErrorText(stringResource(it)) }
        if (!synced && phase == Phase.Input) ErrorText(stringResource(R.string.send_needs_sync))
        when (phase) {
            Phase.Input -> {
                OutlinedTextField(
                    value = keysText,
                    onValueChange = { keysText = it; error = null },
                    label = { Text(stringResource(R.string.sweep_keys_field)) },
                    minLines = 3,
                    visualTransformation = VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Hint(stringResource(R.string.sweep_collision))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { keysText = pasteText(context) }) { Text(stringResource(R.string.send_paste)) }
                    OutlinedButton(onClick = { scanning = true }) { Text(stringResource(R.string.scan_button)) }
                    Button(
                        enabled = keysText.isNotBlank() && synced,
                        onClick = {
                            error = null
                            phase = Phase.Scanning
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val engine = NodeHost.sweepEngine() ?: throw SweepException(SweepFailure.NodeNotReady)
                                        engine.scan(engine.parseKeys(keysText))
                                    }
                                }
                                result.onSuccess { scan = it; phase = Phase.Found }
                                result.onFailure { fail(it); phase = Phase.Input }
                            }
                        },
                    ) { Text(stringResource(R.string.sweep_scan)) }
                }
            }
            Phase.Scanning -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.sweep_scanning))
                OutlinedButton(onClick = {
                    scope.launch(Dispatchers.IO) { NodeHost.sweepEngine()?.abortScan() }
                }) { Text(stringResource(R.string.sweep_cancel)) }
            }
            Phase.Found -> {
                val s = scan
                if (s != null) {
                    Text(stringResource(R.string.sweep_found, Amount.format(s.totalLep), stringResource(R.string.balance_unit), s.utxos.size), style = MaterialTheme.typography.titleMedium)
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            error = null
                            phase = Phase.Working
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val engine = NodeHost.sweepEngine() ?: throw SweepException(SweepFailure.NodeNotReady)
                                        val fees = NodeHost.sendEngine()?.feeOptions() ?: throw SweepException(SweepFailure.NodeNotReady)
                                        val destination = NodeHost.receiveWallet()?.newAddress() ?: throw SweepException(SweepFailure.NodeNotReady)
                                        engine.plan(s, destination, fees[1].lepPerVb)
                                    }
                                }
                                result.onSuccess { plan = it; phase = Phase.Review }
                                result.onFailure { fail(it); phase = Phase.Found }
                            }
                        },
                    ) { Text(stringResource(R.string.send_review_action)) }
                }
            }
            Phase.Review -> {
                val p = plan
                if (p != null) {
                    Text(stringResource(R.string.send_review_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.sweep_to_wallet), fontFamily = FontFamily.Monospace)
                    Text(stringResource(R.string.send_review_amount) + ": " + Amount.format(p.amountLep) + " " + stringResource(R.string.balance_unit))
                    Text(stringResource(R.string.tx_fee) + ": " + Amount.format(p.feeLep) + " " + stringResource(R.string.balance_unit) + "  (" + stringResource(R.string.send_fee_rate, p.feeRate.toPlainString()) + ")")
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            error = null
                            phase = Phase.Working
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { (NodeHost.sweepEngine() ?: throw SweepException(SweepFailure.NodeNotReady)).broadcast(p) }
                                }
                                keysText = ""
                                result.onSuccess { txid = it; phase = Phase.Done }
                                result.onFailure { fail(it); phase = Phase.Review }
                            }
                        },
                    ) { Text(stringResource(R.string.sweep_action)) }
                }
            }
            Phase.Working -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.send_working))
            }
            Phase.Done -> {
                Text(stringResource(R.string.sweep_done), style = MaterialTheme.typography.titleMedium)
                txid?.let { Text(it, fontFamily = FontFamily.Monospace) }
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.send_done_close)) }
            }
        }
    }
}

private fun sweepErrorText(failure: SweepFailure): Int = when (failure) {
    SweepFailure.NoKeys -> R.string.sweep_err_nokeys
    SweepFailure.InvalidKey -> R.string.sweep_err_invalid
    SweepFailure.WrongNetwork -> R.string.sweep_err_network
    SweepFailure.TooManyKeys -> R.string.sweep_err_many
    SweepFailure.NothingFound -> R.string.sweep_err_nothing
    SweepFailure.TooSmall -> R.string.send_err_small
    SweepFailure.InvalidDestination -> R.string.send_recipient_invalid
    SweepFailure.ScanBusy -> R.string.sweep_err_busy
    SweepFailure.SignFailed -> R.string.send_err_unknown
    SweepFailure.Rejected -> R.string.send_err_rejected
    SweepFailure.NodeNotReady -> R.string.fr_err_node
    SweepFailure.Unknown -> R.string.send_err_unknown
}
