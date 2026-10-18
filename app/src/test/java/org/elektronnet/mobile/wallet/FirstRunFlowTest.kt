package org.elektronnet.mobile.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunFlowTest {
    private fun walk(start: FirstRunState, steps: Int): List<FirstRunStep> {
        val out = mutableListOf(start.step)
        var s = start
        repeat(steps) { s = FirstRunFlow.next(s); out += s.step }
        return out
    }

    @Test
    fun createPath() {
        assertEquals(
            listOf(
                FirstRunStep.Welcome, FirstRunStep.Network, FirstRunStep.Notifications, FirstRunStep.PhraseIntro,
                FirstRunStep.PhraseShow, FirstRunStep.PhraseQuiz, FirstRunStep.PassphraseNew, FirstRunStep.Creating, FirstRunStep.Done,
            ),
            walk(FirstRunState(), 8),
        )
    }

    @Test
    fun restorePath() {
        assertEquals(
            listOf(
                FirstRunStep.Notifications, FirstRunStep.RestoreEnter, FirstRunStep.PassphraseRestore,
                FirstRunStep.Restoring, FirstRunStep.Done,
            ),
            walk(FirstRunState(step = FirstRunStep.Notifications, restore = true), 4),
        )
    }

    @Test
    fun phraseIsDroppedWhenDone() {
        val s = FirstRunFlow.next(FirstRunState(step = FirstRunStep.Creating, phrase = listOf("a")))
        assertTrue(s.phrase.isEmpty())
    }

    @Test
    fun backNavigation() {
        assertNull(FirstRunFlow.back(FirstRunState(step = FirstRunStep.Creating)))
        assertNull(FirstRunFlow.back(FirstRunState(step = FirstRunStep.Welcome)))
        val s = FirstRunFlow.back(FirstRunState(step = FirstRunStep.PhraseShow, phrase = listOf("a")))!!
        assertEquals(FirstRunStep.PhraseIntro, s.step)
        assertTrue(s.phrase.isEmpty())
    }

    @Test
    fun failureReturnsToPassphraseStep() {
        val create = FirstRunFlow.failed(FirstRunState(step = FirstRunStep.Creating), BootstrapFailure.WeakPassphrase)
        assertEquals(FirstRunStep.PassphraseNew, create.step)
        val restore = FirstRunFlow.failed(FirstRunState(step = FirstRunStep.Restoring, restore = true), BootstrapFailure.Unknown)
        assertEquals(FirstRunStep.PassphraseRestore, restore.step)
        assertEquals(BootstrapFailure.Unknown, restore.failure)
    }
}
