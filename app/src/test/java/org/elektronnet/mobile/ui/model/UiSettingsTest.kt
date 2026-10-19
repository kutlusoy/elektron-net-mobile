package org.elektronnet.mobile.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiSettingsTest {
    private val keys = setOf("blue", "rust", "red")

    @Test
    fun restoreFallsBackToDefaults() {
        val defaults = UiSettings.restore(null, null, keys)
        assertNull(defaults.themeKey)
        assertEquals(ThemeMode.Dark, defaults.mode)
        val bad = UiSettings.restore("neon", "System", keys)
        assertNull(bad.themeKey)
        assertEquals(ThemeMode.Dark, bad.mode)
    }

    @Test
    fun restoreKeepsValidValues() {
        val s = UiSettings.restore("rust", "Light", keys)
        assertEquals("rust", s.themeKey)
        assertEquals(ThemeMode.Light, s.mode)
    }

    @Test
    fun modeResolution() {
        assertTrue(UiSettings(mode = ThemeMode.Dark).dark)
        assertFalse(UiSettings(mode = ThemeMode.Light).dark)
    }
}
