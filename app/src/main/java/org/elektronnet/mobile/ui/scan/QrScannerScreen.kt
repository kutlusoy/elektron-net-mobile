package org.elektronnet.mobile.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import android.util.Size
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.elektronnet.mobile.R
import org.elektronnet.mobile.wallet.QrDecoder

@Composable
// continuous = true delivers every different code that is read, for animated codes that arrive in pieces
fun QrScannerScreen(
    onResult: (String) -> Unit,
    onClose: () -> Unit,
    continuous: Boolean = false,
    hint: Int = R.string.scan_hint,
    footer: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    BackHandler(onBack = onClose)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_back)) }
            Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.titleLarge)
        }
        if (granted) {
            Text(stringResource(hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            CameraPreview(onResult, continuous, Modifier.fillMaxWidth().weight(1f))
            footer()
        } else if (asked) {
            Text(stringResource(R.string.scan_denied))
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

@Composable
private fun CameraPreview(onResult: (String) -> Unit, continuous: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val decoder = remember { QrDecoder() }
    val delivered = remember { AtomicBoolean(false) }
    val last = remember { java.util.concurrent.atomic.AtomicReference<String?>(null) }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val cameraProvider = future.get()
                provider = cameraProvider
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                // the default analysis size (640x480) is too coarse for the dense codes of Tor and I2P addresses shown on another screen
                val resolution = ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { image: ImageProxy ->
                    try {
                        val plane = image.planes[0]
                        val buffer = plane.buffer
                        val data = ByteArray(buffer.remaining())
                        buffer.get(data)
                        val text = decoder.decode(data, image.width, image.height, plane.rowStride)
                        if (text != null) {
                            if (continuous) {
                                if (text != last.getAndSet(text)) mainExecutor.execute { onResult(text) }
                            } else if (delivered.compareAndSet(false, true)) {
                                mainExecutor.execute { onResult(text) }
                            }
                        }
                    } finally {
                        image.close()
                    }
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, mainExecutor)
            previewView
        },
    )
}
