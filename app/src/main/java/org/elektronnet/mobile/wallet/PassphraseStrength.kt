package org.elektronnet.mobile.wallet

enum class PassphraseStrength { TooShort, Weak, Fair, Strong }

object PassphraseCheck {
    const val MIN_LENGTH = 8

    fun strength(value: String): PassphraseStrength {
        if (value.length < MIN_LENGTH) return PassphraseStrength.TooShort
        val classes = listOf<(Char) -> Boolean>(Char::isLowerCase, Char::isUpperCase, Char::isDigit) .count { f -> value.any(f) } +
            (if (value.any { !it.isLetterOrDigit() }) 1 else 0)
        val distinct = value.toSet().size
        return when {
            distinct < 4 -> PassphraseStrength.Weak
            value.length >= 16 || (value.length >= 12 && classes >= 3) -> PassphraseStrength.Strong
            classes >= 2 && value.length >= 10 -> PassphraseStrength.Fair
            else -> PassphraseStrength.Weak
        }
    }

    fun acceptable(value: String): Boolean = value.length >= MIN_LENGTH
}
