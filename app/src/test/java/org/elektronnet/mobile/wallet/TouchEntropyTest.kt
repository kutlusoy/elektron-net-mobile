package org.elektronnet.mobile.wallet

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TouchEntropyTest {
    private fun scribble(seed: Long, touch: TouchEntropy = TouchEntropy(), count: Int = 4000, start: Long = 1_000_000_000L): TouchEntropy {
        val r = Random(seed)
        var x = 500f
        var y = 900f
        var t = start
        repeat(count) {
            x += (r.nextGaussian() * 25).toFloat()
            y += (r.nextGaussian() * 25).toFloat()
            t += 8_000_000L + r.nextInt(3_000_000)
            touch.add(x, y, t, 0.4f + r.nextFloat() * 0.2f)
        }
        return touch
    }

    @Test
    fun humanLikeMotionCompletes() {
        val touch = scribble(1)
        assertTrue(touch.complete)
        assertEquals(1f, touch.progress)
        assertEquals(32, touch.finish().size)
    }

    @Test
    fun progressGrowsGradually() {
        val touch = scribble(2, count = 100)
        assertTrue(touch.progress > 0f && touch.progress < 1f)
        assertFalse(touch.complete)
        try {
            touch.finish()
            fail("incomplete pool accepted")
        } catch (e: IllegalStateException) {
        }
    }

    @Test
    fun standingStillOrTinyJitterEarnsNothing() {
        val touch = TouchEntropy()
        repeat(5000) { touch.add(100f + (it % 2), 100f, 1_000_000_000L + it * 10_000_000L) }
        assertEquals(0.0, touch.estimatedBits, 0.0)
    }

    @Test
    fun machineGeneratedPatternsEarnLittle() {
        val line = TouchEntropy()
        repeat(5000) { line.add(it * 5f, 100f, 1_000_000_000L + it * 10_000_000L) }
        assertTrue("straight line ${line.estimatedBits}", line.estimatedBits <= 12 * TouchEntropy.BITS_PER_EVENT + 1)
        val burst = TouchEntropy()
        repeat(5000) { burst.add(it * 5f, it * 3f, 1_000_000_000L + it * 100_000L) }
        assertTrue("burst ${burst.estimatedBits}", burst.estimatedBits <= 1.0)
    }

    @Test
    fun outputDependsOnEveryInput() {
        val a = scribble(3).finish().toList()
        assertNotEquals(a, scribble(4).finish().toList())
        assertNotEquals(a, scribble(3, start = 2_000_000_000L).finish().toList())
        assertEquals(a, scribble(3).finish().toList())
    }

    @Test
    fun touchDataOnlyAddsToTheSystemFloor() {
        val sys = SystemEntropy()
        val generator = EntropyGenerator(sys)
        val extra = scribble(5).finish()
        val withTouch = generator.fromSystem(PhraseStrength.Words12, extra)
        assertEquals(16, withTouch.size)
        assertNotEquals(withTouch.toList(), generator.fromSystem(PhraseStrength.Words12, extra).toList())
        val broken = EntropyGenerator(SystemEntropy(RandomSource { ByteArray(it) }, emptyList()))
        try {
            broken.fromSystem(PhraseStrength.Words12, extra)
            fail("touch data replaced a broken system generator")
        } catch (e: EntropyUnavailable) {
        }
    }

    @Test
    fun flowIncludesTouchStep() {
        assertEquals(FirstRunStep.PhraseShow, FirstRunFlow.next(FirstRunState(step = FirstRunStep.PhraseTouch)).step)
        assertEquals(FirstRunStep.PhraseIntro, FirstRunFlow.back(FirstRunState(step = FirstRunStep.PhraseTouch))!!.step)
    }
}
