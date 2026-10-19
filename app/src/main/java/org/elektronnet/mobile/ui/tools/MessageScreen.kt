package org.elektronnet.mobile.ui.tools

import org.elektronnet.mobile.ui.SecretField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import org.elektronnet.mobile.wallet.MessageException
import org.elektronnet.mobile.wallet.MessageFailure
import org.elektronnet.mobile.wallet.VerifyResult
import org.elektronnet.mobile.ui.theme.ElektronMono

@Composable
fun MessageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var verifyMode by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var signature by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var verdict by remember { mutableStateOf<VerifyResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    SecureWindow()

    LaunchedEffect(Unit) {
        if (address.isEmpty()) {
            address = withContext(Dispatchers.IO) { runCatching { NodeHost.receiveWallet()?.currentAddress() }.getOrNull().orEmpty() }
        }
    }

    ToolPage(stringResource(R.string.msg_title), onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (verifyMode) {
                OutlinedButton(onClick = { verifyMode = false; error = null; verdict = null }) { Text(stringResource(R.string.msg_sign_tab)) }
                Button(onClick = {}) { Text(stringResource(R.string.msg_verify_tab)) }
            } else {
                Button(onClick = {}) { Text(stringResource(R.string.msg_sign_tab)) }
                OutlinedButton(onClick = { verifyMode = true; error = null; signature = "" }) { Text(stringResource(R.string.msg_verify_tab)) }
            }
        }
        Hint(stringResource(if (verifyMode) R.string.msg_verify_intro else R.string.msg_sign_intro))
        OutlinedTextField(
            value = address,
            onValueChange = { address = it; error = null; verdict = null },
            label = { Text(stringResource(R.string.tx_address)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = message,
            onValueChange = { message = it; error = null; verdict = null },
            label = { Text(stringResource(R.string.msg_message)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        if (verifyMode) {
            OutlinedTextField(
                value = signature,
                onValueChange = { signature = it; error = null; verdict = null },
                label = { Text(stringResource(R.string.msg_signature)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = address.isNotBlank() && signature.isNotBlank() && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        verdict = withContext(Dispatchers.IO) { runCatching { NodeHost.messageTools()?.verify(address, signature, message) }.getOrNull() }
                        busy = false
                    }
                },
            ) { Text(stringResource(R.string.msg_verify_action)) }
            verdict?.let {
                val (text, bad) = when (it) {
                    VerifyResult.Valid -> R.string.msg_valid to false
                    VerifyResult.Invalid -> R.string.msg_invalid to true
                    VerifyResult.BadFormat -> R.string.msg_bad_format to true
                    VerifyResult.UnsupportedAddress -> R.string.msg_unsupported to true
                }
                if (bad) ErrorText(stringResource(text)) else Text(stringResource(text), style = MaterialTheme.typography.titleMedium)
            }
        } else {
            SecretField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text(stringResource(R.string.fr_pass_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { ErrorText(stringResource(it)) }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = address.isNotBlank() && message.isNotEmpty() && passphrase.isNotEmpty() && !busy,
                onClick = {
                    busy = true
                    error = null
                    val pass = passphrase
                    passphrase = ""
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { NodeHost.messageTools()?.sign(address, message, pass) ?: throw MessageException(MessageFailure.Unknown) }
                        }
                        busy = false
                        result.onSuccess { signature = it }
                        result.onFailure { error = messageErrorText((it as? MessageException)?.failure ?: MessageFailure.Unknown) }
                    }
                },
            ) { Text(stringResource(R.string.msg_sign_action)) }
            if (signature.isNotEmpty()) {
                Text(stringResource(R.string.msg_signature), style = MaterialTheme.typography.labelLarge)
                Text(signature, fontFamily = ElektronMono, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { copyText(context, "signature", signature) }) { Text(stringResource(R.string.action_copy)) }
                    TextButton(onClick = { shareText(context, signature) }) { Text(stringResource(R.string.action_share)) }
                }
            }
        }
    }
}

private fun messageErrorText(failure: MessageFailure): Int = when (failure) {
    MessageFailure.InvalidAddress -> R.string.send_recipient_invalid
    MessageFailure.NotOurAddress -> R.string.msg_err_not_ours
    MessageFailure.WrongPassphrase -> R.string.send_err_passphrase
    MessageFailure.NodeNotReady -> R.string.fr_err_node
    MessageFailure.Unsupported -> R.string.msg_unsupported
    MessageFailure.Unknown -> R.string.send_err_unknown
}
