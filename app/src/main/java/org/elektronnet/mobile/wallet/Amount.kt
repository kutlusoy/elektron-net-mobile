package org.elektronnet.mobile.wallet

import java.math.BigDecimal

enum class AmountUnit(val symbol: String, val decimals: Int) {
    Elek("ELEK", 8),
    MilliElek("mELEK", 5),
    Bit("bit", 2),
    Lepton("lep", 0),
}

object Amount {
    const val LEP_PER_ELEK = 100_000_000L
    const val MAX_LEP = 1_000_000_000L * LEP_PER_ELEK

    var unitProvider: () -> AmountUnit = { AmountUnit.Elek }
    val unit: AmountUnit get() = unitProvider()

    fun unitSymbol(): String = unit.symbol

    fun parse(text: String, allowComma: Boolean = false): Long? = parseIn(AmountUnit.Elek, text, allowComma)

    fun parseIn(unit: AmountUnit, text: String, allowComma: Boolean = false): Long? {
        var t = text.trim()
        if (allowComma) t = t.replace(',', '.')
        val pattern = if (unit.decimals == 0) "[0-9]+" else "[0-9]+(\\.[0-9]{1,${unit.decimals}})?|\\.[0-9]{1,${unit.decimals}}"
        if (!Regex(pattern).matches(t)) return null
        val lep = BigDecimal(if (t.startsWith(".")) "0$t" else t).movePointRight(unit.decimals)
        if (lep.signum() <= 0 || lep > BigDecimal(MAX_LEP)) return null
        return lep.longValueExact()
    }

    fun format(lep: Long): String = formatIn(AmountUnit.Elek, lep)

    fun formatIn(unit: AmountUnit, lep: Long): String {
        val text = BigDecimal(lep).movePointLeft(unit.decimals).setScale(unit.decimals).toPlainString()
        return if (unit.decimals == 0 || !text.contains('.')) text else text.trimEnd('0').trimEnd('.')
    }

    fun display(lep: Long): String = formatIn(unit, lep)

    fun displayWithUnit(lep: Long): String = display(lep) + " " + unit.symbol
}
