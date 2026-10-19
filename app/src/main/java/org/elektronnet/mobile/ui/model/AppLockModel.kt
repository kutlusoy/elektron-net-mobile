package org.elektronnet.mobile.ui.model

class AppLockModel(private val graceMs: Long = GRACE_MS) {
    var enabled: Boolean = false
        private set
    var locked: Boolean = false
        private set
    private var stoppedAt: Long? = null

    fun start(enabled: Boolean) {
        this.enabled = enabled
        locked = enabled
        stoppedAt = null
    }

    fun onStopped(now: Long) {
        if (stoppedAt == null) stoppedAt = now
    }

    fun onStarted(now: Long) {
        val since = stoppedAt
        stoppedAt = null
        if (enabled && since != null && now - since >= graceMs) locked = true
    }

    fun unlock() {
        locked = false
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        locked = false
    }

    companion object {
        const val GRACE_MS = 30_000L
    }
}
