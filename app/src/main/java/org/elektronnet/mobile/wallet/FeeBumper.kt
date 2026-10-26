package org.elektronnet.mobile.wallet

import java.math.BigDecimal
import java.math.RoundingMode
import org.elektronnet.mobile.node.RpcException

enum class BumpFailure { NotBumpable, FeeTooLow, WrongPassphrase, Mismatch, NodeNotReady, Rejected, Unknown }

class BumpException(val failure: BumpFailure, detail: String = "") : Exception(detail)

class FeeBumper(private val rpc: WalletRpc, private val walletName: String = WalletBootstrap.DEFAULT_WALLET) {
    fun currentRate(txid: String): BigDecimal {
        val info = guard { rpc.call("gettransaction", listOf(txid, true, true), walletName) as Map<*, *> }
        val fee = WalletReader.lep(info["fee"]).let { Math.abs(it) }
        val decoded = info["decoded"] as? Map<*, *> ?: throw BumpException(BumpFailure.NotBumpable)
        val vsize = (decoded["vsize"] as? Number)?.toInt() ?: throw BumpException(BumpFailure.NotBumpable)
        if (vsize <= 0) throw BumpException(BumpFailure.NotBumpable)
        return BigDecimal(fee).divide(BigDecimal(vsize), 2, RoundingMode.HALF_UP)
    }

    fun suggestedRate(txid: String): BigDecimal {
        val old = currentRate(txid)
        val fast = runCatching { FeeSource(rpc).options().last().lepPerVb }.getOrDefault(BigDecimal.ONE)
        return fast.max(old.multiply(BigDecimal("1.5"))).max(old.add(BigDecimal.ONE)).setScale(1, RoundingMode.UP)
    }

    fun estimate(txid: String, rate: BigDecimal): Pair<Long, Long> {
        val info = guard { rpc.call("gettransaction", listOf(txid, true, true), walletName) as Map<*, *> }
        val oldFee = Math.abs(WalletReader.lep(info["fee"]))
        val vsize = ((info["decoded"] as? Map<*, *>)?.get("vsize") as? Number)?.toInt() ?: throw BumpException(BumpFailure.NotBumpable)
        if (rate <= currentRate(txid)) throw BumpException(BumpFailure.FeeTooLow)
        return oldFee to rate.multiply(BigDecimal(vsize)).setScale(0, RoundingMode.UP).toLong()
    }

    private fun review(txid: String, psbt: String, rate: BigDecimal) {
        val original = guard { rpc.call("gettransaction", listOf(txid, true, true), walletName) as Map<*, *> }
        val oldFee = Math.abs(WalletReader.lep(original["fee"]))
        val oldOut = ((original["decoded"] as Map<*, *>)["vout"] as List<*>).map { it as Map<*, *> }
        val decoded = guard { rpc.call("decodepsbt", listOf(psbt), null) as Map<*, *> }
        val tx = decoded["tx"] as? Map<*, *> ?: throw BumpException(BumpFailure.Mismatch)
        val newOut = (tx["vout"] as List<*>).map { it as Map<*, *> }
        val inputs = (decoded["inputs"] as? List<*>)?.map { it as Map<*, *> } ?: throw BumpException(BumpFailure.Mismatch)
        val sumIn = inputs.sumOf { row -> WalletReader.lep((row["witness_utxo"] as? Map<*, *>)?.get("amount") ?: throw BumpException(BumpFailure.Mismatch)) }
        val sumOut = newOut.sumOf { WalletReader.lep(it["value"]) }
        val newFee = sumIn - sumOut
        if (newFee <= oldFee) throw BumpException(BumpFailure.Mismatch)
        fun key(out: Map<*, *>) = ((out["scriptPubKey"] as Map<*, *>)["address"]?.toString() ?: "") to WalletReader.lep(out["value"])
        val newKeys = newOut.map(::key)
        for (out in oldOut) {
            val (address, value) = key(out)
            if (address.isEmpty()) throw BumpException(BumpFailure.Mismatch)
            val change = guard { (rpc.call("getaddressinfo", listOf(address), walletName) as Map<*, *>)["ischange"] == true }
            if (!change && (address to value) !in newKeys) throw BumpException(BumpFailure.Mismatch)
        }
        val vsize = (runCatching { (rpc.call("analyzepsbt", listOf(psbt), null) as Map<*, *>)["estimated_vsize"] as? Number }.getOrNull()?.toInt()) ?: 1
        val effective = BigDecimal(newFee).divide(BigDecimal(vsize), 2, RoundingMode.HALF_UP)
        if (effective > rate.multiply(BigDecimal(3)).add(BigDecimal.ONE)) throw BumpException(BumpFailure.Mismatch)
    }

    fun bump(txid: String, rate: BigDecimal, passphrase: String): String {
        if (rate <= currentRate(txid)) throw BumpException(BumpFailure.FeeTooLow)
        var unlocked = false
        val signed: String
        try {
            try {
                rpc.call("walletpassphrase", listOf(passphrase, UNLOCK_SECONDS), walletName)
                unlocked = true
            } catch (e: RpcException) {
                throw BumpException(if (e.code == WRONG_PASSPHRASE) BumpFailure.WrongPassphrase else map(e).failure, e.message ?: "")
            }
            val created = guard {
                rpc.call("psbtbumpfee", linkedMapOf("txid" to txid, "options" to linkedMapOf("fee_rate" to rate, "replaceable" to true)), walletName) as Map<*, *>
            }
            val psbt = created["psbt"]?.toString() ?: throw BumpException(BumpFailure.NotBumpable, created["errors"]?.toString().orEmpty())
            review(txid, psbt, rate)
            val processed = guard { rpc.call("walletprocesspsbt", listOf(psbt, true, "ALL", true, false), walletName) as Map<*, *> }
            signed = processed["psbt"].toString()
            review(txid, signed, rate)
        } finally {
            if (unlocked) WalletLock.lock(rpc, walletName, passphrase)
        }
        val finalized = guard { rpc.call("finalizepsbt", listOf(signed, true), null) as Map<*, *> }
        if (finalized["complete"] != true) throw BumpException(BumpFailure.Unknown, "psbt incomplete")
        return guard { rpc.call("sendrawtransaction", listOf(finalized["hex"].toString()), null).toString() }
    }

    private fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: RpcException) {
        throw map(e)
    }

    private fun map(e: RpcException): BumpException = BumpException(
        when {
            e.inWarmup -> BumpFailure.NodeNotReady
            e.code == VERIFY_REJECTED || e.code == VERIFY_ERROR -> BumpFailure.Rejected
            (e.message ?: "").contains("bump", ignoreCase = true) || (e.message ?: "").contains("replace", ignoreCase = true) -> BumpFailure.NotBumpable
            else -> BumpFailure.Unknown
        },
        e.message ?: "",
    )

    private companion object {
        const val UNLOCK_SECONDS = 10
        const val WRONG_PASSPHRASE = -14
        const val VERIFY_ERROR = -25
        const val VERIFY_REJECTED = -26
    }
}
