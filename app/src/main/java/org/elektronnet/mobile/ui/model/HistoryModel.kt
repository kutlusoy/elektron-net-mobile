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
    val netLep: Long = 0,
)

data class HistoryDay(val date: LocalDate, val rows: List<HistoryRow>)

data class BalanceUi(val amountText: String?, val pendingText: String?, val waitingForSync: Boolean, val trustedLep: Long = 0, val pendingLep: Long = 0, val reusedLep: Long = 0)

enum class HistoryFilter { All, Received, Sent }

object HistoryModel {
    fun filter(items: List<TxItem>, filter: HistoryFilter, query: String): List<TxItem> {
        val needle = query.trim().lowercase()
        return items.filter { item ->
            val kindOk = when (filter) {
                HistoryFilter.All -> true
                HistoryFilter.Received -> item.netLep > 0
                HistoryFilter.Sent -> item.netLep < 0
            }
            kindOk && (needle.isEmpty() || listOfNotNull(item.txid, item.address, item.label).any { it.lowercase().contains(needle) })
        }
    }

    const val PAGE_SIZE = 10

    fun newestFirst(items: List<TxItem>): List<TxItem> = items.sortedByDescending { it.timeSeconds }

    fun pageCount(total: Int, size: Int = PAGE_SIZE): Int = if (total <= 0) 1 else (total + size - 1) / size

    fun page(items: List<TxItem>, page: Int, size: Int = PAGE_SIZE): List<TxItem> {
        val sorted = newestFirst(items)
        val index = page.coerceIn(0, pageCount(sorted.size, size) - 1)
        return sorted.drop(index * size).take(size)
    }

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
            amountText = sign + Amount.display(Math.abs(item.netLep)),
            incoming = incoming,
            status = item.status,
            confirmations = item.confirmations,
            subtitle = item.label ?: item.address?.let(::shorten),
            timeSeconds = item.timeSeconds,
            netLep = item.netLep,
        )
    }

    // coins on reused addresses are the wallet's own, Core only keeps them out of "trusted" and of automatic coin selection
    fun balance(balances: Balances?, synced: Boolean, hasHistory: Boolean): BalanceUi {
        if (balances == null) return BalanceUi(null, null, waitingForSync = false)
        val pending = balances.pendingLep + balances.immatureLep
        val total = balances.trustedLep + balances.usedLep
        val empty = total == 0L && pending == 0L && !hasHistory
        return BalanceUi(
            amountText = if (!synced && empty) null else Amount.display(total),
            pendingText = if (pending != 0L) Amount.display(Math.abs(pending)) else null,
            waitingForSync = !synced && empty,
            trustedLep = total,
            pendingLep = Math.abs(pending),
            reusedLep = balances.usedLep,
        )
    }

    fun shorten(address: String): String =
        if (address.length <= 16) address else address.take(6) + "…" + address.takeLast(6)
}
