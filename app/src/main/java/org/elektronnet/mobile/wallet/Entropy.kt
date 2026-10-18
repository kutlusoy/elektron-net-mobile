package org.elektronnet.mobile.wallet

import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

enum class PhraseStrength(val bytes: Int, val words: Int) {
    Words12(16, 12),
    Words24(32, 24),
    ;

    val bits: Int get() = bytes * 8
    val minRolls: Int get() = Math.ceil(bits / DiceCheck.BITS_PER_ROLL).toInt()
}

class EntropyUnavailable(message: String) : Exception(message)

fun interface RandomSource {
    fun read(count: Int): ByteArray
}

class SystemEntropy(
    private val primary: RandomSource = RandomSource { n -> ByteArray(n).also { SecureRandom().nextBytes(it) } },
    private val extra: List<RandomSource> = listOfNotNull(devUrandom()),
) {
    fun draw(count: Int = 32): ByteArray {
        require(count in 16..64)
        val parts = mutableListOf(healthy(primary, count) ?: throw EntropyUnavailable("system random generator failed its health check"))
        extra.forEach { source -> runCatching { healthy(source, count) }.getOrNull()?.let(parts::add) }
        val sha = MessageDigest.getInstance("SHA-512")
        sha.update(DOMAIN)
        parts.forEach { sha.update(it); it.fill(0) }
        return sha.digest().copyOf(count)
    }

    private fun healthy(source: RandomSource, count: Int): ByteArray? {
        repeat(ATTEMPTS) {
            val first = source.read(count)
            val second = source.read(count)
            val constant = first.all { it == first[0] }
            if (!constant && !first.contentEquals(second)) {
                second.fill(0)
                return first
            }
            first.fill(0)
            second.fill(0)
        }
        return null
    }

    companion object {
        private val DOMAIN = "elektron-system-entropy-v1".toByteArray()
        private const val ATTEMPTS = 3

        private fun devUrandom(): RandomSource? {
            val file = File("/dev/urandom")
            if (!file.canRead()) return null
            return RandomSource { n -> file.inputStream().use { input -> ByteArray(n).also { buf -> check(input.read(buf) == n) } } }
        }
    }
}

class EntropyGenerator(private val system: SystemEntropy = SystemEntropy()) {
    fun fromSystem(strength: PhraseStrength): ByteArray {
        val draw = system.draw(strength.bytes)
        check(draw.size == strength.bytes)
        return draw
    }

    fun fromDice(strength: PhraseStrength, rolls: String, mixSystem: Boolean): ByteArray {
        val check = DiceCheck.evaluate(rolls, strength)
        if (check.issue != null) throw IllegalArgumentException("dice rolls rejected: ${check.issue}")
        val sha = MessageDigest.getInstance("SHA-256")
        if (!mixSystem) return sha.digest(check.normalized.toByteArray(Charsets.US_ASCII)).copyOf(strength.bytes)
        sha.update(MIX_DOMAIN)
        sha.update(check.normalized.toByteArray(Charsets.US_ASCII))
        val extra = system.draw(32)
        sha.update(extra)
        extra.fill(0)
        return sha.digest().copyOf(strength.bytes)
    }

    private companion object {
        val MIX_DOMAIN = "elektron-dice-mix-v1".toByteArray()
    }
}

enum class DiceIssue { TooFew, InvalidCharacter, TooMany, LongRun, RepeatedPattern, Biased }

data class DiceResult(val normalized: String, val rolls: Int, val bits: Double, val issue: DiceIssue?)

object DiceCheck {
    val BITS_PER_ROLL: Double = Math.log(6.0) / Math.log(2.0)
    const val MAX_ROLLS = 200
    private const val MAX_RUN = 9
    private const val PATTERN_LENGTH = 9
    private const val CHI_SQUARE_LIMIT = 20.515

    fun evaluate(input: String, strength: PhraseStrength): DiceResult {
        val rolls = input.filterNot { it.isWhitespace() || it == ',' }
        val bits = rolls.length * BITS_PER_ROLL
        fun result(issue: DiceIssue?) = DiceResult(rolls, rolls.length, bits, issue)
        if (rolls.any { it !in '1'..'6' }) return result(DiceIssue.InvalidCharacter)
        if (rolls.length > MAX_ROLLS) return result(DiceIssue.TooMany)
        if (rolls.length < strength.minRolls) return result(DiceIssue.TooFew)
        if (longestRun(rolls) >= MAX_RUN) return result(DiceIssue.LongRun)
        if (hasRepeatedPattern(rolls)) return result(DiceIssue.RepeatedPattern)
        if (chiSquare(rolls) > CHI_SQUARE_LIMIT) return result(DiceIssue.Biased)
        return result(null)
    }

    private fun longestRun(s: String): Int {
        var best = 0
        var run = 0
        for (i in s.indices) {
            run = if (i > 0 && s[i] == s[i - 1]) run + 1 else 1
            best = maxOf(best, run)
        }
        return best
    }

    private fun hasRepeatedPattern(s: String): Boolean {
        if (s.length < PATTERN_LENGTH * 2) return false
        val seen = HashSet<String>()
        for (i in 0..s.length - PATTERN_LENGTH) if (!seen.add(s.substring(i, i + PATTERN_LENGTH))) return true
        return false
    }

    private fun chiSquare(s: String): Double {
        val expected = s.length / 6.0
        return ('1'..'6').sumOf { face ->
            val observed = s.count { it == face }
            (observed - expected) * (observed - expected) / expected
        }
    }
}
