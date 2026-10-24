package org.elektronnet.mobile.ui.send

import org.elektronnet.mobile.ui.SecretField
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.elektronnet.mobile.ChainConfig
import org.elektronnet.mobile.R
import org.elektronnet.mobile.runtime.Headline
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.ui.tools.Hint
import org.elektronnet.mobile.ui.SecureWindow
import org.elektronnet.mobile.ui.scan.QrScannerScreen
import org.elektronnet.mobile.ui.model.AmountState
import org.elektronnet.mobile.ui.model.FeeInput
import org.elektronnet.mobile.ui.model.RecipientState
import org.elektronnet.mobile.ui.model.SendInput
import org.elektronnet.mobile.wallet.AddressFormat
import org.elektronnet.mobile.wallet.Amount
import org.elektronnet.mobile.wallet.AmountUnit
import org.elektronnet.mobile.fiat.FiatRates
import org.elektronnet.mobile.wallet.EscrowRecord
import org.elektronnet.mobile.wallet.FeeLevel
import org.elektronnet.mobile.wallet.FeeOption
import org.elektronnet.mobile.wallet.PreparedSend
import org.elektronnet.mobile.wallet.SendException
import org.elektronnet.mobile.wallet.SendFailure
import org.elektronnet.mobile.wallet.SendRequest
import org.elektronnet.mobile.ui.theme.ElektronMono

private enum class Step { Recipient, Amount, Fee, Review, Confirm, Working, Done }

