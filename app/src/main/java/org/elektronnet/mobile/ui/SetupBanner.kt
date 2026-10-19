package org.elektronnet.mobile.ui

import org.elektronnet.mobile.ui.SecretField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.elektronnet.mobile.R
import org.elektronnet.mobile.wallet.BootstrapFailure
import org.elektronnet.mobile.wallet.SetupStatus

@Composable
fun SetupBanner(
    status: SetupStatus,
    needsPassphrase: Boolean,
    onRetry: () -> Unit,
    onStartOver: () -> Unit,
    onPassphrase: (String) -> Unit,
) {
    var asking by remember { mutableStateOf(false) }
    val text = when (status) {
        SetupStatus.None -> return
        SetupStatus.Waiting -> R.string.setup_waiting
        SetupStatus.Rescanning -> R.string.setup_rescanning
        SetupStatus.NeedsRescan -> R.string.setup_needs_sync
        is SetupStatus.Ready -> if (status.partialRestore) R.string.setup_partial else return
        is SetupStatus.Failed -> failureText(status.failure)
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val failed = status is SetupStatus.Failed
        Text(stringResource(text), color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        if (status.inProgress) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (failed) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                TextButton(onClick = onStartOver) { Text(stringResource(R.string.setup_start_over)) }
            }
        }
        if (status == SetupStatus.NeedsRescan && needsPassphrase) {
            OutlinedButton(onClick = { asking = true }) { Text(stringResource(R.string.setup_enter_passphrase)) }
        }
    }
    if (asking) {
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.setup_pass_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.setup_pass_body))
                    SecretField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text(stringResource(R.string.fr_pass_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = value.isNotEmpty(), onClick = {
                    onPassphrase(value)
                    value = ""
                    asking = false
                }) { Text(stringResource(R.string.fr_continue)) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.action_back)) } },
        )
    }
}

private fun failureText(failure: BootstrapFailure): Int = when (failure) {
    BootstrapFailure.WalletExists -> R.string.fr_err_exists
    BootstrapFailure.InvalidPhrase -> R.string.fr_restore_invalid
    BootstrapFailure.WeakPassphrase -> R.string.fr_pass_weak
    BootstrapFailure.WrongPassphrase -> R.string.send_err_passphrase
    BootstrapFailure.NodeNotReady -> R.string.fr_err_node
    BootstrapFailure.RecoveryUnsupported -> R.string.fr_err_recovery
    BootstrapFailure.ImportFailed, BootstrapFailure.Unknown -> R.string.fr_err_unknown
}
