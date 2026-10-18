package org.elektronnet.mobile.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
fun SecureWindow(enabled: Boolean = true) {
    val window = (LocalContext.current as? Activity)?.window
    DisposableEffect(enabled) {
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            window?.decorView?.filterTouchesWhenObscured = true
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            window?.decorView?.filterTouchesWhenObscured = false
        }
    }
}