@Composable
fun SendScreen(initialUri: String?, onClose: () -> Unit, onDone: (String?) -> Unit, escrow: EscrowRecord? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.Recipient) }
    var recipientText by remember { mutableStateOf(initialUri.orEmpty()) }
    var legacyOk by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var max by remember { mutableStateOf(false) }
    var available by remember { mutableStateOf<Long?>(null) }
    var options by remember { mutableStateOf<List<FeeOption>>(emptyList()) }
    var floor by remember { mutableStateOf(BigDecimal.ONE) }
    var level by remember { mutableStateOf(FeeLevel.Normal) }
    var custom by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    var prepared by remember { mutableStateOf<PreparedSend?>(null) }
    var passphrase by remember { mutableStateOf("") }
    var failure by remember { mutableStateOf<SendFailure?>(null) }
    var txid by remember { mutableStateOf<String?>(null) }
    var noteFromLink by remember { mutableStateOf<String?>(null) }
    var exportedPsbt by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var reused by remember { mutableStateOf(0L) }
    var selectedCoins by remember { mutableStateOf<List<org.elektronnet.mobile.wallet.Coin>>(emptyList()) }
    var pickCoins by remember { mutableStateOf(false) }
    var subtractFee by remember { mutableStateOf(false) }
    val walletName = escrow?.walletName ?: NodeHost.activeWallet

    val resolved = SendInput.recipient(recipientText, NodeHost.paymentUri, org.elektronnet.mobile.wallet.AddressCheck(ChainConfig.isTestnet), ChainConfig.uriScheme)
    val spendable = if (selectedCoins.isNotEmpty()) selectedCoins.sumOf { it.amountLep } else available ?: 0L
    val fiat = org.elektronnet.mobile.ui.theme.FiatState.current
    var inFiat by remember { mutableStateOf(false) }
    val fiatInput = if (inFiat) fiat else null
    LaunchedEffect(fiat == null) {
        if (fiat == null && inFiat) { amountText = ""; inFiat = false }
    }
    val amountState = SendInput.amount(amountText, spendable, fiatInput)
    val customFee = SendInput.customFee(customText, floor)

    SecureWindow(step == Step.Review || step == Step.Confirm || step == Step.Working)
    BackHandler {
        when (step) {
            Step.Recipient, Step.Done -> if (step == Step.Done) onDone(txid) else onClose()
            Step.Amount -> step = Step.Recipient
            Step.Fee -> step = Step.Amount
            Step.Review -> step = Step.Fee
            Step.Confirm -> { passphrase = ""; step = Step.Review }
            Step.Working -> Unit
        }
    }

    LaunchedEffect(Unit) {
        available = withContext(Dispatchers.IO) { runCatching { NodeHost.sendEngine(walletName)?.spendableLep() }.getOrNull() }
        reused = withContext(Dispatchers.IO) { runCatching { NodeHost.sendEngine(walletName)?.reusedLep() }.getOrNull() } ?: 0L
    }
    LaunchedEffect(recipientText, resolved.address) {
        val isLink = recipientText.trim().startsWith(ChainConfig.uriScheme + ":", ignoreCase = true)
        if (isLink && resolved.usable && step == Step.Recipient) {
            resolved.amountLep?.let { amountText = Amount.plain(it); max = false; inFiat = false }
            noteFromLink = resolved.note
        }
    }

    fun request(): SendRequest {
        val rate = if (custom) (customFee as? FeeInput.Ok)?.rate ?: BigDecimal.ONE else options.first { it.level == level }.lepPerVb
        val lep = (amountState as? AmountState.Ok)?.lep
        return SendRequest(resolved.address.orEmpty(), if (max) null else lep, max, rate, allowLegacy = legacyOk, coins = selectedCoins, subtractFee = subtractFee && !max)
    }

    fun prepare() {
        failure = null
        step = Step.Working
        val req = request()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { NodeHost.sendEngine(walletName)?.prepare(req) ?: throw SendException(SendFailure.NodeNotReady) }
            }
            result.onSuccess { prepared = it; step = Step.Review }
            result.onFailure { failure = (it as? SendException)?.failure ?: SendFailure.Unknown; step = Step.Fee }
        }
    }

    fun send() {
        val ready = prepared ?: return
        val pass = passphrase
        failure = null
        step = Step.Working
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val engine = NodeHost.sendEngine(walletName) ?: throw SendException(SendFailure.NodeNotReady)
                    if (escrow != null) engine.sign(ready, pass).psbt else engine.send(ready, pass)
                }
            }
            passphrase = ""
            result.onSuccess {
                if (escrow != null) exportedPsbt = it else txid = it
                step = Step.Done
            }
            result.onFailure {
                val f = (it as? SendException)?.failure ?: SendFailure.Unknown
                failure = f
                step = if (f == SendFailure.WrongPassphrase) Step.Confirm else Step.Review
            }
        }
    }

    if (picking) {
        org.elektronnet.mobile.ui.wallets.ContactPickerDialog(
            onPick = { recipientText = it.address; legacyOk = false; picking = false },
            onDismiss = { picking = false },
        )
    }
    if (scanning) {
        QrScannerScreen(
            onResult = { recipientText = it; legacyOk = false; scanning = false },
            onClose = { scanning = false },
        )
        return
    }
    if (NodeHost.latest?.headline !is Headline.Synchronized && step != Step.Working && step != Step.Done) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.send_needs_sync))
            Button(onClick = onClose) { Text(stringResource(R.string.action_back)) }
        }
        return
    }
    if (pickCoins) {
        org.elektronnet.mobile.ui.wallets.CoinPickerDialog(
            wallet = walletName,
            selected = selectedCoins,
            onDone = { selectedCoins = it; pickCoins = false },
            onDismiss = { pickCoins = false },
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step != Step.Working && step != Step.Done) {
                TextButton(onClick = { if (step == Step.Recipient) onClose() else { passphrase = ""; step = Step.entries[step.ordinal - 1] } }) {
                    Text(stringResource(R.string.action_back))
                }
            }
            Text(if (escrow != null) escrow.name else stringResource(R.string.send_title), style = MaterialTheme.typography.titleLarge)
        }
        if (escrow != null && step != Step.Done) Hint(stringResource(R.string.escrow_send_from))
        failure?.let { Text(stringResource(failureText(it)), color = MaterialTheme.colorScheme.error) }
        when (step) {
            Step.Recipient -> {
                Text(stringResource(R.string.send_recipient_title), style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = recipientText,
                    onValueChange = { recipientText = it; legacyOk = false },
                    label = { Text(stringResource(R.string.send_recipient_hint)) },
                    minLines = 2,
                    isError = resolved.state is RecipientState.Foreign || resolved.state == RecipientState.Invalid,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { recipientText = clipboardText(context) }) { Text(stringResource(R.string.send_paste)) }
                    OutlinedButton(onClick = { scanning = true }) { Text(stringResource(R.string.scan_button)) }
                    OutlinedButton(onClick = { picking = true }) { Text(stringResource(R.string.contacts_title)) }
                }
                RecipientMessage(resolved.state, resolved.address, legacyOk) { legacyOk = it }
                Button(
                    onClick = { step = Step.Amount },
                    enabled = resolved.usable && (resolved.state != RecipientState.Legacy || legacyOk),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.fr_continue)) }
            }
            Step.Amount -> {
                Text(stringResource(R.string.send_amount_title), style = MaterialTheme.typography.headlineSmall)
                if (available != null) {
                    Text(stringResource(R.string.send_available, Amount.display(spendable), Amount.unitSymbol()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (reused > 0 && selectedCoins.isEmpty() && NodeHost.store?.spendReused == false) {
                        Text(stringResource(R.string.send_reused_hint, Amount.display(reused), Amount.unitSymbol()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickCoins = true }) { Text(stringResource(R.string.coinpick_action)) }
                    if (selectedCoins.isNotEmpty()) {
                        Text(stringResource(R.string.coinpick_selected, selectedCoins.size), color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = { selectedCoins = emptyList() }) { Text(stringResource(R.string.coinpick_clear)) }
                    }
                }
                if (fiat != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.send_enter_in), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        listOf(false to Amount.unitSymbol(), true to fiat.quote).forEach { (fiatMode, name) ->
                            val choose = {
                                if (inFiat != fiatMode) {
                                    val lep = (amountState as? AmountState.Ok)?.lep
                                    amountText = when {
                                        lep == null -> ""
                                        fiatMode -> FiatRates.inputText(lep, fiat)
                                        else -> Amount.plain(lep)
                                    }
                                    inFiat = fiatMode
                                }
                            }
                            if (inFiat == fiatMode) Button(onClick = choose) { Text(name) } else OutlinedButton(onClick = choose) { Text(name) }
                        }
                    }
                }
                OutlinedTextField(
                    value = if (max) (if (fiatInput != null) FiatRates.inputText(spendable, fiatInput) else Amount.plain(spendable)) else amountText,
                    onValueChange = { amountText = it; max = false },
                    label = { Text(stringResource(R.string.receive_amount, fiatInput?.quote ?: Amount.unitSymbol())) },
                    enabled = !max,
                    singleLine = true,
                    isError = !max && (amountState == AmountState.Invalid || amountState is AmountState.Over),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = max, onCheckedChange = { max = it })
                    Text(stringResource(R.string.send_max), modifier = Modifier.padding(start = 12.dp))
                }
                if (!max) {
                    when (val a = amountState) {
                        AmountState.Invalid -> Text(stringResource(R.string.receive_amount_invalid), color = MaterialTheme.colorScheme.error)
                        is AmountState.Over -> Text(stringResource(R.string.send_over_balance, Amount.display(a.availableLep), Amount.unitSymbol()), color = MaterialTheme.colorScheme.error)
                        else -> Unit
                    }
                }
                (amountState as? AmountState.Ok)?.takeIf { !max && !inFiat && Amount.unit != AmountUnit.Elek }?.let {
                    Text("= " + Amount.formatIn(AmountUnit.Elek, it.lep) + " " + AmountUnit.Elek.symbol, color = MaterialTheme.colorScheme.primary)
                }
                (if (max) spendable else (amountState as? AmountState.Ok)?.lep)?.let { lep ->
                    if (inFiat) {
                        Text("\u2248 " + Amount.displayWithUnit(lep), color = MaterialTheme.colorScheme.primary)
                    } else {
                        org.elektronnet.mobile.ui.theme.FiatState.text(lep)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                fiat?.let { Text(stringResource(R.string.fiat_status_ok, FiatRates.format(Amount.LEP_PER_ELEK, it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                noteFromLink?.let { Text(stringResource(R.string.send_link_note, it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Button(
                    onClick = {
                        step = Step.Fee
                    },
                    enabled = max && spendable > 0 || amountState is AmountState.Ok,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.fr_continue)) }
            }
            Step.Fee -> {
                LaunchedEffect(Unit) {
                    if (options.isEmpty()) {
                        val loaded = withContext(Dispatchers.IO) {
                            runCatching { NodeHost.sendEngine(walletName)?.let { it.feeOptions() to it.feeFloor() } }.getOrNull()
                        }
                        loaded?.let { options = it.first; floor = it.second }
                    }
                }
                Text(stringResource(R.string.send_fee_title), style = MaterialTheme.typography.headlineSmall)
                if (options.isEmpty()) {
                    CircularProgressIndicator()
                } else {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = !custom && level == option.level, onClick = { custom = false; level = option.level }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = !custom && level == option.level, onClick = { custom = false; level = option.level })
                            Column {
                                Text(stringResource(feeLabel(option.level)))
                                Text(
                                    stringResource(R.string.send_fee_rate, option.lepPerVb.toPlainString()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (!max) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = subtractFee, onCheckedChange = { subtractFee = it })
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(stringResource(R.string.send_fee_subtract))
                                Text(stringResource(R.string.send_fee_subtract_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (options.none { it.estimated }) Text(stringResource(R.string.send_fee_no_estimate), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (options.any { it.fromExplorer }) Text(stringResource(R.string.send_fee_from_explorer), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = custom, onCheckedChange = { custom = it })
                        Text(stringResource(R.string.send_fee_custom), modifier = Modifier.padding(start = 12.dp))
                    }
                    if (custom) {
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { customText = it },
                            label = { Text(stringResource(R.string.send_fee_custom_label)) },
                            isError = customText.isNotEmpty() && customFee == FeeInput.Invalid,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (customText.isNotEmpty() && customFee == FeeInput.Invalid) {
                            Text(stringResource(R.string.send_fee_custom_invalid, floor.toPlainString(), SendInput.MAX_CUSTOM_RATE), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Button(
                        onClick = ::prepare,
                        enabled = !custom || customFee is FeeInput.Ok,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.send_review_action)) }
                }
            }
            Step.Review -> {
                val review = prepared?.review
                if (review != null) {
                    Text(stringResource(R.string.send_review_title), style = MaterialTheme.typography.headlineSmall)
                    ReviewLine(stringResource(R.string.send_review_to)) {
                        Text(
                            buildAnnotatedString {
                                AddressFormat.runs(review.address).forEach { run ->
                                    val style = if (run.emphasized) SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) else SpanStyle()
                                    withStyle(style) { append(run.text) }
                                }
                            },
                            fontFamily = ElektronMono,
                        )
                    }
                    ReviewLine(stringResource(if (prepared?.request?.subtracts == true) R.string.send_review_gets else R.string.send_review_amount)) { Text(elek(review.amountLep), style = MaterialTheme.typography.titleLarge); FiatLine(review.amountLep) }
                    ReviewLine(stringResource(R.string.tx_fee)) {
                        Text(elek(review.feeLep) + "  (" + stringResource(R.string.send_fee_rate, review.feeRate.toPlainString()) + ")")
                        FiatLine(review.feeLep)
                    }
                    ReviewLine(stringResource(R.string.send_review_total)) { Text(elek(review.totalLep), style = MaterialTheme.typography.titleLarge); FiatLine(review.totalLep) }
                    Button(onClick = { step = Step.Confirm }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (escrow != null) R.string.escrow_sign_action else R.string.action_send)) }
                }
            }
            Step.Confirm -> {
                Text(stringResource(R.string.send_confirm_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.send_confirm_body))
                prepared?.review?.let { Text(elek(it.totalLep), style = MaterialTheme.typography.titleLarge); FiatLine(it.totalLep) }
                SecretField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.fr_pass_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = ::send, enabled = passphrase.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (escrow != null) R.string.escrow_sign_action else R.string.action_send))
                }
            }
            Step.Working -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.send_working))
            }
            Step.Done -> {
                val psbt = exportedPsbt
                if (psbt != null) {
                    Text(stringResource(R.string.escrow_proposal_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.escrow_proposal_body, escrow?.required ?: 2))
                    Text(psbt, fontFamily = ElektronMono, style = MaterialTheme.typography.bodySmall, maxLines = 8)
                    if (psbt.length <= org.elektronnet.mobile.ui.tools.PSBT_QR_LIMIT) org.elektronnet.mobile.ui.receive.QrView(psbt)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { copyPlain(context, psbt) }) { Text(stringResource(R.string.action_copy)) }
                        Button(onClick = { sharePlain(context, psbt) }) { Text(stringResource(R.string.action_share)) }
                    }
                    OutlinedButton(onClick = { onDone(null) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.send_done_close)) }
                } else {
                    Text(stringResource(R.string.send_done_title), style = MaterialTheme.typography.headlineSmall)
                    prepared?.review?.let { Text(elek(it.amountLep)); FiatLine(it.amountLep) }
                    Button(onClick = { onDone(txid) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.send_view_tx)) }
                    OutlinedButton(onClick = { onDone(null) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.send_done_close)) }
                }
            }
        }
    }
}

