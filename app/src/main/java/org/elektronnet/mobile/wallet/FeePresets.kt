package org.elektronnet.mobile.wallet

import java.math.BigDecimal
import java.math.RoundingMode

enum class FeeLevel(val targetBlocks: Int) { Slow(30), Normal(5), Fast(1) }

data class FeeOption(val level: FeeLevel, val lepPerVb: BigDecimal, val estimated: Boolean)

object FeePresets {
    val STEP: BigDecimal = BigDecimal("0.1")

    fun derive(estimates: Map<FeeLevel, BigDecimal?>, floor: BigDecimal): List<FeeOption> {
        var previous = floor
        return FeeLevel.entries.map { level ->
            val raw = estimates[level]
            val value = (raw ?: previous).max(previous).max(floor).setScale(1, RoundingMode.UP)
            previous = value
            FeeOption(level, value, estimated = raw != null)
        }
    }

    fun lepPerVbFromBtcPerKvb(value: Any?): BigDecimal? = when (value) {
        is BigDecimal -> value.movePointRight(5)
        is Long -> BigDecimal(value).movePointRight(5)
        is Int -> BigDecimal(value).movePointRight(5)
        else -> null
    }

    fun btcPerKvb(lepPerVb: BigDecimal): BigDecimal = lepPerVb.movePointLeft(5)
}

class FeeSource(private val rpc: WalletRpc) {
    private companion object {
        val WALLET_MIN: BigDecimal = BigDecimal.ONE
    }

    fun floor(): BigDecimal {
        val pool = rpc.call("getmempoolinfo", emptyList<Any?>(), null) as Map<*, *>
        val minRelay = FeePresets.lepPerVbFromBtcPerKvb(pool["minrelaytxfee"]) ?: BigDecimal.ZERO
        val minPool = FeePresets.lepPerVbFromBtcPerKvb(pool["mempoolminfee"]) ?: BigDecimal.ZERO
        return minRelay.max(minPool).max(WALLET_MIN)
    }

    fun options(): List<FeeOption> {
        val estimates = FeeLevel.entries.associateWith { level ->
            val result = runCatching { rpc.call("estimatesmartfee", listOf(level.targetBlocks), null) as? Map<*, *> }.getOrNull()
            if (result == null || result["errors"] != null) null else FeePresets.lepPerVbFromBtcPerKvb(result["feerate"])
        }
        return FeePresets.derive(estimates, floor())
    }
}
