package org.elektronnet.mobile.wallet

import java.text.DecimalFormatSymbols
import java.util.Locale

// choices for a separator, "default" follows the language and country
enum class SeparatorChoice(val key: String, val char: Char?) {
    Default("default", null),
    Comma("comma", ','),
    Period("period", '.'),
    Space("space", ' '),
    Apostrophe("apostrophe", '\''),
    ;

    companion object {
        fun of(key: String?): SeparatorChoice = entries.firstOrNull { it.key == key } ?: Default
    }
}

data class NumberStyle(val grouping: Boolean, val group: Char, val decimal: Char) {
    // plain is the number as written by the formatter: optional minus, digits, optional dot and decimals
    fun format(plain: String): String {
        val negative = plain.startsWith("-")
        val body = plain.removePrefix("-")
        val integer = body.substringBefore('.')
        val fraction = if (body.contains('.')) body.substringAfter('.') else null
        val grouped = if (grouping) integer.reversed().chunked(3).joinToString(group.toString()).reversed() else integer
        return (if (negative) "-" else "") + grouped + (fraction?.let { decimal + it } ?: "")
    }

    companion object {
        fun forLocale(
            locale: Locale,
            grouping: Boolean = true,
            groupChoice: SeparatorChoice = SeparatorChoice.Default,
            decimalChoice: SeparatorChoice = SeparatorChoice.Default,
        ): NumberStyle {
            val symbols = DecimalFormatSymbols.getInstance(locale)
            // amounts are written with Latin digits, so scripts with their own digits must not bring their own separators
            val latin = symbols.zeroDigit == '0'
            val decimal = decimalChoice.char ?: if (latin) symbols.decimalSeparator else '.'
            var group = groupChoice.char ?: if (latin) symbols.groupingSeparator else ','

            if (group == decimal) group = if (decimal == '.') ',' else '.'
            return NumberStyle(grouping, group, decimal)
        }
    }
}
