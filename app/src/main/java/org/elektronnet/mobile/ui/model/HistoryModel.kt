package org.elektronnet.mobile.ui.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.elektronnet.mobile.wallet.Amount
import org.elektronnet.mobile.wallet.Balances
import org.elektronnet.mobile.wallet.TxItem
import org.elektronnet.mobile.wallet.TxKind
import org.elektronnet.mobile.wallet.TxStatus

data class HistoryRow(
    val txid: String,
    val kind: TxKind,
    val amountText: String,
    val incoming: Boolean,
    val status: TxStatus,
    val confirmations: Int,
    val subtitle: String?,
    val timeSeconds: Long,
)

data class HistoryDay(val date: LocalDate, val rows: List<HistoryRow>)

data class BalanceUi(val amountText: String?, val pendingText: String?, val waitingForSync: Boolean)

object HistoryModel {
    fun days(items: List<TxItem>, zone: ZoneId): List<HistoryDay> =
        items.map(::row).groupBy { Instant.ofEpochSecond(it.timeSeconds).atZone(zone).toLocalDate() }
            .map { (date, rows) -> HistoryDay(date, rows.sortedByDescending { it.timeSeconds }) }
            .sortedByDescending { it.date }

    fun row(item: TxItem): HistoryRow {
        val incoming = item.netLep > 0
        val sign = if (item.netLep > 0) "+" else if (item.netLep < 0) "-" else ""
        return HistoryRow(
            txid = item.txid,
            kind = item.kind,
            amountText = sign + Amount.format(Math.abs(item.netLep)),
            incoming = incoming,
            status = item.status,
            confirmations = item.confirmations,
            subtitle = item.label ?: item.address?.let(::shorten),
            timeSeconds = item.timeSeconds,
        )
    }

    fun balance(balances: Balances?, synced: Boolean, hasHistory: Boolean): BalanceUi {
        if (balances == null) return BalanceUi(null, null, waitingForSync = false)
        val pending = balances.pendingLep + balances.immatureLep
        val empty = balances.trustedLep == 0L && pending == 0L && !hasHistory
        return BalanceUi(
            amountText = if (!synced && empty) null else Amount.format(balances.trustedLep),
            pendingText = if (pending != 0L) Amount.format(Math.abs(pending)) else null,
            waitingForSync = !synced && empty,
        )
    }

    fun shorten(address: String): String =
        if (address.length <= 16) address else address.take(6) + "…" + address.takeLast(6)
}
