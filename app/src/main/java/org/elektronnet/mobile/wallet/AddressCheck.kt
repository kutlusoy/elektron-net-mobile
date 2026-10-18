package org.elektronnet.mobile.wallet

sealed interface AddressKind {
    data object Native : AddressKind
    data object LegacyBase58 : AddressKind
    data class ForeignNetwork(val hrp: String) : AddressKind
    data object Invalid : AddressKind
}

class AddressCheck(private val testnet: Boolean) {
    private val hrp = if (testnet) "tb" else "be"
    private val legacyVersions = if (testnet) setOf(0x6f, 0xc4) else setOf(0x00, 0x05)

    fun classify(raw: String): AddressKind {
        val address = raw.trim()
        if (address.isEmpty()) return AddressKind.Invalid
        Bech32.decode(address)?.let { decoded ->
            return if (decoded.hrp == hrp) AddressKind.Native else AddressKind.ForeignNetwork(decoded.hrp)
        }
        val payload = Base58.decodeChecked(address) ?: return AddressKind.Invalid
        if (payload.size != 21) return AddressKind.Invalid
        return if ((payload[0].toInt() and 0xff) in legacyVersions) AddressKind.LegacyBase58 else AddressKind.Invalid
    }

    fun normalized(raw: String): String {
        val address = raw.trim()
        return if (Bech32.decode(address) != null) address.lowercase() else address
    }
}

object AddressFormat {
    const val EMPHASIS = 6
    private const val GROUP = 4

    data class Run(val text: String, val emphasized: Boolean)

    fun runs(address: String): List<Run> {
        val out = mutableListOf<Run>()
        fun push(text: String, emphasized: Boolean) {
            if (out.isNotEmpty() && out.last().emphasized == emphasized) out[out.lastIndex] = Run(out.last().text + text, emphasized) else out.add(Run(text, emphasized))
        }
        address.forEachIndexed { i, c ->
            if (i > 0 && i % GROUP == 0) push(" ", false)
            push(c.toString(), i < EMPHASIS || i >= address.length - EMPHASIS)
        }
        return out
    }

    fun grouped(address: String): String = address.chunked(GROUP).joinToString(" ")
}
