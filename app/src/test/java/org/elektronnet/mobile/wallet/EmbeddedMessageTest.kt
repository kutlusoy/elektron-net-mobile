package org.elektronnet.mobile.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedMessageTest {
    @Test
    fun plainTextStaysAsItIs() {
        assertEquals("Hello, Zürich! 你好 🎉", EmbeddedMessage.clean("Hello, Zürich! 你好 🎉"))
        assertEquals("a\nb", EmbeddedMessage.clean("a\nb"))
    }

    @Test
    fun controlAndFormatCharactersAreRemoved() {
        assertEquals("ab", EmbeddedMessage.clean("a\u0000\u0007\u001b[31m".replace("[31m", "") + "b"))
        assertEquals("evil", EmbeddedMessage.clean("e‮v‪i⁦l⁩​﻿"))
        assertEquals("ab", EmbeddedMessage.clean("a\u0085  b"))
        assertEquals("ab", EmbeddedMessage.clean("a\uD800b"))
    }

    @Test
    fun lineBreaksAndSpacesAreNormalised() {
        assertEquals("a\nb", EmbeddedMessage.clean("a\r\nb"))
        assertEquals("a\n\nb", EmbeddedMessage.clean("a\n\n\n\n\nb"))
        assertEquals("a b", EmbeddedMessage.clean("a \t 　b"))
        assertEquals("a", EmbeddedMessage.clean("  \n a \n  "))
        assertEquals("", EmbeddedMessage.clean("​ ‮\n"))
    }

    @Test
    fun stackedMarksAreLimited() {
        val zalgo = "x" + "̀".repeat(40) + "b"
        assertEquals("x" + "̀".repeat(3) + "b", EmbeddedMessage.clean(zalgo))
    }

    @Test
    fun markupAndScriptsAreJustText() {
        val text = "<script>alert(1)</script> ' OR 1=1 -- \${jndi:ldap://x} %s %n"
        assertEquals(text, EmbeddedMessage.clean(text))
    }

    @Test
    fun lengthIsCountedInBytesAndCutOnACharacterBoundary() {
        val text = "ä".repeat(800)
        val cut = EmbeddedMessage.clean(text)
        assertEquals(EmbeddedMessage.MAX_BYTES, EmbeddedMessage.byteLength(cut))
        assertEquals(500, cut.length)
        val emoji = EmbeddedMessage.clean("🎉".repeat(300))
        assertTrue(EmbeddedMessage.byteLength(emoji) <= EmbeddedMessage.MAX_BYTES)
        assertEquals(emoji, EmbeddedMessage.clean(emoji))
    }

    @Test
    fun cleaningIsIdempotent() {
        listOf("x‮y\r\n\r\nz", "á́́́b", "  tab\there ").forEach {
            val once = EmbeddedMessage.clean(it)
            assertEquals(once, EmbeddedMessage.clean(once))
        }
    }

    @Test
    fun scriptUsesTheMinimalPush() {
        assertEquals("6a026869", EmbeddedMessage.scriptHex("hi"))
        assertEquals("6a4c4c" + "61".repeat(76), EmbeddedMessage.scriptHex("a".repeat(76)))
        assertEquals("6a4d" + "e803" + "61".repeat(1000), EmbeddedMessage.scriptHex("a".repeat(1000)))
    }

    @Test
    fun outputSizeAndExtraFee() {
        assertEquals(13, EmbeddedMessage.outputVBytes("hi"))
        assertEquals(8 + 3 + 1 + 3 + 1000, EmbeddedMessage.outputVBytes("a".repeat(1000)))
        assertEquals(26L, EmbeddedMessage.extraFeeLep("hi", java.math.BigDecimal("2")))
        assertEquals(14L, EmbeddedMessage.extraFeeLep("hi", java.math.BigDecimal("1.01")))
    }

    @Test
    fun foreignTextIsReadFromAScript() {
        assertEquals("hi", EmbeddedMessage.fromScript(EmbeddedMessage.scriptHex("hi")))
        val long = "ä".repeat(300)
        assertEquals(long, EmbeddedMessage.fromScript(EmbeddedMessage.scriptHex(long)))
        assertEquals("a\nb", EmbeddedMessage.fromScript("6a03610a62"))
    }

    @Test
    fun binaryAndHostileDataIsNotShownAsText() {
        assertEquals(null, EmbeddedMessage.fromScript("6a24" + "00".repeat(36)))
        assertEquals(null, EmbeddedMessage.fromScript("6a02ffff"))
        assertEquals(null, EmbeddedMessage.fromScript("6a0161" + "00"))
        assertEquals(null, EmbeddedMessage.fromScript("6a0261"))
        assertEquals(null, EmbeddedMessage.fromScript("6a"))
        assertEquals(null, EmbeddedMessage.fromScript("6a0107"))
        assertEquals(null, EmbeddedMessage.fromScript("76a914"))
        assertEquals(null, EmbeddedMessage.fromScript("zz"))
        assertEquals(null, EmbeddedMessage.fromScript("6a03e280ae"))
        assertEquals("evil", EmbeddedMessage.fromScript("6a07" + EmbeddedMessage.hex("e\u202Evil")))
    }

    @Test
    fun longForeignTextIsCutForDisplay() {
        val shown = EmbeddedMessage.fromScript(EmbeddedMessage.scriptHex("x".repeat(9000)))!!
        assertEquals(EmbeddedMessage.MAX_SHOWN_BYTES, EmbeddedMessage.byteLength(shown))
    }
}
