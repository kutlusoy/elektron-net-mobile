package org.elektronnet.mobile.ui.model

import org.elektronnet.mobile.wallet.AddressFormat
import org.elektronnet.mobile.wallet.Amount
import org.elektronnet.mobile.wallet.PaymentRequest
import org.elektronnet.mobile.wallet.PaymentUri

data class ReceiveUiModel(
    val address: String,
    val runs: List<AddressFormat.Run>,
    val uri: String,
    val qrText: String,
    val copyText: String,
    val amountInvalid: Boolean,
    val showSyncNote: Boolean,
) {
    companion object {
        const val NOTE_LIMIT = 120

        fun build(address: String, uris: PaymentUri, amountText: String, note: String, synced: Boolean): ReceiveUiModel {
            val amount = if (amountText.isBlank()) null else Amount.parseIn(Amount.unit, amountText, allowComma = true)
            val request = PaymentRequest(address, amount, message = note.trim().take(NOTE_LIMIT).ifEmpty { null })
            return ReceiveUiModel(
                address = address,
                runs = AddressFormat.runs(address),
                uri = uris.build(request),
                qrText = uris.qrText(request),
                copyText = if (amount != null || request.message != null) uris.build(request) else address,
                amountInvalid = amountText.isNotBlank() && amount == null,
                showSyncNote = !synced,
            )
        }
    }
}
