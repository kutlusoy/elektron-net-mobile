package org.elektronnet.mobile.wallet

enum class PassphraseStrength { TooShort, Weak, Fair, Strong }

object PassphraseCheck {
    const val MIN_LENGTH = 10

    // very common passwords, compared without case and without trailing digits and symbols
    private val COMMON = setOf(
        "password", "passwort", "passphrase", "qwerty", "qwertz", "qwertyuiop", "letmein", "welcome", "admin", "administrator",
        "iloveyou", "monkey", "dragon", "master", "sunshine", "princess", "football", "baseball", "superman", "batman",
        "trustno", "shadow", "login", "abc", "abcdef", "abcdefgh", "secret", "geheim", "hallo", "hallowelt", "willkommen",
        "changeme", "default", "test", "testtest", "bitcoin", "elektron", "elektronnet", "wallet", "crypto", "satoshi",
        "blockchain", "freedom", "whatever", "computer", "internet", "samsung", "android", "iphone", "google", "starwars",
        "asdfgh", "asdfghjkl", "zxcvbn", "zxcvbnm", "yxcvbnm", "1q2w3e", "1qaz2wsx", "azertyuiop", "mustang", "liverpool",
        "football", "hockey", "ranger", "jordan", "michael", "charlie", "jennifer", "hunter", "killer", "pepper",
    )

    private fun core(value: String): String = value.lowercase().trimEnd { !it.isLetter() }

    private fun common(value: String): Boolean {
        val c = core(value)
        return c in COMMON || value.lowercase() in COMMON || COMMON.any { it.length >= 6 && c == it + it }
    }

    // the whole value is one repeated character or one run such as 1234567890 or abcdefghij
    private fun trivial(value: String): Boolean {
        if (value.toSet().size < 5) return true
        val steps = value.zipWithNext { a, b -> b.code - a.code }
        return steps.all { it == 1 } || steps.all { it == -1 }
    }

    fun strength(value: String): PassphraseStrength {
        if (value.length < MIN_LENGTH) return PassphraseStrength.TooShort
        if (common(value) || trivial(value)) return PassphraseStrength.Weak
        val classes = listOf<(Char) -> Boolean>(Char::isLowerCase, Char::isUpperCase, Char::isDigit).count { f -> value.any(f) } +
            (if (value.any { !it.isLetterOrDigit() }) 1 else 0)
        return when {
            value.length >= 16 || (value.length >= 12 && classes >= 3) -> PassphraseStrength.Strong
            classes >= 2 -> PassphraseStrength.Fair
            else -> PassphraseStrength.Weak
        }
    }

    // a weak passphrase is refused, not only warned about
    fun acceptable(value: String): Boolean = strength(value).let { it == PassphraseStrength.Fair || it == PassphraseStrength.Strong }
}