@Composable
private fun elek(lep: Long): String =
    Amount.displayWithUnit(lep) + if (Amount.unit != AmountUnit.Elek) " (= " + Amount.formatIn(AmountUnit.Elek, lep) + " " + AmountUnit.Elek.symbol + ")" else ""

@Composable
private fun FiatLine(lep: Long) {
    org.elektronnet.mobile.ui.theme.FiatState.text(lep)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun ReviewLine(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun RecipientMessage(state: RecipientState, address: String?, legacyOk: Boolean, onLegacy: (Boolean) -> Unit) {
    when (state) {
        RecipientState.Empty -> Unit
        RecipientState.Valid -> Text(stringResource(R.string.send_recipient_ok, address?.let { it.take(8) + "…" + it.takeLast(6) }.orEmpty()))
        RecipientState.Legacy -> {
            Text(stringResource(R.string.send_recipient_legacy), color = MaterialTheme.colorScheme.error)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = legacyOk, onCheckedChange = onLegacy)
                Text(stringResource(R.string.send_recipient_legacy_use), modifier = Modifier.padding(start = 12.dp))
            }
        }
        is RecipientState.Foreign -> Text(stringResource(R.string.send_recipient_foreign, state.hrp), color = MaterialTheme.colorScheme.error)
        RecipientState.Invalid -> Text(stringResource(R.string.send_recipient_invalid), color = MaterialTheme.colorScheme.error)
    }
}

