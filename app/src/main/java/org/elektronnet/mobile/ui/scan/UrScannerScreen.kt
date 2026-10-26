package org.elektronnet.mobile.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.elektronnet.mobile.R
import org.elektronnet.mobile.ur.Ur
import org.elektronnet.mobile.ur.UrDecoder

// reads a plain QR code or an animated one (UR) and reports when everything has arrived
@Composable
fun UrScannerScreen(onPlain: (String) -> Unit, onUr: (Ur) -> Unit, onClose: () -> Unit) {
    val decoder = remember { UrDecoder() }
    var progress by remember { mutableStateOf(0.0) }
    var finished by remember { mutableStateOf(false) }
    QrScannerScreen(
        continuous = true,
        hint = R.string.scan_ur_hint,
        onClose = onClose,
        onResult = { text ->
            if (!finished) {
                if (text.trim().startsWith("ur:", ignoreCase = true)) {
                    decoder.receive(text)
                    progress = decoder.progress
                    decoder.result?.let {
                        finished = true
                        onUr(it)
                    }
                } else {
                    finished = true
                    onPlain(text)
                }
            }
        },
        footer = {
            if (progress > 0.0) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.scan_ur_progress, (progress * 100).toInt()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}
