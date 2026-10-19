package org.elektronnet.mobile.ui.model

data class LanguageChoice(val tag: String, val nativeName: String)

object LanguageChoices {
    val choices = listOf(
        LanguageChoice("en", "English"),
        LanguageChoice("de", "Deutsch"),
        LanguageChoice("es", "Espa\u00f1ol"),
        LanguageChoice("fr", "Fran\u00e7ais"),
        LanguageChoice("it", "Italiano"),
        LanguageChoice("pt-BR", "Portugu\u00eas (Brasil)"),
        LanguageChoice("ru", "\u0420\u0443\u0441\u0441\u043a\u0438\u0439"),
        LanguageChoice("tr", "T\u00fcrk\u00e7e"),
        LanguageChoice("id", "Bahasa Indonesia"),
        LanguageChoice("vi", "Ti\u1ebfng Vi\u1ec7t"),
        LanguageChoice("zh", "\u4e2d\u6587"),
        LanguageChoice("ja", "\u65e5\u672c\u8a9e"),
        LanguageChoice("ko", "\ud55c\uad6d\uc5b4"),
        LanguageChoice("ar", "\u0627\u0644\u0639\u0631\u0628\u064a\u0629"),
        LanguageChoice("hi", "\u0939\u093f\u0928\u094d\u0926\u0940"),
    )

    fun valid(tag: String?): String? = tag?.takeIf { value -> choices.any { it.tag == value } }
}
