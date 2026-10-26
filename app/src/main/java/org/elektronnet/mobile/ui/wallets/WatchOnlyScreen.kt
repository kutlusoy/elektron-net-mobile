package org.elektronnet.mobile.ui.wallets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.elektronnet.mobile.R
import org.elektronnet.mobile.ChainConfig
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.ui.scan.UrScannerScreen
import org.elektronnet.mobile.ui.tools.ErrorText
import org.elektronnet.mobile.ui.tools.Hint
import org.elektronnet.mobile.ui.tools.ToolPage
import org.elektronnet.mobile.ui.tools.pasteText
import org.elektronnet.mobile.wallet.SetupRequest
import org.elektronnet.mobile.wallet.UrKeys
import org.elektronnet.mobile.wallet.WatchOnlyInput
import org.elektronnet.mobile.wallet.WatchParse
import org.elektronnet.mobile.wallet.WatchProblem
import org.elektronnet.mobile.wallet.WatchSource

// a wallet without keys, made from what a signing device (for example a SeedSigner) shows as its public key, or from one address
@Composable
fun WatchOnlyScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var urProblem by remember { mutableStateOf(false) }
    val input = remember { WatchOnlyInput(ChainConfig.isTestnet) }
    val walletName = remember { NodeHost.nextWalletName() }
    val parsed = remember(text) { if (text.isBlank()) null else input.parse(text) }
    BackHandler(onBack = { if (scanning) scanning = false else onBack() })

    if (scanning) {
        UrScannerScreen(
            onPlain = { text = it; urProblem = false; scanning = false },
            onUr = { ur ->
                val found = UrKeys.descriptors(ur, ChainConfig.isTestnet)
                urProblem = found == null
                text = found?.joinToString("\n").orEmpty()
                scanning = false
            },
            onClose = { scanning = false },
        )
        return
    }

    ToolPage(stringResource(R.string.watch_title), onBack) {
        Hint(stringResource(R.string.watch_intro))
        OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(32) },
            label = { Text(stringResource(R.string.wallets_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; urProblem = false },
            label = { Text(stringResource(R.string.watch_field)) },
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { text = pasteText(context); urProblem = false }) { Text(stringResource(R.string.send_paste)) }
            OutlinedButton(onClick = { scanning = true }) { Text(stringResource(R.string.scan_button)) }
        }
        if (urProblem) ErrorText(stringResource(R.string.watch_err_ur))
        when (val result = parsed) {
            is WatchParse.Ok -> Text(
                if (result.source is WatchSource.Address) stringResource(R.string.watch_found_address) else stringResource(R.string.watch_found, result.summary),
                color = MaterialTheme.colorScheme.primary,
            )
            is WatchParse.Invalid -> ErrorText(
                stringResource(
                    when (result.problem) {
                        WatchProblem.PrivateKey -> R.string.watch_err_private
                        WatchProblem.WrongNetwork -> R.string.watch_err_network
                        WatchProblem.Unsupported -> R.string.watch_err_unsupported
                        else -> R.string.watch_err_unrecognized
                    },
                ),
            )
            null -> Unit
        }
        Button(
            enabled = parsed is WatchParse.Ok,
            onClick = {
                val ok = parsed as? WatchParse.Ok ?: return@Button
                NodeHost.beginSetup(SetupRequest(emptyList(), "", true, walletName, ok.source), label)
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.watch_add)) }
        Hint(stringResource(R.string.watch_note))
    }
}
