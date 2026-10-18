package org.elektronnet.mobile.wallet

import java.math.BigDecimal
import java.math.RoundingMode
import org.elektronnet.mobile.node.RpcException

enum class SweepType(val prefix: String) {
    P2pkh("p2pkh"),
    P2wpkh("p2wpkh"),
    P2shP2wpkh("p2wpkh-p2sh"),
}

data class SweepKey(val wif: String, val compressed: Boolean, val types: List<SweepType>)

data class SweepUtxo(val txid: String, val vout: Int, val amountLep: Long, val scriptPubKey: String, val type: SweepType, val key: SweepKey, val redeemScript: String?)

data class SweepScan(val keys: List<SweepKey>, val utxos: List<SweepUtxo>) {
    val totalLep: Long get() = utxos.sumOf { it.amountLep }
}

data class SweepPlan(val unsignedHex: String, val scan: SweepScan, val destination: String, val amountLep: Long, val feeLep: Long, val feeRate: BigDecimal)

enum class SweepFailure { NoKeys, InvalidKey, WrongNetwork, TooManyKeys, NothingFound, TooSmall, InvalidDestination, ScanBusy, SignFailed, Rejected, NodeNotReady, Unknown }

class SweepException(val failure: SweepFailure, val detail: String = "") : Exception(detail)

