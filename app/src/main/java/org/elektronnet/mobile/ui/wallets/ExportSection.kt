package org.elektronnet.mobile.ui.wallets

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.elektronnet.mobile.R
import org.elektronnet.mobile.service.ConsoleStore
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.ui.tools.ErrorText
import org.elektronnet.mobile.ui.tools.Hint
import org.elektronnet.mobile.ui.tools.shareText
import org.elektronnet.mobile.wallet.ExportException
import org.elektronnet.mobile.wallet.ExportFailure
import org.elektronnet.mobile.wallet.HistoryExport
import org.elektronnet.mobile.wallet.WalletExport

@Composable
fun ExportSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<File?>(null) }
    var failure by remember { mutableStateOf<ExportFailure?>(null) }
    var saved by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val file = pending
        pending = null
        scope.launch(Dispatchers.IO) {
            val ok = uri != null && file != null &&
                runCatching { context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } }.isSuccess
            file?.delete()
            saved = ok
        }
    }
    var csv by remember { mutableStateOf<String?>(null) }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        val text = csv
        csv = null
        if (uri != null && text != null) {
            scope.launch(Dispatchers.IO) {
                saved = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } }.isSuccess
            }
        }
    }
    Column {
        OutlinedButton(
            onClick = {
                failure = null
                saved = false
                scope.launch {
                    val items = withContext(Dispatchers.IO) { runCatching { NodeHost.walletReader()?.allTransactions() }.getOrNull() }
                    if (items == null) {
                        failure = ExportFailure.NodeNotReady
                    } else {
                        csv = HistoryExport.csv(items)
                        csvLauncher.launch("${NodeHost.activeWallet}-history.csv")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.details_export)) }
        Hint(stringResource(R.string.details_export_hint))
        OutlinedButton(
            onClick = {
                failure = null
                scope.launch {
                    val text = withContext(Dispatchers.IO) { runCatching { NodeHost.walletExport()?.descriptorText() }.getOrNull() }
                    if (text == null) failure = ExportFailure.NodeNotReady else shareText(context, text)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.export_descriptors)) }
        Hint(stringResource(R.string.export_descriptors_hint))
        OutlinedButton(
            onClick = {
                failure = null
                saved = false
                scope.launch {
                    val name = WalletExport.backupName(NodeHost.activeWallet, System.currentTimeMillis())
                    val target = File(File(ConsoleStore.exportsDir(context), "wallets"), name)
                    val result = withContext(Dispatchers.IO) { runCatching { NodeHost.walletExport()?.backup(target) ?: throw ExportException(ExportFailure.NodeNotReady) } }
                    result.onSuccess { pending = it; launcher.launch(name) }
                    result.onFailure { failure = (it as? ExportException)?.failure ?: ExportFailure.Unknown }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.export_file)) }
        Hint(stringResource(R.string.export_file_hint))
        if (saved) Hint(stringResource(R.string.export_saved))
        failure?.let { ErrorText(stringResource(exportError(it))) }
    }
}

private fun exportError(failure: ExportFailure): Int = when (failure) {
    ExportFailure.NotEncrypted -> R.string.export_err_encrypted
    ExportFailure.TargetExists, ExportFailure.Unknown -> R.string.export_err_unknown
    ExportFailure.NodeNotReady -> R.string.fr_err_node
}
