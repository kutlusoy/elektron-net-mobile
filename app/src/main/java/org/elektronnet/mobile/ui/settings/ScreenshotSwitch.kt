package org.elektronnet.mobile.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import org.elektronnet.mobile.R
import org.elektronnet.mobile.ui.ScreenshotMode
import org.elektronnet.mobile.ui.security.Biometric

// turning screenshots on takes a warning and a fingerprint, face or device lock check, so it cannot happen by accident
@Composable
internal fun ScreenshotSwitch() {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    var asking by remember { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf(false) }
    ToggleRow(R.string.settings_screenshots, R.string.settings_screenshots_hint, ScreenshotMode.allowed) { on ->
        if (on) {
            unavailable = false
            asking = true
        } else {
            ScreenshotMode.allowed = false
        }
    }
    if (unavailable) Text(stringResource(R.string.screenshot_auth_unavailable), color = MaterialTheme.colorScheme.error)
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.screenshot_confirm_title)) },
            text = { Text(stringResource(R.string.screenshot_confirm_body, ScreenshotMode.MINUTES)) },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    if (activity == null || !Biometric.available(context)) {
                        unavailable = true
                    } else {
                        Biometric.authenticate(
                            activity,
                            title = context.getString(R.string.screenshot_auth_title),
                            negative = context.getString(R.string.lock_cancel),
                            onSuccess = { ScreenshotMode.allowed = true },
                        )
                    }
                }) { Text(stringResource(R.string.screenshot_confirm_action)) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
