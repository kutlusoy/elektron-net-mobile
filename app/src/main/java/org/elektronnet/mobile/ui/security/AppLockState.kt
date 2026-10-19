package org.elektronnet.mobile.ui.security

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.elektronnet.mobile.ui.ScreenshotMode
import org.elektronnet.mobile.ui.model.AppLockModel

object AppLockState {
    private val model = AppLockModel()
    var locked by mutableStateOf(false)
        private set
    var enabled by mutableStateOf(false)
        private set
    var graceChoice by mutableStateOf(AppLockModel.GRACE_MS)
        private set

    private var started = false

    fun startOnce(enabledSetting: Boolean, graceMs: Long = AppLockModel.GRACE_MS) {
        if (started) return
        started = true
        model.graceMs = graceMs
        graceChoice = graceMs
        model.start(enabledSetting)
        sync()
    }

    fun onStopped() = model.onStopped(System.currentTimeMillis())

    fun onStarted() {
        model.onStarted(System.currentTimeMillis())
        sync()
    }

    fun unlock() {
        model.unlock()
        sync()
    }

    fun applyGrace(ms: Long) {
        model.graceMs = ms
        graceChoice = ms
    }

    fun applyEnabled(value: Boolean) {
        model.setEnabled(value)
        sync()
    }

    private fun sync() {
        if (model.locked) ScreenshotMode.allowed = false
        locked = model.locked
        enabled = model.enabled
    }
}
