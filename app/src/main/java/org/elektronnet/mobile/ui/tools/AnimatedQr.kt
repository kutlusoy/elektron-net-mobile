package org.elektronnet.mobile.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.elektronnet.mobile.R
import org.elektronnet.mobile.ui.receive.QrView
import org.elektronnet.mobile.ur.Ur
import org.elektronnet.mobile.ur.UrEncoder

private val INTERVALS_MS = longArrayOf(500, 330, 250, 170, 120)
private val FRAGMENT_BYTES = intArrayOf(50, 75, 100, 140)

// a payment file as one QR code when it is small, or as an animated code in the UR format that SeedSigner, Sparrow and Keystone read
@Composable
fun PsbtQr(psbt: String) {
    val tooBig = psbt.length > PSBT_QR_LIMIT
    var animated by remember(psbt) { mutableStateOf(tooBig) }
    val bytes = remember(psbt) { runCatching { java.util.Base64.getDecoder().decode(psbt.trim()) }.getOrNull() }
    if (bytes == null || bytes.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.psbt_qr_animated))
            Hint(stringResource(R.string.psbt_qr_animated_hint))
        }
        Switch(checked = animated, onCheckedChange = { animated = it })
    }
    if (animated) AnimatedQr(Ur.ofBytes(Ur.PSBT, bytes)) else if (!tooBig) QrView(psbt) else Hint(stringResource(R.string.scan_psbt_hint))
}

@Composable
fun AnimatedQr(ur: Ur) {
    var speed by remember { mutableIntStateOf(2) }
    var size by remember { mutableIntStateOf(2) }
    var paused by remember { mutableStateOf(false) }
    val encoder = remember(ur, size) { UrEncoder(ur, FRAGMENT_BYTES[size]) }
    var frame by remember(encoder) { mutableStateOf(encoder.nextPart().uppercase()) }
    LaunchedEffect(encoder, speed, paused) {
        while (!paused && !encoder.isSinglePart) {
            delay(INTERVALS_MS[speed])
            frame = encoder.nextPart().uppercase()
        }
    }
    QrView(frame)
    Hint(stringResource(R.string.psbt_qr_running))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { speed = (speed - 1).coerceAtLeast(0) }, enabled = speed > 0) { Text(stringResource(R.string.psbt_qr_slower)) }
        OutlinedButton(onClick = { speed = (speed + 1).coerceAtMost(INTERVALS_MS.size - 1) }, enabled = speed < INTERVALS_MS.size - 1) { Text(stringResource(R.string.psbt_qr_faster)) }
        OutlinedButton(onClick = { paused = !paused }) { Text(stringResource(if (paused) R.string.action_resume else R.string.action_pause)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { size = (size - 1).coerceAtLeast(0) }, enabled = size > 0) { Text(stringResource(R.string.psbt_qr_smaller)) }
        OutlinedButton(onClick = { size = (size + 1).coerceAtMost(FRAGMENT_BYTES.size - 1) }, enabled = size < FRAGMENT_BYTES.size - 1) { Text(stringResource(R.string.psbt_qr_bigger)) }
    }
}
