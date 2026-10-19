package org.elektronnet.mobile.wallet

import java.math.BigDecimal

data class Balances(val trustedLep: Long, val pendingLep: Long, val immatureLep: Long)

enum class TxKind { Received, Sent, Moved, Mined }

enum class TxStatus { Pending, Confirming, Confirmed, Conflicted, Abandoned }

data class TxItem(
    val txid: String,
    val kind: TxKind,
    val netLep: Long,
    val feeLep: Long?,
    val confirmations: Int,
    val timeSeconds: Long,
    val address: String?,
    val label: String?,
    val abandoned: Boolean,
    val immature: Boolean,
) {
    val status: TxStatus
        get() = when {
            abandoned -> TxStatus.Abandoned
            confirmations < 0 -> TxStatus.Conflicted
            confirmations == 0 -> TxStatus.Pending
            confirmations < CONFIRMED_AT -> TxStatus.Confirming
            else -> TxStatus.Confirmed
        }

    companion object {
        const val CONFIRMED_AT = 6
    }
}

class WalletReader(private val rpc: WalletRpc, private val walletName: String = WalletBootstrap.DEFAULT_WALLET) {
    fun balances(): Balances {
        val mine = (rpc.call("getbalances", emptyList<Any?>(), walletName) as Map<*, *>)["mine"] as Map<*, *>
        return Balances(lep(mine["trusted"]), lep(mine["untrusted_pending"]), lep(mine["immature"]))
    }

    fun transactions(limit: Int = DEFAULT_LIMIT): List<TxItem> {
        val entries = rpc.call("listtransactions", listOf("*", ENTRY_WINDOW, 0), walletName) as List<*>
        return group(entries.map { it as Map<*, *> }).reversed().sortedByDescending { it.timeSeconds }.take(limit)
    }

    fun allTransactions(pageSize: Int = PAGE_SIZE): List<TxItem> {
        val pages = ArrayList<List<Map<*, *>>>()
        var skip = 0
        repeat(MAX_PAGES) {
            val page = (rpc.call("listtransactions", listOf("*", pageSize, skip), walletName) as List<*>).map { it as Map<*, *> }
            pages.add(page)
            if (page.size < pageSize) return build(pages)
            skip += pageSize
        }
        return build(pages)
    }

    private fun build(pages: List<List<Map<*, *>>>): List<TxItem> =
        group(pages.asReversed().flatten()).reversed().sortedByDescending { it.timeSeconds }

    companion object {
        const val PAGE_SIZE = 500
        private const val MAX_PAGES = 100
        const val DEFAULT_LIMIT = 100
        private const val ENTRY_WINDOW = 1000

        fun lep(value: Any?): Long = when (value) {
            null -> 0L
            is BigDecimal -> value.movePointRight(8).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
            is Long -> value * Amount.LEP_PER_ELEK
            is Int -> value.toLong() * Amount.LEP_PER_ELEK
            is Double -> BigDecimal(value.toString()).movePointRight(8).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
            else -> 0L
        }

        fun group(entries: List<Map<*, *>>): List<TxItem> = entries.groupBy { it["txid"].toString() }.map { (txid, rows) ->
            val sends = rows.filter { it["category"] == "send" }
            val receives = rows.filter { it["category"] == "receive" }
            val generated = rows.filter { it["category"] == "generate" || it["category"] == "immature" || it["category"] == "orphan" }
            val fee = sends.firstNotNullOfOrNull { it["fee"] }?.let { lep(it) }
            val sendSum = sends.sumOf { lep(it["amount"]) }
            val receiveSum = receives.sumOf { lep(it["amount"]) }
            val generatedSum = generated.sumOf { lep(it["amount"]) }
            val net = sendSum + receiveSum + generatedSum + (fee ?: 0L)
            val kind = when {
                generated.isNotEmpty() -> TxKind.Mined
                sends.isNotEmpty() && receives.isNotEmpty() && sendSum + receiveSum == 0L -> TxKind.Moved
                sends.isNotEmpty() -> TxKind.Sent
                else -> TxKind.Received
            }
            val first = rows.first()
            val shown = when (kind) {
                TxKind.Sent -> sends.first()
                TxKind.Received -> receives.first()
                else -> first
            }
            TxItem(
                txid = txid,
                kind = kind,
                netLep = net,
                feeLep = fee?.let { -it },
                confirmations = (first["confirmations"] as? Number)?.toInt() ?: 0,
                timeSeconds = (first["time"] as? Number)?.toLong() ?: 0L,
                address = shown["address"]?.toString(),
                label = shown["label"]?.toString()?.takeIf { it.isNotEmpty() },
                abandoned = sends.any { it["abandoned"] == true },
                immature = generated.any { it["category"] == "immature" },
            )
        }
    }
}
