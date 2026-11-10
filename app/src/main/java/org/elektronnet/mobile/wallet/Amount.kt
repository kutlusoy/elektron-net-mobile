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

    var coinProvider: () -> String = { "ELEK" }
    val currentCoin: String get() = coinProvider()

    var testnetProvider: () -> Boolean = { false }

    var styleProvider: () -> NumberStyle = { NumberStyle.forLocale(java.util.Locale.getDefault()) }
    val numberStyle: NumberStyle get() = styleProvider()

    fun symbolFor(unit: AmountUnit, coin: String = currentCoin, isTestnet: Boolean = testnetProvider()): String {
        val isBtc = coin.equals("BTC", ignoreCase = true)
        return when (unit) {
            AmountUnit.Elek -> if (isBtc) {
                if (isTestnet) "tBTC" else "BTC"
            } else {
                if (isTestnet) "tELEK" else "ELEK"
            }
            AmountUnit.MilliElek -> if (isBtc) "mBTC" else "mELEK"
            AmountUnit.Bit -> "bit"
            AmountUnit.Lepton -> if (isBtc) "sat" else "lep"
        }
    }

    fun unitSymbol(coin: String = currentCoin): String = symbolFor(unit, coin)

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

    // for reading, with the thousands and decimal separators of the user
    fun display(lep: Long): String = numberStyle.format(formatIn(unit, lep))

    // for text fields, the number is read back by parse
    fun plain(lep: Long): String = formatIn(unit, lep)

    fun displayWithUnit(lep: Long, coin: String = currentCoin): String = display(lep) + " " + unitSymbol(coin)
}
