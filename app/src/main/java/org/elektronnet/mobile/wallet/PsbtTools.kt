package org.elektronnet.mobile.wallet

import java.math.BigDecimal
import java.math.RoundingMode
import org.elektronnet.mobile.node.RpcException

enum class PsbtStatus { NeedsSignatures, ReadyToFinalize, Final }

enum class PsbtWarning { HighFee, NoOwnedInputs, UnknownAmounts }

data class PsbtInputView(
    val address: String?,
    val amountLep: Long?,
    val owner: String?,
    val signatures: Int,
    val required: Int?,
    val final: Boolean,
)

data class PsbtOutputView(val address: String?, val amountLep: Long, val owner: String?)

data class PsbtView(
    val psbt: String,
    val inputs: List<PsbtInputView>,
    val outputs: List<PsbtOutputView>,
    val feeLep: Long?,
    val feeRate: BigDecimal?,
    val status: PsbtStatus,
    val signableBy: List<String>,
    val netByWallet: Map<String, Long>,
    val warnings: Set<PsbtWarning>,
) {
    val signaturesHave: Int get() = inputs.filter { !it.final }.minOfOrNull { it.signatures } ?: 0
    val signaturesNeed: Int? get() = inputs.filter { !it.final }.mapNotNull { it.required }.maxOrNull()
}

