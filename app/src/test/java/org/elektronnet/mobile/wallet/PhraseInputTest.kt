package org.elektronnet.mobile.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhraseInputTest {
    private val bip39 = Bip39(loadWordlist())

    @Test
    fun theWordBeingTypedIsTheLastOneWithoutASpace() {
        assertEquals("aban", PhraseInput.partial("legal Aban"))
        assertEquals("", PhraseInput.partial("legal "))
        assertEquals("", PhraseInput.partial(""))
    }

    @Test
    fun aSuggestionReplacesOnlyTheWordBeingTyped() {
        assertEquals("legal abandon ", PhraseInput.complete("legal aban", "abandon"))
        assertEquals("abandon ", PhraseInput.complete("aban", "abandon"))
        assertEquals("legal\nabandon ", PhraseInput.complete("legal\naba", "abandon"))
    }

    @Test
    fun onlyFinishedWordsAreCheckedAgainstTheList() {
        assertTrue(PhraseInput.unknown("legal winn", bip39).isEmpty())
        assertEquals(listOf("winnr"), PhraseInput.unknown("legal winnr ", bip39))
        assertEquals(listOf("winnr"), PhraseInput.unknown("legal winnr thank", bip39))
        assertTrue(PhraseInput.unknown("legal winner thank ", bip39).isEmpty())
    }

    @Test
    fun suggestionsFollowThePrefix() {
        val hints = bip39.suggestions(PhraseInput.partial("legal aban"), 8)
        assertEquals(listOf("abandon"), hints)
        assertTrue(bip39.suggestions(PhraseInput.partial("legal "), 8).isEmpty())
    }
}
