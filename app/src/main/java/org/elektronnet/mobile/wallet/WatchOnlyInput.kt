package org.elektronnet.mobile.wallet

// what a watch-only wallet is built from: one address, or ranged descriptors (each marked as receive or change chain)
data class WatchDescriptor(val descriptor: String, val internal: Boolean)

sealed interface WatchSource {
    data class Address(val address: String) : WatchSource
    data class Descriptors(val items: List<WatchDescriptor>) : WatchSource
}

enum class WatchProblem { Empty, Unrecognized, PrivateKey, WrongNetwork, Unsupported }

sealed interface WatchParse {
    data class Ok(val source: WatchSource, val summary: String) : WatchParse
    data class Invalid(val problem: WatchProblem) : WatchParse
}

// reads what a hardware or signing device hands over: an address, an extended public key (xpub, ypub, zpub, tpub, vpub),
// a key with its origin like [73c5da0a/84h/0h/0h]xpub..., or a full output descriptor
class WatchOnlyInput(private val testnet: Boolean, private val addresses: AddressCheck = AddressCheck(testnet)) {
    private class Version(val bytes: Int, val testnet: Boolean, val script: String?, val multisig: Boolean = false)

    private val versions = listOf(
        Version(0x0488B21E, false, null), Version(0x049D7CB2, false, "sh(wpkh)"), Version(0x04B24746, false, "wpkh"),
        Version(0x0295B43F, false, "sh(wpkh)", multisig = true), Version(0x02AA7ED3, false, "wpkh", multisig = true),
        Version(0x043587CF, true, null), Version(0x044A5262, true, "sh(wpkh)"), Version(0x045F1CF6, true, "wpkh"),
        Version(0x024289EF, true, "sh(wpkh)", multisig = true), Version(0x02575483, true, "wpkh", multisig = true),
    )

    private val keyPattern = Regex("""^(?:\[([0-9a-fA-F]{8})((?:/\d+['hH]?)*)])?([1-9A-HJ-NP-Za-km-z]{100,112})((?:/(?:\d+|\*)['hH]?)*)$""")
    private val privatePattern = Regex("""[xyztuvXYZTUV]prv|\b[5KLc9][1-9A-HJ-NP-Za-km-z]{50,51}\b""")
    private val checksum = Regex("""#[a-z0-9]{8}$""")

    fun parse(raw: String): WatchParse {
        val text = raw.trim()
        if (text.isEmpty()) return WatchParse.Invalid(WatchProblem.Empty)
        if (privatePattern.containsMatchIn(text)) return WatchParse.Invalid(WatchProblem.PrivateKey)
        when (val kind = addresses.classify(text)) {
            AddressKind.Native, AddressKind.LegacyBase58 -> return WatchParse.Ok(WatchSource.Address(addresses.normalized(text)), "address")
            is AddressKind.ForeignNetwork -> return WatchParse.Invalid(WatchProblem.WrongNetwork)
            AddressKind.Invalid -> Unit
        }
        val lines = candidates(text)
        if (lines.isEmpty()) return WatchParse.Invalid(WatchProblem.Unrecognized)
        val items = mutableListOf<WatchDescriptor>()
        var summary = ""
        for (line in lines) {
            val result = single(line) ?: return WatchParse.Invalid(WatchProblem.Unrecognized)
            if (result.problem != null) return WatchParse.Invalid(result.problem)
            items += result.items
            if (summary.isEmpty()) summary = result.summary
        }
        if (items.isEmpty()) return WatchParse.Invalid(WatchProblem.Unrecognized)
        return WatchParse.Ok(WatchSource.Descriptors(items.distinct()), summary)
    }

    private class Single(val items: List<WatchDescriptor> = emptyList(), val summary: String = "", val problem: WatchProblem? = null)

    // one item per line, a JSON export is searched for its descriptor strings
    private fun candidates(text: String): List<String> {
        if (text.startsWith("{") || text.startsWith("[{")) {
            return Regex(""""((?:wpkh|pkh|sh|wsh|tr)\([^"]*)"""").findAll(text).map { it.groupValues[1] }.toList()
        }
        return text.lines().map { it.trim().trim(',', '"') }.filter { it.isNotEmpty() }
    }

    private fun single(line: String): Single? {
        val text = line.replace(checksum, "")
        if (text.contains('(')) return descriptor(text)
        val match = keyPattern.matchEntire(text) ?: return null
        return key(match)
    }

    private fun descriptor(text: String): Single? {
        if (!Regex("""^(wpkh|pkh|sh|wsh|tr)\(.*\)$""").matches(text)) return null
        val expanded = if (text.contains("<0;1>")) listOf(text.replace("<0;1>", "0"), text.replace("<0;1>", "1")) else listOf(text)
        val items = expanded.map { WatchDescriptor(it, internal = Regex("""/1/\*[h']?\)+$""").containsMatchIn(it)) }
        return Single(items, text.substringBefore('(') + " descriptor")
    }

    private fun key(match: MatchResult): Single {
        val fingerprint = match.groupValues[1]
        val origin = match.groupValues[2]
        val encoded = match.groupValues[3]
        val children = match.groupValues[4]
        val payload = Base58.decodeChecked(encoded) ?: return Single(problem = WatchProblem.Unrecognized)
        if (payload.size != 78) return Single(problem = WatchProblem.Unrecognized)
        val number = ((payload[0].toInt() and 0xFF) shl 24) or ((payload[1].toInt() and 0xFF) shl 16) or ((payload[2].toInt() and 0xFF) shl 8) or (payload[3].toInt() and 0xFF)
        val version = versions.firstOrNull { it.bytes == number } ?: return Single(problem = WatchProblem.Unrecognized)
        if (version.testnet != testnet) return Single(problem = WatchProblem.WrongNetwork)
        if (version.multisig) return Single(problem = WatchProblem.Unsupported)
        // the node only knows the plain version, the type of script is taken from the prefix or the path
        val plain = payload.copyOf().also { p ->
            val target = if (testnet) 0x043587CF else 0x0488B21E
            for (i in 0 until 4) p[i] = (target shr (24 - 8 * i)).toByte()
        }
        val xpub = Base58.encodeChecked(plain)
        val purpose = Regex("""^/(\d+)['hH]""").find(origin)?.groupValues?.get(1)?.toInt()
        val script = when {
            purpose == 84 -> "wpkh"
            purpose == 49 -> "sh(wpkh)"
            purpose == 44 -> "pkh"
            purpose == 48 || purpose == 86 -> return Single(problem = WatchProblem.Unsupported)
            version.script != null -> version.script
            else -> "wpkh"
        }
        val originText = if (fingerprint.isNotEmpty()) "[" + fingerprint.lowercase() + origin.replace('\'', 'h').replace('H', 'h') + "]" else ""
        val paths = if (children.isEmpty()) listOf("/0/*" to false, "/1/*" to true)
        else if (children.endsWith("/0/*")) listOf(children to false, children.removeSuffix("/0/*") + "/1/*" to true)
        else listOf(children to children.endsWith("/1/*"))
        val items = paths.map { (path, internal) ->
            val inner = originText + xpub + path
            val descriptor = when (script) {
                "sh(wpkh)" -> "sh(wpkh($inner))"
                else -> "$script($inner)"
            }
            WatchDescriptor(descriptor, internal)
        }
        return Single(items, "$script key" + (if (fingerprint.isNotEmpty()) " $fingerprint" else ""))
    }
}
