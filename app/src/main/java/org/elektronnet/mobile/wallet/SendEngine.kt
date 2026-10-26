package org.elektronnet.mobile.wallet

import java.math.BigDecimal
import java.math.RoundingMode
import org.elektronnet.mobile.node.RpcException

enum class SendFailure {
    InvalidAddress, ForeignNetwork, LegacyNotConfirmed, InvalidAmount, AmountTooSmall, InsufficientFunds,
    FeeTooLow, PolicyChanged, PsbtMismatch, WrongPassphrase, NodeNotReady, Rejected, InvalidPsbt, NothingToSign, Unknown,
}

class SendException(val failure: SendFailure, detail: String = "") : Exception(detail)

data class SendRequest(
    val address: String,
    val amountLep: Long?,
    val max: Boolean,
    val feeRate: BigDecimal,
    val allowLegacy: Boolean = false,
    val coins: List<Coin> = emptyList(),
    val subtractFee: Boolean = false,
) {
    val subtracts: Boolean get() = max || subtractFee
}

data class Review(
    val address: String,
    val amountLep: Long,
    val feeLep: Long,
    val changeLep: Long,
    val totalLep: Long,
    val feeRate: BigDecimal,
    val inputs: Int,
) {
    fun sameAs(other: Review): Boolean = address == other.address && amountLep == other.amountLep && feeLep == other.feeLep &&
        changeLep == other.changeLep && totalLep == other.totalLep && inputs == other.inputs
}

class SignedSend(val psbt: String, val complete: Boolean)

class PreparedSend(val psbt: String, val request: SendRequest, val review: Review, val policyFloor: BigDecimal, val grossLep: Long)

