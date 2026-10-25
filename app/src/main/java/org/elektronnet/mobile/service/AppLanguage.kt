package org.elektronnet.mobile.service

import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import org.elektronnet.mobile.ui.model.LanguageChoices

object AppLanguage {
    private const val PREFS = "policy"
    private const val KEY = "language_tag"

    fun stored(context: Context): String? = LanguageChoices.valid(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    fun store(context: Context, tag: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply { if (tag == null) remove(KEY) else putString(KEY, tag) }.apply()
    }

    // the device locale as it was before the app changed the default
    private val systemLocale: Locale = Locale.getDefault()

    fun wrap(base: Context): Context {
        val tag = stored(base) ?: return base
        val chosen = Locale.forLanguageTag(tag)
        // the same language as the device keeps the device region, so numbers follow it (for example de-CH)
        val locale = if (chosen.country.isEmpty() && chosen.language == systemLocale.language) systemLocale else chosen
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
