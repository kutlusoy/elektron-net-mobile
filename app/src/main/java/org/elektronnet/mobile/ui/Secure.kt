package org.elektronnet.mobile.ui

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.LocalActivity

@Composable
fun SecureWindow(enabled: Boolean = true) {
    val window = LocalActivity.current?.window
    val active = enabled && !ScreenshotMode.allowed
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
