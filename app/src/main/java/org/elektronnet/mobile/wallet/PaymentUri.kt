package org.elektronnet.mobile.wallet

import java.io.ByteArrayOutputStream

data class PaymentRequest(val address: String, val amountLep: Long? = null, val label: String? = null, val message: String? = null)

sealed interface UriParse {
    data class Ok(val request: PaymentRequest, val kind: AddressKind) : UriParse
    data class Invalid(val reason: UriProblem) : UriParse
}

enum class UriProblem { Scheme, Address, Amount, Duplicate, RequiredParameter, Encoding }

class PaymentUri(private val scheme: String, private val addresses: AddressCheck) {
    fun build(request: PaymentRequest): String {
        val params = mutableListOf<String>()
        request.amountLep?.let { params += "amount=" + Amount.format(it) }
        request.label?.takeIf { it.isNotEmpty() }?.let { params += "label=" + encode(it) }
        request.message?.takeIf { it.isNotEmpty() }?.let { params += "message=" + encode(it) }
        val address = if (addresses.classify(request.address) == AddressKind.Native) request.address.lowercase() else request.address
        return "${scheme.lowercase()}:" + address + if (params.isEmpty()) "" else "?" + params.joinToString("&")
    }

    fun qrText(request: PaymentRequest): String = build(request)

    fun parse(input: String): UriParse {
        val text = input.trim()
        if (!text.startsWith("$scheme:", ignoreCase = true)) return UriParse.Invalid(UriProblem.Scheme)
        val rest = text.substring(scheme.length + 1)
        val query = rest.substringAfter('?', "")
        val address = addresses.normalized(rest.substringBefore('?'))
        val kind = addresses.classify(address)
        if (address.isEmpty() || kind == AddressKind.Invalid || kind is AddressKind.ForeignNetwork) return UriParse.Invalid(UriProblem.Address)
        var amount: Long? = null
        var label: String? = null
        var message: String? = null
        val seen = mutableSetOf<String>()
        if (rest.contains('?')) {
            for (pair in query.split('&').filter { it.isNotEmpty() }) {
                val key = pair.substringBefore('=').lowercase()
                val rawValue = pair.substringAfter('=', "")
                if (!seen.add(key)) return UriParse.Invalid(UriProblem.Duplicate)
                when {
                    key == "amount" -> amount = Amount.parse(rawValue) ?: return UriParse.Invalid(UriProblem.Amount)
                    key == "label" -> label = clean(decode(rawValue) ?: return UriParse.Invalid(UriProblem.Encoding), MAX_LABEL)
                    key == "message" -> message = clean(decode(rawValue) ?: return UriParse.Invalid(UriProblem.Encoding), MAX_MESSAGE)
                    key.startsWith("req-") -> return UriParse.Invalid(UriProblem.RequiredParameter)
                }
            }
        }
        return UriParse.Ok(PaymentRequest(address, amount, label, message), kind)
    }

    private fun encode(value: String): String {
        val out = StringBuilder()
        for (b in value.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xff
            val unreserved = c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || c.toChar() in "-._~"
            if (unreserved) out.append(c.toChar()) else out.append('%').append("%02X".format(c))
        }
        return out.toString()
    }

    private fun decode(value: String): String? {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            if (value[i] == '%') {
                val hex = value.substring(i + 1, minOf(i + 3, value.length))
                if (hex.length != 2 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
                out.write(hex.toInt(16))
                i += 3
            } else {
                out.write(value[i].toString().toByteArray(Charsets.UTF_8))
                i++
            }
        }
        return try {
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(out.toByteArray())).toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            null
        }
    }

    private fun clean(value: String, max: Int): String =
        value.filter { !it.isISOControl() && it.code !in BIDI_CONTROLS && it != '\u200B' && it != '\uFEFF' }.take(max)

    private companion object {
        const val MAX_LABEL = 64
        const val MAX_MESSAGE = 256
        val BIDI_CONTROLS = (0x202A..0x202E) + (0x2066..0x2069) + listOf(0x200E, 0x200F, 0x061C)
    }
}
