package org.elektronnet.mobile.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiSettingsTest {
    private val keys = setOf("blue", "neon", "red")

    @Test
    fun restoreFallsBackToDefaults() {
        val defaults = UiSettings.restore(null, null, null, keys)
        assertNull(defaults.themeKey)
        assertEquals(ThemeMode.Dark, defaults.mode)
        assertEquals(EffectSetting.Subtle, defaults.effects)
        val bad = UiSettings.restore("gone", "Sepia", "Max", keys)
        assertNull(bad.themeKey)
        assertEquals(ThemeMode.Dark, bad.mode)
        assertEquals(EffectSetting.Subtle, bad.effects)
    }

    @Test
    fun restoreKeepsValidValues() {
        val s = UiSettings.restore("neon", "Light", "Full", keys)
        assertEquals("neon", s.themeKey)
        assertEquals(ThemeMode.Light, s.mode)
        assertEquals(EffectSetting.Full, s.effects)
    }

    @Test
    fun modeResolution() {
        assertTrue(UiSettings(mode = ThemeMode.System).isDark(true))
        assertFalse(UiSettings(mode = ThemeMode.System).isDark(false))
        assertTrue(UiSettings(mode = ThemeMode.Dark).isDark(false))
        assertFalse(UiSettings(mode = ThemeMode.Light).isDark(true))
    }

    @Test
    fun batterySaverTurnsEffectsOff() {
        assertEquals(EffectSetting.Off, UiSettings(effects = EffectSetting.Full).effectiveEffects(true))
        assertEquals(EffectSetting.Full, UiSettings(effects = EffectSetting.Full).effectiveEffects(false))
    }
}