class SendEngine(
    private val rpc: WalletRpc,
    private val addresses: AddressCheck,
    private val walletName: String = WalletBootstrap.DEFAULT_WALLET,
    private val feeFallback: (() -> org.elektronnet.mobile.explorer.RecommendedFees?)? = null,
    private val spendReused: () -> Boolean = { false },
) {
    fun feeOptions(): List<FeeOption> = guard { FeeSource(rpc, feeFallback).options() }

    fun feeFloor(): BigDecimal = guard { FeeSource(rpc).floor() }

    fun spendableLep(): Long = WalletReader(rpc, walletName).balances().let { it.trustedLep + if (spendReused()) it.usedLep else 0L }

    fun reusedLep(): Long = WalletReader(rpc, walletName).balances().usedLep

    fun prepare(request: SendRequest): PreparedSend {
        AddressReuse.ensure(rpc, walletName)
        if (request.coins.isEmpty() && !request.max) {
            for (group in reusedGroups(request.amountLep ?: 0L)) {
                try {
                    return prepareWith(request.copy(coins = group))
                } catch (e: SendException) {
                    if (e.failure != SendFailure.InsufficientFunds) throw e
                }
            }
        }
        return withReusedKeptOut(request) { prepareWith(request) }
    }

    // Core's funding ignores avoid_reuse, so unless the setting allows it the reused coins are locked for the time of the call
    private fun <T> withReusedKeptOut(request: SendRequest, block: () -> T): T {
        if (spendReused() || request.coins.isNotEmpty()) return block()
        val reused = runCatching { CoinControl(rpc, walletName).coins().filter { it.reused && !it.frozen } }.getOrDefault(emptyList())
        val outputs = reused.map { linkedMapOf("txid" to it.txid, "vout" to it.vout) }
        if (outputs.isNotEmpty()) runCatching { rpc.call("lockunspent", listOf(false, outputs), walletName) }
        try {
            return block()
        } finally {
            if (outputs.isNotEmpty()) runCatching { rpc.call("lockunspent", listOf(true, outputs), walletName) }
        }
    }

    private fun reusedGroups(amount: Long): List<List<Coin>> = runCatching {
        CoinControl(rpc, walletName).coins()
            .filter { it.reused && !it.frozen && it.confirmations > 0 && it.address != null }
            .groupBy { it.address }
            .values
            .filter { group -> group.sumOf { it.amountLep } >= amount }
            .sortedBy { group -> group.sumOf { it.amountLep } }
    }.getOrDefault(emptyList())

    private fun prepareWith(request: SendRequest): PreparedSend {
        when (val kind = addresses.classify(request.address)) {
            AddressKind.Native -> Unit
            AddressKind.LegacyBase58 -> if (!request.allowLegacy) throw SendException(SendFailure.LegacyNotConfirmed)
            is AddressKind.ForeignNetwork -> throw SendException(SendFailure.ForeignNetwork, kind.hrp)
            AddressKind.Invalid -> throw SendException(SendFailure.InvalidAddress)
        }
        val address = addresses.normalized(request.address)
        val floor = guard { FeeSource(rpc).floor() }
        if (request.feeRate < floor) throw SendException(SendFailure.FeeTooLow)
        val gross = if (request.max) {
            (if (request.coins.isEmpty()) spendableLep() else request.coins.sumOf { it.amountLep }).also { if (it <= 0) throw SendException(SendFailure.InsufficientFunds) }
        } else {
            request.amountLep ?: 0L
        }
        if (gross <= 0) throw SendException(SendFailure.InvalidAmount)
        val options = linkedMapOf<String, Any?>("fee_rate" to request.feeRate, "replaceable" to true)
        if (request.coins.isNotEmpty()) options["add_inputs"] = false
        if (request.subtracts) options["subtractFeeFromOutputs"] = listOf(0)
        val created = guard {
            rpc.call(
                "walletcreatefundedpsbt",
                linkedMapOf(
                    "inputs" to request.coins.map { linkedMapOf("txid" to it.txid, "vout" to it.vout) },
                    "outputs" to listOf(linkedMapOf(address to BigDecimal(gross).movePointLeft(8))),
                    "locktime" to 0,
                    "options" to options,
                ),
                walletName,
            ) as Map<*, *>
        }
        val psbt = created["psbt"].toString()
        val prepared = PreparedSend(psbt, request.copy(address = address), Review(address, 0, 0, 0, 0, request.feeRate, 0), floor, gross)
        val review = validate(prepared)
        return PreparedSend(psbt, request.copy(address = address), review, floor, gross)
    }

    fun validate(prepared: PreparedSend): Review {
        val decoded = guard { rpc.call("decodepsbt", listOf(prepared.psbt), null) as Map<*, *> }
        val tx = decoded["tx"] as? Map<*, *> ?: throw SendException(SendFailure.PsbtMismatch)
        val outputs = (tx["vout"] as? List<*>)?.map { it as Map<*, *> } ?: throw SendException(SendFailure.PsbtMismatch)
        val inputRows = (decoded["inputs"] as? List<*>)?.map { it as Map<*, *> } ?: throw SendException(SendFailure.PsbtMismatch)
        if (inputRows.isEmpty() || outputs.isEmpty() || inputRows.size != (tx["vin"] as? List<*>)?.size) throw SendException(SendFailure.PsbtMismatch)
        if (prepared.request.coins.isNotEmpty()) {
            val used = (tx["vin"] as List<*>).map { (it as Map<*, *>)["txid"].toString() + ":" + (it["vout"] as Number).toInt() }.toSet()
            if (used != prepared.request.coins.map { it.id }.toSet()) throw SendException(SendFailure.PsbtMismatch)
        }
        val sumIn = inputRows.sumOf { row ->
            val utxo = row["witness_utxo"] as? Map<*, *> ?: throw SendException(SendFailure.PsbtMismatch)
            WalletReader.lep(utxo["amount"])
        }
        val rows = outputs.map { out ->
            val script = out["scriptPubKey"] as? Map<*, *> ?: throw SendException(SendFailure.PsbtMismatch)
            val address = script["address"]?.toString() ?: throw SendException(SendFailure.PsbtMismatch)
            address to WalletReader.lep(out["value"])
        }
        val sumOut = rows.sumOf { it.second }
        val fee = sumIn - sumOut
        (decoded["fee"] as? Any?)?.let { if (WalletReader.lep(it) != fee) throw SendException(SendFailure.PsbtMismatch) }
        if (fee <= 0) throw SendException(SendFailure.PsbtMismatch)
        val destinations = rows.filter { it.first == prepared.request.address }
        if (destinations.size != 1) throw SendException(SendFailure.PsbtMismatch)
        val amount = destinations.single().second
        val expected = if (prepared.request.subtracts) prepared.grossLep - fee else prepared.grossLep
        if (amount != expected || amount <= 0) throw SendException(SendFailure.PsbtMismatch)
        val others = rows.filter { it.first != prepared.request.address }
        if (others.size > 1 || (prepared.request.max && others.isNotEmpty())) throw SendException(SendFailure.PsbtMismatch)
        others.forEach { if (!isOwnChange(it.first)) throw SendException(SendFailure.PsbtMismatch) }
        val vsize = (runCatching { (rpc.call("analyzepsbt", listOf(prepared.psbt), null) as Map<*, *>)["estimated_vsize"] as? Number }.getOrNull()?.toInt())
            ?: (ESTIMATE_OVERHEAD + ESTIMATE_INPUT * inputRows.size + ESTIMATE_OUTPUT * rows.size)
        val rate = BigDecimal(fee).divide(BigDecimal(vsize), 2, RoundingMode.HALF_UP)
        if (rate > prepared.request.feeRate.multiply(RATE_TOLERANCE).add(BigDecimal.ONE)) throw SendException(SendFailure.PsbtMismatch)
        val change = others.sumOf { it.second }
        return Review(
            address = prepared.request.address,
            amountLep = amount,
            feeLep = fee,
            changeLep = change,
            totalLep = if (prepared.request.subtracts) prepared.grossLep else amount + fee,
            feeRate = rate,
            inputs = inputRows.size,
        )
    }

    fun sign(prepared: PreparedSend, passphrase: String): SignedSend {
        val before = validate(prepared)
        if (!before.sameAs(prepared.review)) throw SendException(SendFailure.PsbtMismatch)
        val floor = guard { FeeSource(rpc).floor() }
        if (floor > prepared.request.feeRate) throw SendException(SendFailure.PolicyChanged)
        var unlocked = false
        try {
            try {
                rpc.call("walletpassphrase", listOf(passphrase, UNLOCK_SECONDS), walletName)
                unlocked = true
            } catch (e: RpcException) {
                throw SendException(if (e.code == WRONG_PASSPHRASE) SendFailure.WrongPassphrase else map(e).failure, e.message ?: "")
            }
            val processed = guard {
                rpc.call("walletprocesspsbt", listOf(prepared.psbt, true, "ALL", true, false), walletName) as Map<*, *>
            }
            lockQuietly(passphrase)
            unlocked = false
            val signed = processed["psbt"].toString()
            if (!validate(PreparedSend(signed, prepared.request, prepared.review, prepared.policyFloor, prepared.grossLep)).sameAs(prepared.review)) {
                throw SendException(SendFailure.PsbtMismatch)
            }
            val finalized = guard { rpc.call("finalizepsbt", listOf(signed, false), null) as Map<*, *> }
            return SignedSend(signed, finalized["complete"] == true)
        } finally {
            if (unlocked) lockQuietly(passphrase)
        }
    }

    fun broadcast(prepared: PreparedSend, signedPsbt: String): String {
        val finalized = guard { rpc.call("finalizepsbt", listOf(signedPsbt, true), null) as Map<*, *> }
        if (finalized["complete"] != true) throw SendException(SendFailure.Unknown, "psbt incomplete")
        val maxRate = FeePresets.btcPerKvb(prepared.request.feeRate.multiply(BigDecimal(SEND_RATE_CAP)))
        return guard { rpc.call("sendrawtransaction", listOf(finalized["hex"].toString(), maxRate), null).toString() }
    }

    fun send(prepared: PreparedSend, passphrase: String): String {
        val signed = sign(prepared, passphrase)
        if (!signed.complete) throw SendException(SendFailure.Unknown, "psbt incomplete")
        return broadcast(prepared, signed.psbt)
    }

    private fun isOwnChange(address: String): Boolean = guard {
        val info = rpc.call("getaddressinfo", listOf(address), walletName) as Map<*, *>
        info["ismine"] == true && info["ischange"] == true
    }

    private fun lockQuietly(passphrase: String) {
        WalletLock.lock(rpc, walletName, passphrase)
    }

    private fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: RpcException) {
        throw map(e)
    }

    private fun map(e: RpcException): SendException = SendException(
        when {
            e.inWarmup -> SendFailure.NodeNotReady
            e.code == INSUFFICIENT_FUNDS || (e.message ?: "").contains("Insufficient funds", ignoreCase = true) ||
                (e.message ?: "").contains("preselected coins", ignoreCase = true) -> SendFailure.InsufficientFunds
            (e.message ?: "").contains("too small", ignoreCase = true) -> SendFailure.AmountTooSmall
            e.code == VERIFY_REJECTED || e.code == VERIFY_ERROR -> SendFailure.Rejected
            else -> SendFailure.Unknown
        },
        e.message ?: "",
    )

    private companion object {
        const val UNLOCK_SECONDS = 10
        const val WRONG_PASSPHRASE = -14
        const val INSUFFICIENT_FUNDS = -6
        const val VERIFY_ERROR = -25
        const val VERIFY_REJECTED = -26
        const val ESTIMATE_OVERHEAD = 11
        const val ESTIMATE_INPUT = 68
        const val ESTIMATE_OUTPUT = 31
        const val SEND_RATE_CAP = 3
        val RATE_TOLERANCE = BigDecimal("1.3")
    }
}
