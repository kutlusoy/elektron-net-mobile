package org.elektronnet.mobile.ui.model

import java.math.BigDecimal
import org.elektronnet.mobile.wallet.AddressCheck
import org.elektronnet.mobile.wallet.AddressKind
import org.elektronnet.mobile.wallet.Amount
import org.elektronnet.mobile.wallet.PaymentUri
import org.elektronnet.mobile.wallet.UriParse

sealed interface RecipientState {
    data object Empty : RecipientState
    data object Valid : RecipientState
    data object Legacy : RecipientState
    data class Foreign(val hrp: String) : RecipientState
    data object Invalid : RecipientState
}

data class ResolvedRecipient(val state: RecipientState, val address: String?, val amountLep: Long?, val note: String?) {
    val usable: Boolean get() = state == RecipientState.Valid || state == RecipientState.Legacy
}

sealed interface AmountState {
    data object Empty : AmountState
    data object Invalid : AmountState
    data class Over(val availableLep: Long) : AmountState
    data class Ok(val lep: Long) : AmountState
}

sealed interface FeeInput {
    data object Invalid : FeeInput
    data class Ok(val rate: BigDecimal) : FeeInput
}

object SendInput {
    const val MAX_CUSTOM_RATE = 10_000

    fun recipient(text: String, uris: PaymentUri, check: AddressCheck, scheme: String): ResolvedRecipient {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ResolvedRecipient(RecipientState.Empty, null, null, null)
        if (trimmed.startsWith("$scheme:", ignoreCase = true)) {
            return when (val parsed = uris.parse(trimmed)) {
                is UriParse.Ok -> ResolvedRecipient(stateOf(parsed.kind), parsed.request.address, parsed.request.amountLep, parsed.request.message)
                is UriParse.Invalid -> ResolvedRecipient(foreignOrInvalid(trimmed.substringAfter(':').substringBefore('?'), check), null, null, null)
            }
        }
        val kind = check.classify(trimmed)
        return ResolvedRecipient(stateOf(kind), if (kind == AddressKind.Native || kind == AddressKind.LegacyBase58) check.normalized(trimmed) else null, null, null)
    }

    // fiatRate set: the text is an amount in the fiat currency of that rate
    fun amount(text: String, availableLep: Long, fiatRate: org.elektronnet.mobile.fiat.FiatRate? = null): AmountState {
        if (text.isBlank()) return AmountState.Empty
        val lep = (if (fiatRate != null) org.elektronnet.mobile.fiat.FiatRates.lepFromFiat(text, fiatRate) else Amount.parseIn(Amount.unit, text, allowComma = true)) ?: return AmountState.Invalid
        return if (lep > availableLep) AmountState.Over(availableLep) else AmountState.Ok(lep)
    }

    fun customFee(text: String, floor: BigDecimal): FeeInput {
        val value = text.trim().replace(',', '.').toBigDecimalOrNull() ?: return FeeInput.Invalid
        return if (value < floor || value > BigDecimal(MAX_CUSTOM_RATE)) FeeInput.Invalid else FeeInput.Ok(value)
    }

    private fun stateOf(kind: AddressKind): RecipientState = when (kind) {
        AddressKind.Native -> RecipientState.Valid
        AddressKind.LegacyBase58 -> RecipientState.Legacy
        is AddressKind.ForeignNetwork -> RecipientState.Foreign(kind.hrp)
        AddressKind.Invalid -> RecipientState.Invalid
    }

    private fun foreignOrInvalid(raw: String, check: AddressCheck): RecipientState =
        when (val kind = check.classify(raw)) {
            is AddressKind.ForeignNetwork -> RecipientState.Foreign(kind.hrp)
            else -> RecipientState.Invalid
        }
}
