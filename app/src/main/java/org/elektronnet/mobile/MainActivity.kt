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
import org.elektronnet.mobile.ui.ScreenshotMode
import org.elektronnet.mobile.ui.theme.Palettes
import org.elektronnet.mobile.ui.theme.UiState
import org.elektronnet.mobile.service.AppLanguage
import org.elektronnet.mobile.service.SettingsStore

class MainActivity : FragmentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        startNode()
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    private fun splashTheme(key: String?, dark: Boolean): Int =
        resources.getIdentifier("Theme_Elektron_Splash_${key ?: BuildConfig.DEFAULT_THEME}_${if (dark) "dark" else "light"}", "style", packageName)

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun rememberSplashTheme(style: Int) {
        splashScreen.setSplashScreenTheme(style)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val saved = SettingsStore(this).loadUi(Palettes.all.map { it.key }.toSet())
        splashTheme(saved.themeKey, saved.dark).takeIf { it != 0 }?.let { setTheme(it) }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = NodeHost.init(this)
        AppLockState.startOnce(settings.biometricLock && Biometric.available(this), settings.lockGraceMs)
        NodeHost.incomingUri = intent?.dataString
        setContent {
            val ui = UiState.settings
            val dark = ui.dark
            LaunchedEffect(ui.themeKey, dark) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    splashTheme(ui.themeKey, dark).takeIf { it != 0 }?.let(::rememberSplashTheme)
                }
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
                        onRestart = { NodeService.send(this, NodeService.ACTION_RESTART) },
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

    override fun onDestroy() {
        if (isFinishing) ScreenshotMode.allowed = false
        super.onDestroy()
    }

    override fun onStop() {
        AppLockState.onStopped()
        NodeHost.appVisible = false
        NodeHost.lockWallet()
        super.onStop()
    }

    private fun requestStart() {
        val needsPermission = (NodeHost.store?.walletReady() == true || NodeHost.store?.nodeOnly == true) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else startNode()
    }

    private fun startNode() = NodeService.start(this)
}
