package org.elektronnet.mobile.wallet

import java.math.BigDecimal
import java.math.RoundingMode
import org.elektronnet.mobile.node.RpcException

enum class CpfpFailure { NotPending, NoOutput, TooSmall, FeeTooLow, WrongPassphrase, NodeNotReady, Rejected, Mismatch, Unknown }

class CpfpException(val failure: CpfpFailure, detail: String = "") : Exception(detail)

data class CpfpPlan(val parentTxid: String, val vout: Int, val inputLep: Long, val packageRate: BigDecimal, val targetRate: BigDecimal, val childFeeLep: Long, val sendLep: Long)

class Cpfp(private val rpc: WalletRpc, private val walletName: String = WalletBootstrap.DEFAULT_WALLET) {
    fun suggestedRate(parentTxid: String): BigDecimal {
        val current = packageOf(parentTxid).rate()
        val fast = runCatching { FeeSource(rpc).options().last().lepPerVb }.getOrDefault(BigDecimal.ONE)
        return fast.max(current.add(BigDecimal.ONE)).setScale(1, RoundingMode.UP)
    }

    fun plan(parentTxid: String, targetRate: BigDecimal): CpfpPlan {
        val pkg = packageOf(parentTxid)
        val current = pkg.rate()
        if (targetRate <= current) throw CpfpException(CpfpFailure.FeeTooLow)
        val coin = spendable(parentTxid)
        val childFee = targetRate.multiply(BigDecimal(pkg.vsize + CHILD_VSIZE)).setScale(0, RoundingMode.UP).toLong() - pkg.feeLep
        val child = BigDecimal(childFee).divide(BigDecimal(CHILD_VSIZE), 2, RoundingMode.UP)
        if (childFee <= 0 || child < MIN_CHILD_RATE) throw CpfpException(CpfpFailure.FeeTooLow)
        val send = coin.second - childFee
        if (send < DUST_LEP) throw CpfpException(CpfpFailure.TooSmall)
        return CpfpPlan(parentTxid, coin.first, coin.second, current, targetRate, childFee, send)
    }

    fun accelerate(parentTxid: String, targetRate: BigDecimal, passphrase: String): String {
        val plan = plan(parentTxid, targetRate)
        var unlocked = false
        val signed: String
        try {
            try {
                rpc.call("walletpassphrase", listOf(passphrase, UNLOCK_SECONDS), walletName)
                unlocked = true
            } catch (e: RpcException) {
                throw CpfpException(if (e.code == WRONG_PASSPHRASE) CpfpFailure.WrongPassphrase else map(e).failure, e.message ?: "")
            }
            val destination = guard { rpc.call("getrawchangeaddress", emptyList<Any?>(), walletName).toString() }
            val created = guard {
                rpc.call(
                    "createpsbt",
                    listOf(
                        listOf(linkedMapOf("txid" to parentTxid, "vout" to plan.vout)),
                        listOf(linkedMapOf(destination to BigDecimal(plan.sendLep).movePointLeft(8))),
                        0,
                        true,
                    ),
                    null,
                )
            }
            val psbt = created.toString()
            review(psbt, plan, destination)
            val processed = guard { rpc.call("walletprocesspsbt", listOf(psbt, true, "ALL", true, false), walletName) as Map<*, *> }
            signed = processed["psbt"].toString()
            review(signed, plan, destination)
        } finally {
            if (unlocked) runCatching { rpc.call("walletlock", emptyList<Any?>(), walletName) }
        }
        val finalized = guard { rpc.call("finalizepsbt", listOf(signed, true), null) as Map<*, *> }
        if (finalized["complete"] != true) throw CpfpException(CpfpFailure.Unknown, "psbt incomplete")
        return guard { rpc.call("sendrawtransaction", listOf(finalized["hex"].toString()), null).toString() }
    }

    private class Package(val feeLep: Long, val vsize: Int) {
        fun rate(): BigDecimal = BigDecimal(feeLep).divide(BigDecimal(vsize), 2, RoundingMode.HALF_UP)
    }

    private fun packageOf(txid: String): Package {
        val entry = try {
            rpc.call("getmempoolentry", listOf(txid), null) as Map<*, *>
        } catch (e: RpcException) {
            throw if (e.inWarmup) CpfpException(CpfpFailure.NodeNotReady) else CpfpException(CpfpFailure.NotPending, e.message ?: "")
        }
        val fees = entry["fees"] as? Map<*, *> ?: throw CpfpException(CpfpFailure.Unknown)
        val size = (entry["ancestorsize"] as? Number)?.toInt() ?: throw CpfpException(CpfpFailure.Unknown)
        if (size <= 0) throw CpfpException(CpfpFailure.Unknown)
        return Package(WalletReader.lep(fees["ancestor"]), size)
    }

    private fun spendable(parentTxid: String): Pair<Int, Long> {
        val unspent = guard { rpc.call("listunspent", listOf(0, 9_999_999, emptyList<String>(), true), walletName) as List<*> }
        val best = unspent.map { it as Map<*, *> }
            .filter { it["txid"] == parentTxid && it["spendable"] != false }
            .maxByOrNull { WalletReader.lep(it["amount"]) } ?: throw CpfpException(CpfpFailure.NoOutput)
        return (best["vout"] as Number).toInt() to WalletReader.lep(best["amount"])
    }

    private fun review(psbt: String, plan: CpfpPlan, destination: String) {
        val decoded = guard { rpc.call("decodepsbt", listOf(psbt), null) as Map<*, *> }
        val tx = decoded["tx"] as? Map<*, *> ?: throw CpfpException(CpfpFailure.Mismatch)
        val vin = (tx["vin"] as List<*>).map { it as Map<*, *> }
        val vout = (tx["vout"] as List<*>).map { it as Map<*, *> }
        if (vin.size != 1 || vin[0]["txid"] != plan.parentTxid || (vin[0]["vout"] as? Number)?.toInt() != plan.vout) throw CpfpException(CpfpFailure.Mismatch)
        if (vout.size != 1 || ((vout[0]["scriptPubKey"] as Map<*, *>)["address"]) != destination) throw CpfpException(CpfpFailure.Mismatch)
        val fee = plan.inputLep - WalletReader.lep(vout[0]["value"])
        if (fee != plan.childFeeLep) throw CpfpException(CpfpFailure.Mismatch)
    }

    private fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: RpcException) {
        throw map(e)
    }

    private fun map(e: RpcException): CpfpException = CpfpException(
        when {
            e.inWarmup -> CpfpFailure.NodeNotReady
            e.code == VERIFY_REJECTED || e.code == VERIFY_ERROR -> CpfpFailure.Rejected
            else -> CpfpFailure.Unknown
        },
        e.message ?: "",
    )

    companion object {
        const val CHILD_VSIZE = 111
        private const val UNLOCK_SECONDS = 10
        private const val WRONG_PASSPHRASE = -14
        private const val VERIFY_ERROR = -25
        private const val VERIFY_REJECTED = -26
        private const val DUST_LEP = 546L
        private val MIN_CHILD_RATE = BigDecimal("1.00")
    }
}
