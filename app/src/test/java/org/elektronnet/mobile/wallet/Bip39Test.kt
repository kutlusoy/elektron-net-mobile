package org.elektronnet.mobile.wallet

import java.io.File
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun loadWordlist(): List<String> {
    val path = System.getenv("BIP39_WORDLIST") ?: "src/main/assets/bip39/english.txt"
    return File(path).readLines().filter { it.isNotBlank() }
}

private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

class Bip39Test {
    private val bip39 = Bip39(loadWordlist())

    @Test
    fun entropyVectors() {
        assertEquals(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
            bip39.fromEntropy(ByteArray(16)).joinToString(" "),
        )
        assertEquals(
            "legal winner thank year wave sausage worth useful legal winner thank yellow",
            bip39.fromEntropy(hex("7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f")).joinToString(" "),
        )
        assertEquals(24, bip39.fromEntropy(ByteArray(32)).size)
        assertEquals("art", bip39.fromEntropy(ByteArray(32)).last())
    }

    @Test
    fun seedVector() {
        val phrase = Bip39.parse("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about")
        assertEquals(
            "c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04",
            Bip39.toSeed(phrase, "TREZOR").toHex(),
        )
    }

    @Test
    fun validation() {
        val ok = Bip39.parse("legal winner thank year wave sausage worth useful legal winner thank yellow")
        assertTrue(bip39.validate(ok))
        assertFalse(bip39.validate(ok.dropLast(1) + "zoo"))
        assertFalse(bip39.validate(ok.dropLast(1)))
        assertFalse(bip39.validate(ok.dropLast(1) + "notaword"))
    }

    @Test
    fun parsingIsForgiving() {
        assertEquals(listOf("legal", "winner"), Bip39.parse("  Legal \n WINNER  "))
    }

    @Test
    fun generatedPhrasesRoundTrip() {
        val random = java.security.SecureRandom()
        repeat(50) {
            val entropy = ByteArray(if (it % 2 == 0) 16 else 32).also(random::nextBytes)
            assertTrue(bip39.validate(bip39.fromEntropy(entropy)))
        }
    }

    @Test
    fun suggestions() {
        assertEquals(listOf("abandon"), bip39.suggestions("aband"))
        assertTrue(bip39.suggestions("").isEmpty())
        assertTrue(bip39.isWord("zoo"))
    }

    @Test
    fun quizAcceptsOnlyCorrectWords() {
        val phrase = bip39.fromEntropy(ByteArray(16))
        val quiz = BackupQuiz(phrase, Random(1))
        assertEquals(3, quiz.positions.toSet().size)
        val right = quiz.positions.map { phrase[it] }
        assertTrue(quiz.check(right.map { " ${it.uppercase()} " }))
        assertFalse(quiz.check(right.dropLast(1) + "wrong"))
        assertEquals(listOf(quiz.positions.last()), quiz.wrongPositions(right.dropLast(1) + "wrong"))
        assertFalse(quiz.check(right.dropLast(1)))
    }

    @Test
    fun passphraseStrength() {
        assertEquals(PassphraseStrength.TooShort, PassphraseCheck.strength("abc"))
        assertEquals(PassphraseStrength.TooShort, PassphraseCheck.strength("xkqzmrt12"))
        assertEquals(PassphraseStrength.Weak, PassphraseCheck.strength("aaaaaaaaaa"))
        assertEquals(PassphraseStrength.Weak, PassphraseCheck.strength("abcdefghij"))
        assertEquals(PassphraseStrength.Weak, PassphraseCheck.strength("1234567890"))
        assertEquals(PassphraseStrength.Weak, PassphraseCheck.strength("longenough"))
        assertEquals(PassphraseStrength.Fair, PassphraseCheck.strength("xkqzmrtv12"))
        assertEquals(PassphraseStrength.Strong, PassphraseCheck.strength("correct horse battery"))
        assertFalse(PassphraseCheck.acceptable("short"))
        assertFalse(PassphraseCheck.acceptable("longenough"))
        assertTrue(PassphraseCheck.acceptable("xkqzmrtv12"))
        assertTrue(PassphraseCheck.acceptable("correct horse battery"))
    }

    @Test
    fun commonPassphrasesAreRefusedEvenWhenLongOrDecorated() {
        listOf("Password123!", "PASSWORD2024", "qwertyuiop", "Passwort1234", "bitcoin1234!", "passwordpassword", "Elektron123456").forEach {
            assertFalse(PassphraseCheck.acceptable(it))
        }
        assertTrue(PassphraseCheck.acceptable("Tr0ub4dor&3x"))
        assertTrue(PassphraseCheck.acceptable("blue-kettle-moves-slowly"))
    }
}