private fun feeLabel(level: FeeLevel): Int = when (level) {
    FeeLevel.Slow -> R.string.send_fee_slow
    FeeLevel.Normal -> R.string.send_fee_normal
    FeeLevel.Fast -> R.string.send_fee_fast
}

private fun failureText(failure: SendFailure): Int = when (failure) {
    SendFailure.InvalidAddress -> R.string.send_recipient_invalid
    SendFailure.ForeignNetwork -> R.string.send_err_foreign
    SendFailure.LegacyNotConfirmed -> R.string.send_recipient_legacy
    SendFailure.InvalidAmount -> R.string.receive_amount_invalid
    SendFailure.AmountTooSmall -> R.string.send_err_small
    SendFailure.InsufficientFunds -> R.string.send_err_funds
    SendFailure.FeeTooLow -> R.string.send_err_fee_low
    SendFailure.PolicyChanged -> R.string.send_err_policy
    SendFailure.PsbtMismatch -> R.string.send_err_mismatch
    SendFailure.WrongPassphrase -> R.string.send_err_passphrase
    SendFailure.NodeNotReady -> R.string.fr_err_node
    SendFailure.Rejected -> R.string.send_err_rejected
    SendFailure.InvalidPsbt, SendFailure.NothingToSign, SendFailure.Unknown -> R.string.send_err_unknown
}

private fun copyPlain(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("psbt", text))
}

private fun sharePlain(context: Context, text: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
    context.startActivity(android.content.Intent.createChooser(intent, null))
}

private fun clipboardText(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
}
