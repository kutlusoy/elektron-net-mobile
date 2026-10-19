package org.elektronnet.mobile.ui.security

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.elektronnet.mobile.ui.model.AppLockModel

object AppLockState {
    private val model = AppLockModel()
    var locked by mutableStateOf(false)
        private set
    var enabled by mutableStateOf(false)
        private set

    private var started = false

    fun startOnce(enabledSetting: Boolean) {
        if (started) return
        started = true
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

    fun setEnabled(value: Boolean) {
        model.setEnabled(value)
        sync()
    }

    private fun sync() {
        locked = model.locked
        enabled = model.enabled
    }
}
