package org.elektronnet.mobile.ui.theme

import org.elektronnet.mobile.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PalettesTest {
    @Test
    fun hasTenThemes() {
        assertEquals(11, Palettes.all.size)
        assertEquals(11, Palettes.all.map { it.key }.toSet().size)
    }

    @Test
    fun defaultThemeExists() {
        assertEquals(BuildConfig.DEFAULT_THEME, Palettes.byKey(BuildConfig.DEFAULT_THEME).key)
    }

    @Test
    fun unknownKeyFallsBackToFirst() {
        assertEquals(Palettes.all.first(), Palettes.byKey("does-not-exist"))
    }

    @Test
    fun mainnetUsesBlueTestnetUsesRed() {
        assertTrue(BuildConfig.DEFAULT_THEME == if (BuildConfig.IS_TESTNET) "red" else "teal")
    }
}
