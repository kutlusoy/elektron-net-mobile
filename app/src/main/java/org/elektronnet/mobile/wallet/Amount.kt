package org.elektronnet.mobile.wallet

import java.math.BigDecimal

object Amount {
    const val LEP_PER_ELEK = 100_000_000L
    const val MAX_LEP = 1_000_000_000L * LEP_PER_ELEK

    fun parse(text: String, allowComma: Boolean = false): Long? {
        var t = text.trim()
        if (allowComma) t = t.replace(',', '.')
        if (!Regex("[0-9]+(\\.[0-9]{1,8})?|\\.[0-9]{1,8}").matches(t)) return null
        val lep = BigDecimal(if (t.startsWith(".")) "0$t" else t).movePointRight(8)
        if (lep.signum() <= 0 || lep > BigDecimal(MAX_LEP)) return null
        return lep.longValueExact()
    }

    fun format(lep: Long): String {
        val whole = lep / LEP_PER_ELEK
        val frac = (lep % LEP_PER_ELEK).toString().padStart(8, '0').trimEnd('0')
        return if (frac.isEmpty()) whole.toString() else "$whole.$frac"
    }
}
