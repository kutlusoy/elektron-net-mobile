package org.elektronnet.mobile.ui.wallet

import org.elektronnet.mobile.ui.SecretField
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.elektronnet.mobile.R
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.ui.model.SendInput
import org.elektronnet.mobile.ui.tools.Hint
import org.elektronnet.mobile.wallet.Amount
import org.elektronnet.mobile.wallet.CpfpException
import org.elektronnet.mobile.wallet.CpfpFailure
import org.elektronnet.mobile.wallet.CpfpPlan

@Composable
fun CpfpSection(txid: String, onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var rateText by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf<CpfpPlan?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<CpfpFailure?>(null) }
    var done by remember { mutableStateOf(false) }
    val rate = rateText.trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it <= BigDecimal(SendInput.MAX_CUSTOM_RATE) }
    LaunchedEffect(txid, open) {
        if (open && rateText.isEmpty()) {
            val suggested = withContext(Dispatchers.IO) { runCatching { NodeHost.cpfp()?.suggestedRate(txid) }.getOrNull() }
            if (suggested == null) failure = CpfpFailure.NotPending else rateText = suggested.toPlainString()
        }
    }
    LaunchedEffect(rate, open) {
        plan = null
        if (open && rate != null) {
            val result = withContext(Dispatchers.IO) { runCatching { NodeHost.cpfp()?.plan(txid, rate) } }
            plan = result.getOrNull()
            failure = (result.exceptionOrNull() as? CpfpException)?.failure
        }
    }
    if (done) {
        Text(stringResource(R.string.cpfp_done), color = MaterialTheme.colorScheme.primary)
        return
    }
    if (!open) {
        Button(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cpfp_action)) }
        return
    }
    Text(stringResource(R.string.cpfp_title), style = MaterialTheme.typography.titleMedium)
    Hint(stringResource(R.string.cpfp_hint))
    OutlinedTextField(
        value = rateText,
        onValueChange = { rateText = it },
        label = { Text(stringResource(R.string.cpfp_target_rate)) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    plan?.let {
        Text(stringResource(R.string.cpfp_current, it.packageRate.toPlainString()))
        Text(stringResource(R.string.cpfp_cost, Amount.display(it.childFeeLep), Amount.unitSymbol()))
    }
    SecretField(
        value = passphrase,
        onValueChange = { passphrase = it },
        label = { Text(stringResource(R.string.fr_pass_label)) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    failure?.let { Text(stringResource(cpfpError(it)), color = MaterialTheme.colorScheme.error) }
    Button(
        enabled = plan != null && passphrase.isNotEmpty() && !busy,
        onClick = {
            val pass = passphrase
            passphrase = ""
            failure = null
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { NodeHost.cpfp()?.accelerate(txid, rate!!, pass) ?: throw CpfpException(CpfpFailure.NodeNotReady) }
                }
                busy = false
                result.onSuccess { done = true; onDone(it) }
                result.onFailure { failure = (it as? CpfpException)?.failure ?: CpfpFailure.Unknown }
            }
        },
    ) { Text(stringResource(R.string.cpfp_confirm)) }
}

private fun cpfpError(failure: CpfpFailure): Int = when (failure) {
    CpfpFailure.NotPending -> R.string.cpfp_err_pending
    CpfpFailure.NoOutput -> R.string.cpfp_err_output
    CpfpFailure.TooSmall -> R.string.cpfp_err_small
    CpfpFailure.FeeTooLow -> R.string.bump_err_fee
    CpfpFailure.WrongPassphrase -> R.string.send_err_passphrase
    CpfpFailure.Mismatch -> R.string.send_err_mismatch
    CpfpFailure.NodeNotReady -> R.string.fr_err_node
    CpfpFailure.Rejected, CpfpFailure.Unknown -> R.string.bump_err_unknown
}
