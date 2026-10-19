package org.elektronnet.mobile.ui.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockModelTest {
    @Test
    fun coldStartIsLockedOnlyWhenEnabled() {
        val off = AppLockModel().apply { start(false) }
        assertFalse(off.locked)
        val on = AppLockModel().apply { start(true) }
        assertTrue(on.locked)
    }

    @Test
    fun shortAbsenceStaysUnlocked() {
        val lock = AppLockModel(graceMs = 30_000).apply { start(true); unlock() }
        lock.onStopped(1_000)
        lock.onStarted(20_000)
        assertFalse(lock.locked)
    }

    @Test
    fun longAbsenceLocksAgain() {
        val lock = AppLockModel(graceMs = 30_000).apply { start(true); unlock() }
        lock.onStopped(1_000)
        lock.onStarted(40_000)
        assertTrue(lock.locked)
    }

    @Test
    fun disabledNeverLocksAndEnablingDoesNotLockAtOnce() {
        val lock = AppLockModel(graceMs = 0).apply { start(false) }
        lock.onStopped(0)
        lock.onStarted(100_000)
        assertFalse(lock.locked)
        lock.setEnabled(true)
        assertFalse(lock.locked)
        lock.onStopped(0)
        lock.onStarted(100_000)
        assertTrue(lock.locked)
    }

    @Test
    fun repeatedStopEventsKeepTheFirstTime() {
        val lock = AppLockModel(graceMs = 30_000).apply { start(true); unlock() }
        lock.onStopped(1_000)
        lock.onStopped(35_000)
        lock.onStarted(36_000)
        assertTrue(lock.locked)
    }

    @Test
    fun graceCanBeChangedAndZeroLocksImmediately() {
        val lock = AppLockModel(graceMs = 30_000).apply { start(true); unlock() }
        lock.graceMs = 0
        lock.onStopped(1_000)
        lock.onStarted(1_001)
        assertTrue(lock.locked)
        assertTrue(AppLockModel.CHOICES_MS.contains(AppLockModel.GRACE_MS))
    }
}
