package org.elektronnet.mobile.ui.model

enum class ThemeMode {
    Dark, Light;

    companion object {
        fun parse(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: Dark
    }
}

data class UiSettings(
    val themeKey: String? = null,
    val mode: ThemeMode = ThemeMode.Dark,
) {
    val dark: Boolean get() = mode == ThemeMode.Dark

    companion object {
        fun restore(themeKey: String?, mode: String?, validKeys: Set<String>): UiSettings =
            UiSettings(themeKey?.takeIf { it in validKeys }, ThemeMode.parse(mode))
    }
}