class SweepEngine(
    private val rpc: WalletRpc,
    private val testnet: Boolean,
    private val addresses: AddressCheck,
) {
    private val secretVersion = if (testnet) 0xef else 0x80

    fun parseKeys(text: String): List<SweepKey> {
        val tokens = text.split(Regex("[\\s,;]+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) throw SweepException(SweepFailure.NoKeys)
        if (tokens.size > MAX_KEYS) throw SweepException(SweepFailure.TooManyKeys)
        return tokens.map(::parseKey).distinctBy { it.wif }
    }

    fun parseKey(token: String): SweepKey {
        val restricted = SweepType.entries.firstOrNull { token.startsWith(it.prefix + ":") }
        val wif = if (restricted != null) token.substringAfter(':') else token
        val payload = Base58.decodeChecked(wif) ?: throw SweepException(SweepFailure.InvalidKey)
        if (payload.size != 33 && payload.size != 34) throw SweepException(SweepFailure.InvalidKey)
        if ((payload[0].toInt() and 0xff) != secretVersion) throw SweepException(SweepFailure.WrongNetwork)
        val compressed = payload.size == 34
        if (compressed && payload[33].toInt() != 1) throw SweepException(SweepFailure.InvalidKey)
        val allowed = if (compressed) SweepType.entries else listOf(SweepType.P2pkh)
        val types = if (restricted != null) {
            if (restricted !in allowed) throw SweepException(SweepFailure.InvalidKey)
            listOf(restricted)
        } else {
            allowed
        }
        return SweepKey(wif, compressed, types)
    }

    fun scan(keys: List<SweepKey>): SweepScan {
        val descriptors = keys.flatMap { key -> key.types.map { descriptorFor(it, key.wif) } }
        val checked = descriptors.map { d -> d + "#" + guard { (rpc.call("getdescriptorinfo", listOf(d), null) as Map<*, *>)["checksum"].toString() } }
        val result = guard { rpc.call("scantxoutset", listOf("start", checked), null) as Map<*, *> }
        if (result["success"] != true) throw SweepException(SweepFailure.Unknown)
        val scripts = scriptTable(keys)
        val utxos = (result["unspents"] as? List<*>).orEmpty().map { it as Map<*, *> }.mapNotNull { u ->
            val spk = u["scriptPubKey"].toString()
            val owner = scripts[spk] ?: return@mapNotNull null
            SweepUtxo(u["txid"].toString(), (u["vout"] as Number).toInt(), WalletReader.lep(u["amount"]), spk, owner.type, owner.key, owner.redeemScript)
        }
        if (utxos.isEmpty()) throw SweepException(SweepFailure.NothingFound)
        return SweepScan(keys, utxos)
    }

    fun abortScan() {
        runCatching { rpc.call("scantxoutset", listOf("abort"), null) }
    }

    fun plan(scan: SweepScan, destination: String, feeRate: BigDecimal): SweepPlan {
        if (addresses.classify(destination) != AddressKind.Native) throw SweepException(SweepFailure.InvalidDestination)
        val address = addresses.normalized(destination)
        val vsize = BASE_VSIZE + OUTPUT_VSIZE + scan.utxos.sumOf { inputVsize(it) }
        val fee = feeRate.multiply(BigDecimal(vsize)).setScale(0, RoundingMode.UP).toLong()
        val amount = scan.totalLep - fee
        if (amount < DUST) throw SweepException(SweepFailure.TooSmall)
        val inputs = scan.utxos.map { linkedMapOf("txid" to it.txid, "vout" to it.vout) }
        val raw = guard {
            rpc.call(
                "createrawtransaction",
                listOf(inputs, listOf(linkedMapOf(address to BigDecimal(amount).movePointLeft(8)))),
                null,
            ).toString()
        }
        return SweepPlan(raw, scan, address, amount, fee, feeRate)
    }

    fun broadcast(plan: SweepPlan): String {
        val prev = plan.scan.utxos.map { u ->
            val entry = linkedMapOf<String, Any?>(
                "txid" to u.txid, "vout" to u.vout, "scriptPubKey" to u.scriptPubKey, "amount" to BigDecimal(u.amountLep).movePointLeft(8),
            )
            u.redeemScript?.let { entry["redeemScript"] = it }
            entry
        }
        val signed = guard {
            rpc.call("signrawtransactionwithkey", listOf(plan.unsignedHex, plan.scan.keys.map { it.wif }, prev), null) as Map<*, *>
        }
        if (signed["complete"] != true) throw SweepException(SweepFailure.SignFailed)
        val maxRate = FeePresets.btcPerKvb(plan.feeRate.multiply(BigDecimal(RATE_CAP)))
        return try {
            rpc.call("sendrawtransaction", listOf(signed["hex"].toString(), maxRate), null).toString()
        } catch (e: RpcException) {
            throw SweepException(SweepFailure.Rejected, e.message ?: "")
        }
    }

    private class Owner(val key: SweepKey, val type: SweepType, val redeemScript: String?)

    private fun scriptTable(keys: List<SweepKey>): Map<String, Owner> {
        val table = HashMap<String, Owner>()
        for (key in keys) {
            val wpkhScript = if (key.compressed) scriptOf("wpkh(${key.wif})") else null
            for (type in key.types) {
                val spk = when (type) {
                    SweepType.P2pkh -> scriptOf("pkh(${key.wif})")
                    SweepType.P2wpkh -> wpkhScript!!
                    SweepType.P2shP2wpkh -> scriptOf("sh(wpkh(${key.wif}))")
                }
                table[spk] = Owner(key, type, if (type == SweepType.P2shP2wpkh) wpkhScript else null)
            }
        }
        return table
    }

    private fun scriptOf(descriptor: String): String {
        val info = guard { rpc.call("getdescriptorinfo", listOf(descriptor), null) as Map<*, *> }
        val address = guard { (rpc.call("deriveaddresses", listOf(info["descriptor"].toString()), null) as List<*>).first().toString() }
        return guard { (rpc.call("validateaddress", listOf(address), null) as Map<*, *>)["scriptPubKey"].toString() }
    }

    private fun descriptorFor(type: SweepType, wif: String): String = when (type) {
        SweepType.P2pkh -> "pkh($wif)"
        SweepType.P2wpkh -> "wpkh($wif)"
        SweepType.P2shP2wpkh -> "sh(wpkh($wif))"
    }

    private fun inputVsize(u: SweepUtxo): Int = when (u.type) {
        SweepType.P2pkh -> if (u.key.compressed) 148 else 180
        SweepType.P2wpkh -> 68
        SweepType.P2shP2wpkh -> 91
    }

    private fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: RpcException) {
        throw SweepException(
            when {
                e.inWarmup -> SweepFailure.NodeNotReady
                e.code == SCAN_IN_PROGRESS -> SweepFailure.ScanBusy
                else -> SweepFailure.Unknown
            },
            e.message ?: "",
        )
    }

    companion object {
        const val MAX_KEYS = 20
        private const val BASE_VSIZE = 11
        private const val OUTPUT_VSIZE = 31
        private const val DUST = 294L
        private const val RATE_CAP = 3
        private const val SCAN_IN_PROGRESS = -8
    }
}
