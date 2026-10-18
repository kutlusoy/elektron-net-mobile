package org.elektronnet.mobile.ui.model

enum class ThemeMode {
    System, Dark, Light;

    companion object {
        fun parse(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: System
    }
}

enum class EffectSetting {
    Off, Subtle, Full;

    companion object {
        fun parse(value: String?): EffectSetting = entries.firstOrNull { it.name == value } ?: Subtle
    }
}

data class UiSettings(
    val themeKey: String? = null,
    val mode: ThemeMode = ThemeMode.System,
    val effects: EffectSetting = EffectSetting.Subtle,
) {
    companion object {
        fun restore(themeKey: String?, mode: String?, effects: String?, validKeys: Set<String>): UiSettings =
            UiSettings(themeKey?.takeIf { it in validKeys }, ThemeMode.parse(mode), EffectSetting.parse(effects))
    }

    fun isDark(systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.System -> systemDark
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }

    fun effectiveEffects(batterySaver: Boolean): EffectSetting = if (batterySaver) EffectSetting.Off else effects
}
