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
    fun dicePathJoinsTheSameQuiz() {
        assertEquals(FirstRunStep.PhraseShow, FirstRunFlow.next(FirstRunState(step = FirstRunStep.PhraseDice)).step)
        assertEquals(FirstRunStep.PhraseIntro, FirstRunFlow.back(FirstRunState(step = FirstRunStep.PhraseDice))!!.step)
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

    @Test
    fun addWalletSkipsTheNetworkAndNotificationSteps() {
        val add = FirstRunState(addWallet = true)
        assertEquals(FirstRunStep.PhraseIntro, FirstRunFlow.next(add).step)
        assertEquals(FirstRunStep.RestoreEnter, FirstRunFlow.next(add.copy(restore = true)).step)
        assertEquals(FirstRunStep.Welcome, FirstRunFlow.back(FirstRunState(step = FirstRunStep.PhraseIntro, addWallet = true))!!.step)
        assertEquals(FirstRunStep.Welcome, FirstRunFlow.back(FirstRunState(step = FirstRunStep.RestoreEnter, addWallet = true, restore = true))!!.step)
        assertNull(FirstRunFlow.back(add))
    }
}
