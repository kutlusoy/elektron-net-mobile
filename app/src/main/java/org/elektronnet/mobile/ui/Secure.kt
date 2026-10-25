package org.elektronnet.mobile.ui

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.LocalActivity

@Composable
// always keeps the protection on even when screenshots are allowed in Settings, for recovery words, passphrases, the lock and the console
fun SecureWindow(enabled: Boolean = true, always: Boolean = false) {
    val window = LocalActivity.current?.window
    val active = enabled && (always || !ScreenshotMode.allowed)
    DisposableEffect(active) {
        if (active) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            window?.decorView?.filterTouchesWhenObscured = true
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            window?.decorView?.filterTouchesWhenObscured = false
        }
    }
}
