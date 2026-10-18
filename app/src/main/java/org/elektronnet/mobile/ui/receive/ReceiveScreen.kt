package org.elektronnet.mobile.ui.receive

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.elektronnet.mobile.R
import org.elektronnet.mobile.runtime.Headline
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.ui.model.ReceiveUiModel
import org.elektronnet.mobile.wallet.QrCode
import org.elektronnet.mobile.ui.theme.ElektronMono

@Composable
fun ReceiveScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var address by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(false) }
    val synced = NodeHost.latest?.headline is Headline.Synchronized

    BackHandler(onBack = onClose)
    LaunchedEffect(attempt) {
        failed = false
        val result = withContext(Dispatchers.IO) { runCatching { NodeHost.receiveWallet()?.currentAddress() }.getOrNull() }
        if (result == null) failed = true else address = result
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_back)) }
            Text(stringResource(R.string.receive_title), style = MaterialTheme.typography.titleLarge)
        }
        val current = address
        if (current == null) {
            Text(stringResource(if (failed) R.string.receive_unavailable else R.string.receive_loading))
            if (failed) Button(onClick = { attempt++ }) { Text(stringResource(R.string.action_retry)) }
            return@Column
        }
        val model = ReceiveUiModel.build(current, NodeHost.paymentUri, amount, note, synced)
        QrView(model.qrText)
        Text(
            buildAnnotatedString {
                model.runs.forEach { run ->
                    val style = if (run.emphasized) SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) else SpanStyle()
                    withStyle(style) { append(run.text) }
                }
            },
            fontFamily = ElektronMono,
            style = MaterialTheme.typography.titleMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { copyAddress(context, model.address) }) { Text(stringResource(R.string.action_copy)) }
            Button(onClick = { share(context, model.uri) }) { Text(stringResource(R.string.action_share)) }
        }
        if (model.showSyncNote) Text(stringResource(R.string.receive_sync_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { options = !options }) {
            Text(stringResource(if (options) R.string.receive_options_hide else R.string.receive_options_show))
        }
        if (options) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text(stringResource(R.string.receive_amount, stringResource(R.string.balance_unit))) },
                isError = model.amountInvalid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (model.amountInvalid) Text(stringResource(R.string.receive_amount_invalid), color = MaterialTheme.colorScheme.error)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(ReceiveUiModel.NOTE_LIMIT) },
                label = { Text(stringResource(R.string.receive_note)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun QrView(text: String) {
    val matrix = remember(text) { runCatching { QrCode.matrix(text) }.getOrNull() } ?: return
    Box(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color.White)
            .padding(16.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val cell = size.width / matrix.size
            matrix.forEachIndexed { y, row ->
                row.forEachIndexed { x, dark ->
                    if (dark) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}

private fun copyAddress(context: Context, address: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("address", address))
    Handler(Looper.getMainLooper()).postDelayed({
        val current = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        if (current == address) clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
    }, CLIPBOARD_CLEAR_MS)
}

private fun share(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, null))
}

private const val CLIPBOARD_CLEAR_MS = 60_000L
