package org.elektronnet.mobile.ui.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageChoicesTest {
    @Test
    fun choicesMatchTheShippedLocales() {
        val shipped = File("shipped-locales.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        assertEquals(shipped, LanguageChoices.choices.map { it.tag })
    }

    @Test
    fun onlyKnownTagsAreAccepted() {
        assertEquals("de", LanguageChoices.valid("de"))
        assertNull(LanguageChoices.valid("xx"))
        assertNull(LanguageChoices.valid(null))
    }
}
