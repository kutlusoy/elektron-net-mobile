package org.elektronnet.mobile.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountUnitTest {
    private val oneElek = 100_000_000L

    @Test
    fun conversionsMatchTheDocumentedFactors() {
        assertEquals("1", Amount.formatIn(AmountUnit.Elek, oneElek))
        assertEquals("1000", Amount.formatIn(AmountUnit.MilliElek, oneElek))
        assertEquals("1000000", Amount.formatIn(AmountUnit.Bit, oneElek))
        assertEquals("100000000", Amount.formatIn(AmountUnit.Lepton, oneElek))
        assertEquals("0.137", Amount.formatIn(AmountUnit.Elek, 13_700_000L))
        assertEquals("137", Amount.formatIn(AmountUnit.MilliElek, 13_700_000L))
        assertEquals("1.5", Amount.formatIn(AmountUnit.MilliElek, 150_000L))
        assertEquals("0.01", Amount.formatIn(AmountUnit.Bit, 1L))
        assertEquals("6.99999859", Amount.formatIn(AmountUnit.Elek, 699_999_859L))
        assertEquals("0", Amount.formatIn(AmountUnit.Elek, 0L))
    }

    @Test
    fun parsingRoundTripsInEveryUnit() {
        for (unit in AmountUnit.entries) {
            for (lep in listOf(1L, 100L, 123_456_789L, oneElek, 5 * oneElek + 7)) {
                val text = Amount.formatIn(unit, lep)
                assertEquals("$unit $text", lep, Amount.parseIn(unit, text))
            }
        }
    }

    @Test
    fun parsingRefusesTooManyDecimalsAndBadInput() {
        assertNull(Amount.parseIn(AmountUnit.Elek, "0.123456789"))
        assertNull(Amount.parseIn(AmountUnit.MilliElek, "1.123456"))
        assertNull(Amount.parseIn(AmountUnit.Bit, "1.234"))
        assertNull(Amount.parseIn(AmountUnit.Lepton, "1.5"))
        assertNull(Amount.parseIn(AmountUnit.Elek, "0"))
        assertNull(Amount.parseIn(AmountUnit.Elek, "-1"))
        assertNull(Amount.parseIn(AmountUnit.Elek, "abc"))
        assertEquals(150_000L, Amount.parseIn(AmountUnit.MilliElek, "1,5", allowComma = true))
        assertEquals(1L, Amount.parseIn(AmountUnit.Lepton, "1"))
        assertEquals(1L, Amount.parseIn(AmountUnit.Bit, ".01"))
    }

    @Test
    fun displayFollowsTheProvider() {
        Amount.unitProvider = { AmountUnit.Bit }
        try {
            assertEquals("1000000 bit", Amount.displayWithUnit(oneElek))
        } finally {
            Amount.unitProvider = { AmountUnit.Elek }
        }
        assertEquals("1 ELEK", Amount.displayWithUnit(oneElek))
    }
}
