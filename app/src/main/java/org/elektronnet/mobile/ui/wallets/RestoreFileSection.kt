package org.elektronnet.mobile.ui.wallets

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.elektronnet.mobile.R
import org.elektronnet.mobile.service.ConsoleStore
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.ui.GlassCard
import org.elektronnet.mobile.ui.SecretField
import org.elektronnet.mobile.ui.tools.ErrorText
import org.elektronnet.mobile.ui.tools.Hint
import org.elektronnet.mobile.wallet.PassphraseCheck
import org.elektronnet.mobile.wallet.RestoreFailure
import org.elektronnet.mobile.wallet.RestoreResult

@Composable
fun RestoreFileSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<RestoreFailure?>(null) }
    var detail by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }
    var needsPassphrase by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        failure = null
        done = false
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val staged = File(File(ConsoleStore.exportsDir(context), "restore"), "incoming-${System.currentTimeMillis()}.dat")
                try {
                    staged.parentFile?.mkdirs()
                    context.contentResolver.openInputStream(uri)?.use { input -> staged.outputStream().use { input.copyTo(it) } }
                    NodeHost.restoreWalletFile(staged)
                } catch (e: java.io.IOException) {
                    RestoreResult.Failed(RestoreFailure.InvalidFile)
                } finally {
                    staged.delete()
                }
            }
            busy = false
            when (result) {
                is RestoreResult.Restored -> if (result.encrypted) done = true else needsPassphrase = result.walletName
                is RestoreResult.Failed -> {
                    failure = result.reason
                    detail = result.detail
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.restore_file_action))
        }
        Hint(stringResource(R.string.restore_file_hint))
        if (done) Hint(stringResource(R.string.restore_file_done))
        failure?.let {
            ErrorText(stringResource(restoreError(it)))
            if (detail.isNotBlank()) Hint(detail.take(160))
        }
    }
    needsPassphrase?.let { name ->
        SetPassphraseDialog(
            name = name,
            onDone = { needsPassphrase = null; done = true },
            onCancel = {
                needsPassphrase = null
                scope.launch(Dispatchers.IO) { NodeHost.discardRestoredWallet(name) }
            },
        )
    }
}

@Composable
private fun SetPassphraseDialog(name: String, onDone: () -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var failure by remember { mutableStateOf<RestoreFailure?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.restore_pass_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.restore_pass_body))
                SecretField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.fr_pass_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Hint(stringResource(R.string.fr_pass_short, PassphraseCheck.MIN_LENGTH))
                SecretField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text(stringResource(R.string.fr_pass_confirm)) },
                    isError = confirm.isNotEmpty() && confirm != value,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                failure?.let { ErrorText(stringResource(restoreError(it))) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && PassphraseCheck.acceptable(value) && value == confirm,
                onClick = {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { NodeHost.protectRestoredWallet(name, value) }
                        busy = false
                        if (result == null) onDone() else failure = result
                    }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onCancel) { Text(stringResource(R.string.restore_pass_cancel)) } },
    )
}

private fun restoreError(failure: RestoreFailure): Int = when (failure) {
    RestoreFailure.InvalidFile, RestoreFailure.NameTaken -> R.string.restore_file_err_file
    RestoreFailure.WeakPassphrase -> R.string.fr_pass_weak
    RestoreFailure.NodeNotReady -> R.string.fr_err_node
    RestoreFailure.Unknown -> R.string.fr_err_unknown
}

@Composable
fun RestoreNotice(onDismiss: () -> Unit) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.restore_file_done))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        }
    }
}
