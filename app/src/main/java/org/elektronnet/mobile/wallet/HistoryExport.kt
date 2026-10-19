package org.elektronnet.mobile.wallet

import java.time.Instant
import java.time.ZoneOffset

object HistoryExport {
    const val HEADER = "txid,time_utc,type,amount_elek,fee_elek,confirmations,address,label"

    fun csv(items: List<TxItem>): String = (listOf(HEADER) + items.sortedBy { it.timeSeconds }.map(::row)).joinToString("\n") + "\n"

    private fun row(item: TxItem): String {
        val time = if (item.timeSeconds > 0) Instant.ofEpochSecond(item.timeSeconds).atOffset(ZoneOffset.UTC).toString() else ""
        val sign = if (item.netLep < 0) "-" else ""
        return listOf(
            item.txid,
            time,
            item.kind.name.lowercase(),
            sign + Amount.format(Math.abs(item.netLep)),
            item.feeLep?.let { Amount.format(it) }.orEmpty(),
            item.confirmations.toString(),
            item.address.orEmpty(),
            item.label.orEmpty(),
        ).joinToString(",") { cell(it) }
    }

    private fun cell(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r" && !value.matches(Regex("-?[0-9]+(\\.[0-9]+)?"))) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
