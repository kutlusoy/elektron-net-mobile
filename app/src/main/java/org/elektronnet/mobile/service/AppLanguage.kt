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

    fun wrap(base: Context): Context {
        val tag = stored(base) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
