package org.elektronnet.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.elektronnet.mobile.service.NodeHost
import org.elektronnet.mobile.service.NodeService
import org.elektronnet.mobile.ui.AppRoot
import org.elektronnet.mobile.ui.security.AppLockState
import org.elektronnet.mobile.ui.security.Biometric
import org.elektronnet.mobile.ui.security.LockScreen
import org.elektronnet.mobile.ui.theme.EffectLevel
import org.elektronnet.mobile.ui.theme.ElektronTheme
import org.elektronnet.mobile.ui.theme.UiState

class MainActivity : FragmentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        startNode()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = NodeHost.init(this)
        AppLockState.startOnce(settings.biometricLock && Biometric.available(this))
        NodeHost.incomingUri = intent?.dataString
        setContent {
            val ui = UiState.settings
            val dark = ui.dark
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            ElektronTheme(
                paletteKey = ui.themeKey ?: BuildConfig.DEFAULT_THEME,
                dark = dark,
                effects = EffectLevel.Off,
            ) {
                if (AppLockState.locked) {
                    LockScreen(onUnlock = ::unlock)
                } else {
                    AppRoot(
                        onBiometric = ::setBiometric,
                        onStart = ::requestStart,
                        onStop = { NodeService.send(this, NodeService.ACTION_STOP) },
                        onPause = { NodeService.send(this, NodeService.ACTION_PAUSE) },
                        onResume = { NodeService.send(this, NodeService.ACTION_RESUME) },
                    )
                }
            }
        }
        if (!settings.load().userStopped) requestStart()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        NodeHost.incomingUri = intent.dataString
    }

    override fun onStart() {
        super.onStart()
        NodeHost.appVisible = true
        AppLockState.onStarted()
    }

    private fun unlock() {
        Biometric.authenticate(
            this,
            title = getString(R.string.lock_prompt),
            negative = getString(R.string.lock_cancel),
            onSuccess = AppLockState::unlock,
        )
    }

    private fun setBiometric(enable: Boolean) {
        if (!enable) {
            NodeHost.store?.biometricLock = false
            AppLockState.applyEnabled(false)
            return
        }
        if (!Biometric.available(this)) {
            android.widget.Toast.makeText(this, R.string.lock_unavailable, android.widget.Toast.LENGTH_LONG).show()
            return
        }
        Biometric.authenticate(
            this,
            title = getString(R.string.lock_prompt),
            negative = getString(R.string.lock_cancel),
            onSuccess = {
                NodeHost.store?.biometricLock = true
                AppLockState.applyEnabled(true)
            },
        )
    }

    override fun onStop() {
        AppLockState.onStopped()
        NodeHost.appVisible = false
        NodeHost.lockWallet()
        super.onStop()
    }

    private fun requestStart() {
        val needsPermission = NodeHost.store?.walletReady() == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else startNode()
    }

    private fun startNode() = NodeService.start(this)
}
