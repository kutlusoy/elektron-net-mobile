package org.elektronnet.mobile.wallet

import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EntropyTest {
    private val rolls50 = "32461151351521144121541512665155412152342515356215"
    private val rolls100 = "5623156151524654345433226215354364351154232441615533635454113466116636564364631432514123262444124453"

    private fun issue(rolls: String, strength: PhraseStrength = PhraseStrength.Words12) = DiceCheck.evaluate(rolls, strength).let { it.issue ?: it.warning }

    private fun counter(): RandomSource {
        val n = AtomicInteger()
        return RandomSource { count -> ByteArray(count) { (n.getAndIncrement() * 37 + 11).toByte() } }
    }

    @Test
    fun rollRequirementsReachTheTargetBits() {
        assertEquals(50, PhraseStrength.Words12.minRolls)
        assertEquals(100, PhraseStrength.Words24.minRolls)
        assertTrue(50 * DiceCheck.BITS_PER_ROLL >= 128.0)
        assertTrue(49 * DiceCheck.BITS_PER_ROLL < 128.0)
        assertTrue(100 * DiceCheck.BITS_PER_ROLL >= 256.0)
    }

    @Test
    fun diceOnlyIsVerifiableWithSha256() {
        val out = EntropyGenerator().fromDice(PhraseStrength.Words12, rolls50, mixSystem = false)
        assertEquals("b4b7bf155511dfa13688d93685fea50b", out.joinToString("") { "%02x".format(it) })
        val out24 = EntropyGenerator().fromDice(PhraseStrength.Words24, rolls100, mixSystem = false)
        assertEquals(
            "21d00ff6b15e57b2168b387d3c12196db131c3f72e8b7d0b520efcdc470907a1",
            out24.joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun whitespaceAndCommasAreIgnored() {
        val spaced = rolls50.chunked(5).joinToString(" ")
        assertArrayEquals(
            EntropyGenerator().fromDice(PhraseStrength.Words12, rolls50, false),
            EntropyGenerator().fromDice(PhraseStrength.Words12, spaced, false),
        )
    }

    @Test
    fun mixedModeDependsOnDiceAndOnSystemRandom() {
        val a = EntropyGenerator(SystemEntropy(counter(), emptyList()))
        val shifted = AtomicInteger(1000)
        val b = EntropyGenerator(SystemEntropy({ n -> ByteArray(n) { (shifted.getAndIncrement() * 91 + 5).toByte() } }, emptyList()))
        val withA = a.fromDice(PhraseStrength.Words12, rolls50, true)
        assertNotEquals(withA.toList(), b.fromDice(PhraseStrength.Words12, rolls50, true).toList())
        assertNotEquals(withA.toList(), a.fromDice(PhraseStrength.Words12, rolls50.reversed(), true).toList())
        assertEquals(16, withA.size)
        assertEquals(32, a.fromDice(PhraseStrength.Words24, rolls100, true).size)
    }

    @Test
    fun diceChecks() {
        assertEquals(null, issue(rolls50))
        assertEquals(null, issue(rolls100, PhraseStrength.Words24))
        assertEquals(DiceIssue.TooFew, issue(rolls50.dropLast(1)))
        assertEquals(DiceIssue.TooFew, issue(rolls50, PhraseStrength.Words24))
        assertEquals(DiceIssue.InvalidCharacter, issue(rolls50.dropLast(1) + "7"))
        assertEquals(DiceIssue.InvalidCharacter, issue(rolls50.dropLast(1) + "0"))
        assertEquals(DiceIssue.LongRun, issue("1".repeat(10) + rolls50.drop(10)))
        assertEquals(DiceIssue.RepeatedPattern, issue("123456".repeat(9)))
        val loaded = java.util.Random(3)
        val biased = (1..60).joinToString("") { (if (loaded.nextInt(100) < 45) 1 else loaded.nextInt(6) + 1).toString() }
        assertEquals(DiceIssue.Biased, issue(biased))
        assertEquals(DiceIssue.TooMany, issue("1".repeat(201)))
    }

    @Test
    fun qualityWarningsNeverBlockEntropy() {
        val runny = "1".repeat(10) + rolls50.drop(10)
        val result = DiceCheck.evaluate(runny, PhraseStrength.Words12)
        assertEquals(null, result.issue)
        assertEquals(DiceIssue.LongRun, result.warning)
        assertEquals(16, EntropyGenerator().fromDice(PhraseStrength.Words12, runny, mixSystem = false).size)
        try {
            EntropyGenerator().fromDice(PhraseStrength.Words12, rolls50.dropLast(1), mixSystem = false)
            fail("too few rolls accepted")
        } catch (e: IllegalArgumentException) {
        }
    }

    @Test
    fun falseRejectRateOfHonestDiceIsLow() {
        val random = java.security.SecureRandom()
        var rejected = 0
        val trials = 4000
        repeat(trials) {
            val rolls = (1..50).joinToString("") { (random.nextInt(6) + 1).toString() }
            if (issue(rolls) != null) rejected++
        }
        assertTrue("rejected $rejected of $trials", rejected < trials * 0.01)
    }

    @Test
    fun systemEntropyRejectsBrokenSources() {
        val zeros = RandomSource { ByteArray(it) }
        val stuck = RandomSource { ByteArray(it) { 7 } }
        val repeating = RandomSource { n -> ByteArray(n) { (it * 3 + 1).toByte() } }
        for (broken in listOf(zeros, stuck, repeating)) {
            try {
                SystemEntropy(broken, emptyList()).draw()
                fail("broken source accepted")
            } catch (e: EntropyUnavailable) {
            }
        }
    }

    @Test
    fun brokenExtraSourceIsIgnoredButPrimaryIsRequired() {
        val good = SystemEntropy(counter(), listOf(RandomSource { ByteArray(it) }, RandomSource { throw java.io.IOException("x") }))
        assertEquals(32, good.draw().size)
        try {
            SystemEntropy(RandomSource { throw IllegalStateException("x") }, emptyList()).draw()
            fail("exception not propagated")
        } catch (e: IllegalStateException) {
        }
    }

    @Test
    fun mixingNeverReducesBelowTheStrongestSource() {
        val fixed = RandomSource { n -> ByteArray(n) { (it * 5 + 3).toByte() }.also { if (it.isNotEmpty()) it[0] = (System.nanoTime() and 0xff).toByte() } }
        val outputs = (1..200).map { SystemEntropy(RandomSource { n -> java.security.SecureRandom().generateSeed(n) }, listOf(fixed)).draw(16).toList() }
        assertEquals(200, outputs.toSet().size)
    }

    @Test
    fun realSourcesProduceDistinctWellSpreadOutput() {
        val generator = EntropyGenerator()
        val seen = HashSet<List<Byte>>()
        var ones = 0L
        val samples = 2000
        repeat(samples) {
            val e = generator.fromSystem(PhraseStrength.Words12)
            assertEquals(16, e.size)
            seen.add(e.toList())
            ones += e.sumOf { Integer.bitCount(it.toInt() and 0xff) }
        }
        assertEquals(samples, seen.size)
        val total = samples * 128.0
        assertTrue("bit balance ${ones / total}", Math.abs(ones / total - 0.5) < 0.01)
    }

    @Test
    fun phrasesFromAllPathsAreValid() {
        val bip39 = Bip39(loadWordlist())
        val g = EntropyGenerator()
        assertTrue(bip39.validate(bip39.fromEntropy(g.fromSystem(PhraseStrength.Words12))))
        assertEquals(24, bip39.fromEntropy(g.fromSystem(PhraseStrength.Words24)).size)
        assertTrue(bip39.validate(bip39.fromEntropy(g.fromDice(PhraseStrength.Words12, rolls50, true))))
        assertEquals(12, bip39.fromEntropy(g.fromDice(PhraseStrength.Words12, rolls50, false)).size)
        assertFalse(PhraseStrength.Words12.bits < 128)
    }
}
