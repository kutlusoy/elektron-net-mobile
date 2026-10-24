package org.elektronnet.mobile.wallet


class NumberStyleTest {
    private val us = NumberStyle.forLocale(java.util.Locale.US)
    private val de = NumberStyle.forLocale(java.util.Locale.GERMANY)

    @org.junit.Test
    fun followsTheLanguageAndCountryByDefault() {
        org.junit.Assert.assertEquals("1,234,567.89", us.format("1234567.89"))
        org.junit.Assert.assertEquals("1.234.567,89", de.format("1234567.89"))
        org.junit.Assert.assertEquals("-12,345", us.format("-12345"))
        org.junit.Assert.assertEquals("123", us.format("123"))
        org.junit.Assert.assertEquals("1,000", us.format("1000"))
        org.junit.Assert.assertEquals("0.00000001", us.format("0.00000001"))
        org.junit.Assert.assertEquals("1.234.567", de.format("1234567"))
        org.junit.Assert.assertEquals(',', NumberStyle.forLocale(java.util.Locale("es", "ES")).decimal)
    }

    @org.junit.Test
    fun theUserMayOverrideBothSeparatorsAndSwitchGroupingOff() {
        val own = NumberStyle.forLocale(java.util.Locale.US, true, SeparatorChoice.Apostrophe, SeparatorChoice.Comma)
        org.junit.Assert.assertEquals("1'234'567,89", own.format("1234567.89"))
        val off = NumberStyle.forLocale(java.util.Locale.GERMANY, false)
        org.junit.Assert.assertEquals("1234567,89", off.format("1234567.89"))
        val space = NumberStyle.forLocale(java.util.Locale.US, true, SeparatorChoice.Space)
        org.junit.Assert.assertEquals("1 234", space.format("1234"))
    }

    @org.junit.Test
    fun theTwoSeparatorsNeverCollide() {
        val same = NumberStyle.forLocale(java.util.Locale.US, true, SeparatorChoice.Period, SeparatorChoice.Period)
        org.junit.Assert.assertNotEquals(same.group, same.decimal)
        val comma = NumberStyle.forLocale(java.util.Locale.US, true, SeparatorChoice.Comma, SeparatorChoice.Comma)
        org.junit.Assert.assertNotEquals(comma.group, comma.decimal)
    }

    @org.junit.Test
    fun displayGroupsButPlainStaysParsable() {
        val before = Amount.styleProvider
        Amount.styleProvider = { us }
        try {
            org.junit.Assert.assertEquals("1,234.5", Amount.display(123_450_000_000L))
            org.junit.Assert.assertEquals("1234.5", Amount.plain(123_450_000_000L))
            org.junit.Assert.assertEquals(123_450_000_000L, Amount.parse(Amount.plain(123_450_000_000L)))
        } finally {
            Amount.styleProvider = before
        }
    }

    @org.junit.Test
    fun scriptsWithTheirOwnDigitsStillGetLatinSeparators() {
        val ar = NumberStyle.forLocale(java.util.Locale.forLanguageTag("ar"))
        org.junit.Assert.assertEquals("1,234.5", ar.format("1234.5"))
    }

    @org.junit.Test
    fun everyAppLanguageYieldsDistinctSeparators() {
        org.elektronnet.mobile.ui.model.LanguageChoices.choices.forEach {
            val style = NumberStyle.forLocale(java.util.Locale.forLanguageTag(it.tag))
            org.junit.Assert.assertNotEquals(style.group, style.decimal)
            org.junit.Assert.assertTrue(it.tag, style.format("1234567.5").startsWith("1"))
        }
    }
}
