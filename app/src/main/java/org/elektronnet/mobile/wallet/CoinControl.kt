package org.elektronnet.mobile.wallet

import org.elektronnet.mobile.node.RpcException

data class Coin(
    val txid: String,
    val vout: Int,
    val address: String?,
    val amountLep: Long,
    val confirmations: Int,
    val label: String?,
    val frozen: Boolean,
) {
    val id: String get() = "$txid:$vout"
}

data class AddressRow(val address: String, val label: String?, val receivedLep: Long, val txCount: Int)

class CoinControl(private val rpc: WalletRpc, private val walletName: String = WalletBootstrap.DEFAULT_WALLET) {
    fun coins(): List<Coin> {
        val spendable = (rpc.call("listunspent", listOf(0), walletName) as List<*>).map { entry ->
            val row = entry as Map<*, *>
            Coin(
                txid = row["txid"].toString(),
                vout = (row["vout"] as Number).toInt(),
                address = row["address"]?.toString(),
                amountLep = WalletReader.lep(row["amount"]),
                confirmations = (row["confirmations"] as? Number)?.toInt() ?: 0,
                label = row["label"]?.toString()?.takeIf { it.isNotEmpty() },
                frozen = false,
            )
        }
        val frozen = (rpc.call("listlockunspent", emptyList<Any?>(), walletName) as List<*>).mapNotNull { entry ->
            val row = entry as Map<*, *>
            val txid = row["txid"].toString()
            val vout = (row["vout"] as Number).toInt()
            val out = try {
                rpc.call("gettxout", listOf(txid, vout, true), null) as? Map<*, *>
            } catch (e: RpcException) {
                null
            } ?: return@mapNotNull null
            Coin(
                txid = txid,
                vout = vout,
                address = (out["scriptPubKey"] as? Map<*, *>)?.get("address")?.toString(),
                amountLep = WalletReader.lep(out["value"]),
                confirmations = (out["confirmations"] as? Number)?.toInt() ?: 0,
                label = null,
                frozen = true,
            )
        }
        return (spendable + frozen).sortedWith(compareByDescending<Coin> { it.amountLep }.thenBy { it.id })
    }

    fun setFrozen(coins: List<Coin>, frozen: Boolean) {
        if (coins.isEmpty()) return
        val outputs = coins.map { linkedMapOf("txid" to it.txid, "vout" to it.vout) }
        rpc.call("lockunspent", if (frozen) listOf(false, outputs, true) else listOf(true, outputs), walletName)
    }

    fun setLabel(address: String, label: String) {
        rpc.call("setlabel", listOf(address, label.trim()), walletName)
    }

    fun addresses(): List<AddressRow> =
        (rpc.call("listreceivedbyaddress", listOf(0, true), walletName) as List<*>).map { entry ->
            val row = entry as Map<*, *>
            AddressRow(
                address = row["address"].toString(),
                label = row["label"]?.toString()?.takeIf { it.isNotEmpty() },
                receivedLep = WalletReader.lep(row["amount"]),
                txCount = (row["txids"] as? List<*>)?.size ?: 0,
            )
        }.sortedWith(compareByDescending<AddressRow> { it.txCount }.thenBy { it.address })
}
