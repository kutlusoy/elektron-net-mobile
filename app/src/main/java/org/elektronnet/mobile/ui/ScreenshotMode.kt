package org.elektronnet.mobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ScreenshotMode {
    // switches itself off after this time, the Settings switch needs a warning and a biometric or device lock check
    const val MINUTES = 15

    var allowed by mutableStateOf(false)
}
