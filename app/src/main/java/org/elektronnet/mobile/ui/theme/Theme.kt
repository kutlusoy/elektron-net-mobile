package org.elektronnet.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import org.elektronnet.mobile.BuildConfig

val LocalEffectLevel = staticCompositionLocalOf { EffectLevel.Subtle }
val LocalStatus = staticCompositionLocalOf { Palettes.darkStatus }
val LocalTokens = staticCompositionLocalOf { Palettes.byKey(BuildConfig.DEFAULT_THEME).dark }

private fun Tokens.toScheme(dark: Boolean): ColorScheme =
    if (dark) {
        darkColorScheme(
            primary = accent, onPrimary = onAccent, secondary = accent2, background = bg, onBackground = text,
            surface = surface, onSurface = text, surfaceVariant = surface2, onSurfaceVariant = muted,
            outline = border, tertiary = link,
        )
    } else {
        lightColorScheme(
            primary = link, onPrimary = onAccent, secondary = link, background = bg, onBackground = text,
            surface = surface, onSurface = text, surfaceVariant = surface2, onSurfaceVariant = muted,
            outline = border, tertiary = link,
        )
    }

@Composable
fun ElektronTheme(
    paletteKey: String = BuildConfig.DEFAULT_THEME,
    dark: Boolean = isSystemInDarkTheme(),
    effects: EffectLevel = EffectLevel.Subtle,
    content: @Composable () -> Unit,
) {
    val palette = Palettes.byKey(paletteKey)
    val tokens = if (dark) palette.dark else palette.light
    val status = if (dark) Palettes.darkStatus else Palettes.lightStatus
    CompositionLocalProvider(
        LocalEffectLevel provides effects,
        LocalStatus provides status,
        LocalTokens provides tokens,
    ) {
        MaterialTheme(colorScheme = tokens.toScheme(dark), content = content)
    }
}