class PsbtTools(
    private val rpc: WalletRpc,
    private val wallets: () -> List<String>,
) {
    fun normalize(text: String): String {
        val cleaned = text.filterNot { it.isWhitespace() }
        if (!cleaned.startsWith(MAGIC_BASE64) || cleaned.length < 20 || !cleaned.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }) {
            throw SendException(SendFailure.InvalidPsbt)
        }
        return cleaned
    }

    fun inspect(text: String): PsbtView {
        val psbt = normalize(text)
        val decoded = try {
            rpc.call("decodepsbt", listOf(psbt), null) as Map<*, *>
        } catch (e: RpcException) {
            throw SendException(SendFailure.InvalidPsbt, e.message ?: "")
        }
        val tx = decoded["tx"] as? Map<*, *> ?: throw SendException(SendFailure.InvalidPsbt)
        val vin = (tx["vin"] as? List<*>)?.map { it as Map<*, *> } ?: throw SendException(SendFailure.InvalidPsbt)
        val vout = (tx["vout"] as? List<*>)?.map { it as Map<*, *> } ?: throw SendException(SendFailure.InvalidPsbt)
        val inputRows = (decoded["inputs"] as? List<*>)?.map { it as Map<*, *> } ?: throw SendException(SendFailure.InvalidPsbt)
        if (inputRows.size != vin.size) throw SendException(SendFailure.InvalidPsbt)
        val names = wallets()
        val ownerCache = HashMap<String, String?>()
        fun ownerOf(address: String?): String? {
            if (address == null) return null
            return ownerCache.getOrPut(address) { names.firstOrNull { isMine(address, it) } }
        }

        val inputs = inputRows.mapIndexed { index, row ->
            val utxo = (row["witness_utxo"] as? Map<*, *>) ?: nonWitnessOutput(row, vin[index])
            val script = utxo?.get("scriptPubKey") as? Map<*, *>
            val address = script?.get("address")?.toString()
            val signatures = (row["partial_signatures"] as? Map<*, *>)?.size ?: 0
            val final = row["final_scriptwitness"] != null || row["final_scriptSig"] != null
            PsbtInputView(
                address = address,
                amountLep = utxo?.get("amount")?.let { WalletReader.lep(it) },
                owner = ownerOf(address),
                signatures = signatures,
                required = requiredSignatures(row, script),
                final = final,
            )
        }
        val outputs = vout.map { out ->
            val address = (out["scriptPubKey"] as? Map<*, *>)?.get("address")?.toString()
            PsbtOutputView(address, WalletReader.lep(out["value"]), ownerOf(address))
        }
        val knownIn = inputs.all { it.amountLep != null }
        val sumIn = inputs.sumOf { it.amountLep ?: 0L }
        val sumOut = outputs.sumOf { it.amountLep }
        val fee = if (knownIn) sumIn - sumOut else null
        val analysis = runCatching { rpc.call("analyzepsbt", listOf(psbt), null) as Map<*, *> }.getOrNull()
        val vsize = (analysis?.get("estimated_vsize") as? Number)?.toInt()
        val rate = if (fee != null && vsize != null && vsize > 0) BigDecimal(fee).divide(BigDecimal(vsize), 2, RoundingMode.HALF_UP) else null
        val open = inputs.filter { !it.final }
        val status = when {
            open.isEmpty() -> PsbtStatus.Final
            analysis?.get("next") in setOf("finalizer", "extractor") -> PsbtStatus.ReadyToFinalize
            open.all { it.required != null && it.signatures >= it.required } -> PsbtStatus.ReadyToFinalize
            else -> PsbtStatus.NeedsSignatures
        }
        val net = names.associateWith { w ->
            outputs.filter { it.owner == w }.sumOf { it.amountLep } - inputs.filter { it.owner == w }.sumOf { it.amountLep ?: 0L }
        }.filter { (name, value) -> value != 0L || inputs.any { it.owner == name } || outputs.any { it.owner == name } }
        val signable = names.filter { w -> open.any { it.owner == w } }
        val warnings = mutableSetOf<PsbtWarning>()
        if (!knownIn) warnings += PsbtWarning.UnknownAmounts
        if (fee != null && (fee < 0 || (rate != null && rate > HIGH_RATE) || (sumOut > 0 && fee > sumOut / 20 && fee > HIGH_FEE_FLOOR))) warnings += PsbtWarning.HighFee
        if (open.isNotEmpty() && signable.isEmpty()) warnings += PsbtWarning.NoOwnedInputs
        return PsbtView(psbt, inputs, outputs, fee, rate, status, signable, net, warnings)
    }

    fun sign(psbt: String, walletName: String, passphrase: String): String {
        val normalized = normalize(psbt)
        var unlocked = false
        try {
            try {
                rpc.call("walletpassphrase", listOf(passphrase, UNLOCK_SECONDS), walletName)
                unlocked = true
            } catch (e: RpcException) {
                throw SendException(if (e.code == WRONG_PASSPHRASE) SendFailure.WrongPassphrase else SendFailure.Unknown, e.message ?: "")
            }
            val processed = try {
                rpc.call("walletprocesspsbt", listOf(normalized, true, "ALL", true, false), walletName) as Map<*, *>
            } catch (e: RpcException) {
                throw SendException(SendFailure.Unknown, e.message ?: "")
            }
            runCatching { rpc.call("walletlock", emptyList<Any?>(), walletName) }
            unlocked = false
            val result = processed["psbt"].toString()
            if (result == normalized) throw SendException(SendFailure.NothingToSign)
            return result
        } finally {
            if (unlocked) runCatching { rpc.call("walletlock", emptyList<Any?>(), walletName) }
        }
    }

    fun combine(psbts: List<String>): String {
        val normalized = psbts.map(::normalize)
        return try {
            rpc.call("combinepsbt", listOf(normalized), null).toString()
        } catch (e: RpcException) {
            throw SendException(SendFailure.InvalidPsbt, e.message ?: "")
        }
    }

    fun finalizeAndBroadcast(psbt: String): String {
        val finalized = try {
            rpc.call("finalizepsbt", listOf(normalize(psbt), true), null) as Map<*, *>
        } catch (e: RpcException) {
            throw SendException(SendFailure.InvalidPsbt, e.message ?: "")
        }
        if (finalized["complete"] != true) throw SendException(SendFailure.Unknown, "psbt incomplete")
        return try {
            rpc.call("sendrawtransaction", listOf(finalized["hex"].toString()), null).toString()
        } catch (e: RpcException) {
            throw SendException(if (e.code == -26 || e.code == -25) SendFailure.Rejected else SendFailure.Unknown, e.message ?: "")
        }
    }

    private fun isMine(address: String, wallet: String): Boolean = try {
        (rpc.call("getaddressinfo", listOf(address), wallet) as Map<*, *>)["ismine"] == true
    } catch (e: RpcException) {
        false
    }

    private fun nonWitnessOutput(row: Map<*, *>, vin: Map<*, *>): Map<*, *>? {
        val tx = row["non_witness_utxo"] as? Map<*, *> ?: return null
        val n = (vin["vout"] as? Number)?.toInt() ?: return null
        val out = (tx["vout"] as? List<*>)?.getOrNull(n) as? Map<*, *> ?: return null
        return mapOf("scriptPubKey" to out["scriptPubKey"], "amount" to out["value"])
    }

    private fun requiredSignatures(row: Map<*, *>, script: Map<*, *>?): Int? {
        val witnessScript = (row["witness_script"] as? Map<*, *>)?.get("hex")?.toString()
        if (witnessScript != null && witnessScript.length >= 4 && witnessScript.endsWith("ae")) {
            val op = witnessScript.substring(0, 2).toInt(16)
            if (op in 0x51..0x60) return op - 0x50
        }
        return when (script?.get("type")) {
            "witness_v0_keyhash", "pubkeyhash", "pubkey", "witness_v1_taproot" -> 1
            else -> null
        }
    }

    companion object {
        const val MAGIC_BASE64 = "cHNidP8"
        private const val UNLOCK_SECONDS = 10
        private const val WRONG_PASSPHRASE = -14
        private val HIGH_RATE = BigDecimal(1000)
        private const val HIGH_FEE_FLOOR = 100_000L
    }
}
