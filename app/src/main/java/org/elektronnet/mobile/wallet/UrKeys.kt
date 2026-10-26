package org.elektronnet.mobile.wallet

import org.elektronnet.mobile.ur.Cbor
import org.elektronnet.mobile.ur.Ur

// turns the key types of the UR registry (crypto-hdkey, crypto-output, crypto-account) into descriptor text
object UrKeys {
    private const val TAG_HDKEY = 303L
    private const val TAG_KEYPATH = 304L
    private const val TAG_COIN_INFO = 305L
    private val SCRIPTS = mapOf(400L to "sh", 401L to "wsh", 403L to "pkh", 404L to "wpkh", 406L to "multi", 407L to "sortedmulti")

    class Unsupported : IllegalArgumentException("Unsupported key data")

    // one descriptor text per output, null when the UR is not a key type or cannot be used
    fun descriptors(ur: Ur, testnet: Boolean): List<String>? = try {
        val item = Cbor.decode(ur.cbor)
        when (ur.type) {
            "crypto-hdkey" -> listOf(hdKey(unwrapTag(item, TAG_HDKEY), testnet))
            "crypto-output" -> listOf(output(item, testnet))
            "crypto-account" -> account(item, testnet)
            else -> null
        }
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun unwrapTag(item: Cbor, tag: Long): Cbor = if (item is Cbor.Tag && item.tag == tag) item.item else item

    private fun map(item: Cbor): Map<Long, Cbor> =
        (item as? Cbor.Map ?: throw Unsupported()).entries.associate { (k, v) -> (k as? Cbor.Uint ?: throw Unsupported()).value to v }

    private fun account(item: Cbor, testnet: Boolean): List<String> {
        val outputs = (map(item)[2L] as? Cbor.Array ?: throw Unsupported()).items
        return outputs.map { output(it, testnet) }
    }

    private fun output(item: Cbor, testnet: Boolean): String {
        var current = item
        val scripts = ArrayList<String>()
        while (current is Cbor.Tag && SCRIPTS.containsKey(current.tag)) {
            scripts += SCRIPTS.getValue(current.tag)
            current = current.item
        }
        if (scripts.isEmpty()) throw Unsupported()
        val inner = if (scripts.last() == "multi" || scripts.last() == "sortedmulti") {
            val m = map(current)
            val threshold = (m[1L] as? Cbor.Uint ?: throw Unsupported()).value
            val keys = (m[2L] as? Cbor.Array ?: throw Unsupported()).items.map { hdKey(unwrapTag(it, TAG_HDKEY), testnet) }
            scripts.removeAt(scripts.lastIndex).let { name -> "$name($threshold,${keys.joinToString(",")})" }
        } else {
            hdKey(unwrapTag(current, TAG_HDKEY), testnet)
        }
        return scripts.reversed().fold(inner) { acc, name -> "$name($acc)" }
    }

    private fun path(item: Cbor?, withWildcard: Boolean): List<String>? {
        val components = ((item as? Cbor.Tag)?.item ?: item)?.let { map(it)[1L] as? Cbor.Array } ?: return null
        val out = ArrayList<String>()
        var i = 0
        while (i + 1 < components.items.size) {
            val index = components.items[i]
            val hardened = (components.items[i + 1] as? Cbor.Simple)?.value == 21
            out += when {
                index is Cbor.Uint -> index.value.toString() + if (hardened) "h" else ""
                index is Cbor.Array && index.items.isEmpty() && withWildcard -> "*" + if (hardened) "h" else ""
                else -> throw Unsupported()
            }
            i += 2
        }
        return out
    }

    private fun hdKey(item: Cbor, testnet: Boolean): String {
        val m = map(item)
        if ((m[2L] as? Cbor.Simple)?.value == 21) throw IllegalArgumentException("Private key")
        val key = (m[3L] as? Cbor.Bytes ?: throw Unsupported()).value
        val chain = (m[4L] as? Cbor.Bytes ?: throw Unsupported()).value
        require(key.size == 33 && chain.size == 32) { "Unsupported key data" }
        (m[5L])?.let { use ->
            val network = (map(unwrapTag(use, TAG_COIN_INFO))[2L] as? Cbor.Uint)?.value ?: 0L
            require((network == 1L) == testnet) { "Wrong network" }
        }
        val origin = m[6L]?.let { unwrapTag(it, TAG_KEYPATH) }
        val originMap = origin?.let { map(it) }
        val components = path(origin, false)
        val fingerprint = (originMap?.get(2L) as? Cbor.Uint)?.value
        val depth = (originMap?.get(3L) as? Cbor.Uint)?.value?.toInt() ?: components?.size ?: 0
        val parent = (m[8L] as? Cbor.Uint)?.value ?: 0L
        val last = components?.lastOrNull()
        val child = if (last == null) 0L else last.removeSuffix("h").toLong() + if (last.endsWith("h")) 0x80000000L else 0L
        val payload = ByteArray(78)
        val version = if (testnet) 0x043587CF else 0x0488B21E
        for (i in 0 until 4) payload[i] = (version shr (24 - 8 * i)).toByte()
        payload[4] = depth.toByte()
        for (i in 0 until 4) payload[5 + i] = (parent shr (24 - 8 * i)).toByte()
        for (i in 0 until 4) payload[9 + i] = (child shr (24 - 8 * i)).toByte()
        System.arraycopy(chain, 0, payload, 13, 32)
        System.arraycopy(key, 0, payload, 45, 33)
        val xpub = Base58.encodeChecked(payload)
        val originText = if (fingerprint != null && components != null) "[" + "%08x".format(fingerprint) + (if (components.isEmpty()) "" else "/" + components.joinToString("/")) + "]" else ""
        val children = path(m[7L]?.let { unwrapTag(it, TAG_KEYPATH) }, true)?.takeIf { it.isNotEmpty() }?.joinToString("/", prefix = "/") ?: ""
        return originText + xpub + children
    }
}
