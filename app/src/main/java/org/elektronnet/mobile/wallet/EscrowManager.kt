package org.elektronnet.mobile.wallet

import org.elektronnet.mobile.node.Json
import org.elektronnet.mobile.node.RpcException

enum class EscrowPhase { KeyOnly, Active }

data class Cosigner(val label: String, val key: String)

data class EscrowRecord(
    val id: Int,
    val name: String,
    val walletName: String,
    val account: Int,
    val myLabel: String,
    val myKey: String,
    val phase: EscrowPhase,
    val required: Int = 0,
    val cosigners: List<Cosigner> = emptyList(),
    val descriptor: String? = null,
    val currentAddress: String? = null,
    // the wallet whose seed the escrow key comes from, records from before this field belong to the first wallet
    val owner: String = WalletBootstrap.DEFAULT_WALLET,
) {
    val total: Int get() = cosigners.size + 1

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "id" to id, "name" to name, "wallet" to walletName, "account" to account, "myLabel" to myLabel, "myKey" to myKey,
        "phase" to phase.name, "required" to required,
        "cosigners" to cosigners.map { linkedMapOf("label" to it.label, "key" to it.key) },
        "descriptor" to descriptor, "address" to currentAddress, "owner" to owner,
    )

    companion object {
        fun fromMap(m: Map<*, *>): EscrowRecord = EscrowRecord(
            id = (m["id"] as Number).toInt(),
            name = m["name"].toString(),
            walletName = m["wallet"].toString(),
            account = (m["account"] as Number).toInt(),
            myLabel = m["myLabel"].toString(),
            myKey = m["myKey"].toString(),
            phase = EscrowPhase.valueOf(m["phase"].toString()),
            required = (m["required"] as? Number)?.toInt() ?: 0,
            cosigners = (m["cosigners"] as? List<*>).orEmpty().map { Cosigner((it as Map<*, *>)["label"].toString(), it["key"].toString()) },
            descriptor = m["descriptor"]?.toString(),
            currentAddress = m["address"]?.toString(),
            owner = m["owner"]?.toString() ?: WalletBootstrap.DEFAULT_WALLET,
        )
    }
}

interface EscrowStore {
    fun load(): List<EscrowRecord>
    fun save(records: List<EscrowRecord>)
}

class MemoryEscrowStore : EscrowStore {
    private var json: String = "[]"
    override fun load(): List<EscrowRecord> = (Json.parse(json) as List<*>).map { EscrowRecord.fromMap(it as Map<*, *>) }
    override fun save(records: List<EscrowRecord>) {
        json = Json.stringify(records.map { it.toMap() })
    }
}

enum class EscrowFailure {
    NameMissing, WalletMissing, WrongPassphrase, InvalidKey, DuplicateKey, WrongNetwork, BadThreshold, TooManyKeys,
    InvalidDescriptor, NotOurKey, NotKeyOnly, NotActive, HasFunds, ImportFailed, NodeNotReady, Unknown,
}

class EscrowException(val failure: EscrowFailure, detail: String = "") : Exception(detail)

class EscrowManager(
    private val rpc: WalletRpc,
    private val store: EscrowStore,
    private val testnet: Boolean,
    private val mainWallet: String = WalletBootstrap.DEFAULT_WALLET,
    private val walletPrefix: String = "escrow",
    private val removeWalletFiles: (String) -> Unit = {},
) {
    private val coinType = if (testnet) Descriptors.COIN_TYPE_TESTNET else Descriptors.COIN_TYPE_ELEKTRON
    private val keyPrefix = if (testnet) "tpub" else "xpub"

    fun list(): List<EscrowRecord> = store.load().filter { it.owner == mainWallet }

    fun get(id: Int): EscrowRecord = store.load().firstOrNull { it.id == id && it.owner == mainWallet } ?: throw EscrowException(EscrowFailure.WalletMissing)

    fun createKey(name: String, myLabel: String, passphrase: String, fixedAccount: Int? = null): EscrowRecord {
        val trimmed = name.trim().take(MAX_NAME)
        if (trimmed.isEmpty()) throw EscrowException(EscrowFailure.NameMissing)
        val records = store.load()
        val account = fixedAccount ?: ((records.filter { it.owner == mainWallet }.maxOfOrNull { it.account } ?: -1) + 1)
        val onNode = namesOnNode()
        var id = (records.maxOfOrNull { it.id } ?: 0) + 1
        while ("$walletPrefix-$id" in onNode) id++
        val wallet = "$walletPrefix-$id"
        var master: String? = masterKey(passphrase)
        var created = false
        try {
            guard {
                rpc.call(
                    "createwallet",
                    linkedMapOf("wallet_name" to wallet, "blank" to true, "passphrase" to passphrase, "descriptors" to true, "load_on_startup" to true),
                    null,
                )
            }
            created = true
            unlock(wallet, passphrase, SETUP_UNLOCK_SECONDS)
            val d = "wpkh($master/48h/${coinType}h/${account}h/2h/0/*)"
            importAll(wallet, listOf(ImportSpec(d, active = false, internal = false)), 0)
            val listed = guard { (rpc.call("listdescriptors", emptyList<Any?>(), wallet) as Map<*, *>)["descriptors"] as List<*> }
            val text = listed.map { (it as Map<*, *>)["desc"].toString() }.firstOrNull { KEY_FROM_DESC.containsMatchIn(it) } ?: throw EscrowException(EscrowFailure.Unknown)
            val key = KEY_FROM_DESC.find(text)!!.groupValues[1]
            val record = EscrowRecord(id, trimmed, wallet, account, myLabel.trim().take(MAX_LABEL).ifEmpty { "Me" }, key, EscrowPhase.KeyOnly, owner = mainWallet)
            store.save(records + record)
            return record
        } catch (e: Exception) {
            if (created) {
                runCatching { rpc.call("unloadwallet", linkedMapOf("wallet_name" to wallet, "load_on_startup" to false), null) }
                runCatching { removeWalletFiles(wallet) }
            }
            throw e
        } finally {
            master = null
            lockQuietly(wallet)
        }
    }

    fun activate(id: Int, required: Int, cosigners: List<Cosigner>, passphrase: String): EscrowRecord {
        val record = get(id)
        if (record.phase != EscrowPhase.KeyOnly) throw EscrowException(EscrowFailure.NotKeyOnly)
        val keys = cosigners.map { Cosigner(it.label.trim().take(MAX_LABEL).ifEmpty { "Partner" }, normalizeKey(it.key)) }
        keys.forEach { checkKey(it.key) }
        val all = listOf(record.myKey) + keys.map { it.key }
        if (all.map(::keyCore).toSet().size != all.size) throw EscrowException(EscrowFailure.DuplicateKey)
        if (all.size > MAX_KEYS) throw EscrowException(EscrowFailure.TooManyKeys)
        if (all.size < 2 || required < 1 || required > all.size) throw EscrowException(EscrowFailure.BadThreshold)
        val master = masterKey(passphrase)
        try {
            unlock(record.walletName, passphrase, SETUP_UNLOCK_SECONDS)
            val specs = listOf(0, 1).map { chain ->
                val parts = all.map { key ->
                    if (key == record.myKey) "$master/48h/${coinType}h/${record.account}h/2h/$chain/*" else "$key/$chain/*"
                }
                ImportSpec("wsh(sortedmulti($required,${parts.joinToString(",")}))", active = true, internal = chain == 1)
            }
            importAll(record.walletName, specs, ESCROW_LOOKAHEAD)
            val publicDesc = publicDescriptor(required, all, 0)
            val address = guard { rpc.call("getnewaddress", linkedMapOf("address_type" to "bech32"), record.walletName).toString() }
            val updated = record.copy(phase = EscrowPhase.Active, required = required, cosigners = keys, descriptor = publicDesc, currentAddress = address)
            store.save(store.load().map { if (it.id == id) updated else it })
            return updated
        } finally {
            lockQuietly(record.walletName)
        }
    }

    fun restore(name: String, publicDescriptor: String, labels: Map<String, String>, passphrase: String): EscrowRecord {
        val parsed = parseDescriptor(publicDescriptor)
        val master = masterFingerprint()
        val mine = parsed.keys.filter { it.startsWith("[$master/") }
        if (mine.size != 1) throw EscrowException(EscrowFailure.NotOurKey)
        val path = Regex("""^\[[0-9a-f]{8}/48h/${coinType}h/(\d+)h/2h]""").find(mine.single()) ?: throw EscrowException(EscrowFailure.NotOurKey)
        val account = path.groupValues[1].toInt()
        if (list().any { it.account == account }) throw EscrowException(EscrowFailure.DuplicateKey)
        val key = createKey(name, labels[mine.single()] ?: "Me", passphrase, account)
        val others = parsed.keys.filter { it != mine.single() }.map { Cosigner(labels[it].orEmpty(), it) }
        return activate(key.id, parsed.required, others, passphrase)
    }

    fun rescan(id: Int, passphrase: String) {
        val record = get(id)
        val pruneHeight = (guard { rpc.call("getblockchaininfo", emptyList<Any?>(), null) as Map<*, *> }["pruneheight"] as? Number)?.toLong() ?: 0L
        unlock(record.walletName, passphrase, RESCAN_UNLOCK_SECONDS)
        try {
            guard { rpc.call("rescanblockchain", listOf(pruneHeight), record.walletName) }
            val help = runCatching { rpc.call("help", listOf("rescanutxoset"), null)?.toString().orEmpty() }.getOrDefault("")
            if (help.isNotEmpty() && !help.contains("unknown command", ignoreCase = true)) {
                guard { rpc.call("rescanutxoset", emptyList<Any?>(), record.walletName) }
            }
        } finally {
            lockQuietly(record.walletName)
        }
    }

    fun address(id: Int): String {
        val record = get(id)
        if (record.phase != EscrowPhase.Active) throw EscrowException(EscrowFailure.NotActive)
        val receive = ReceiveWallet(rpc, object : AddressStore {
            override var current: String?
                get() = record.currentAddress
                set(value) {
                    store.save(store.load().map { if (it.id == id) it.copy(currentAddress = value) else it })
                }
        }, record.walletName)
        return receive.currentAddress()
    }

    fun reader(id: Int): WalletReader = WalletReader(rpc, get(id).walletName)

    fun walletNames(): List<String> = listOf(mainWallet) + list().filter { it.phase == EscrowPhase.Active }.map { it.walletName }

    fun backupText(id: Int): String {
        val record = get(id)
        val descriptor = record.descriptor ?: throw EscrowException(EscrowFailure.NotActive)
        return buildString {
            append("Elektron escrow backup\n")
            append("Name: ${record.name}\n")
            append("Threshold: ${record.required} of ${record.total}\n")
            append("Descriptor: $descriptor\n")
            append("Keys:\n")
            append("  ${record.myLabel} (this wallet): ${record.myKey}\n")
            record.cosigners.forEach { append("  ${it.label}: ${it.key}\n") }
        }
    }

    fun removeEscrow(id: Int, force: Boolean = false) {
        val record = get(id)
        if (!force && record.phase == EscrowPhase.Active) {
            val balances = WalletReader(rpc, record.walletName).balances()
            if (balances.trustedLep + balances.pendingLep + balances.immatureLep != 0L) throw EscrowException(EscrowFailure.HasFunds)
        }
        runCatching { rpc.call("unloadwallet", linkedMapOf("wallet_name" to record.walletName, "load_on_startup" to false), null) }
        // the keys must be gone before the entry is, a leftover folder keeps the entry so the removal can be repeated
        try {
            removeWalletFiles(record.walletName)
        } catch (e: Exception) {
            throw EscrowException(EscrowFailure.Unknown, e.message ?: "")
        }
        store.save(store.load().filter { it.id != id })
    }

    fun publicDescriptor(required: Int, keys: List<String>, chain: Int): String {
        val body = "wsh(sortedmulti($required,${keys.sortedBy(::keyCore).joinToString(",") { "$it/$chain/*" }}))"
        val info = guard { rpc.call("getdescriptorinfo", listOf(body), null) as Map<*, *> }
        return body + "#" + info["checksum"]
    }

    data class ParsedDescriptor(val required: Int, val keys: List<String>)

    fun parseDescriptor(text: String): ParsedDescriptor {
        val clean = text.trim().substringBefore('#').replace("'", "h").filterNot { it.isWhitespace() }
        val m = Regex("""^wsh\(sortedmulti\((\d+),(.+)\)\)$""").find(clean) ?: throw EscrowException(EscrowFailure.InvalidDescriptor)
        val required = m.groupValues[1].toInt()
        val keys = m.groupValues[2].split(",").map { part ->
            if (!part.endsWith("/0/*")) throw EscrowException(EscrowFailure.InvalidDescriptor)
            normalizeKey(part.removeSuffix("/0/*"))
        }
        keys.forEach { checkKey(it) }
        if (keys.size < 2 || required < 1 || required > keys.size || keys.size > MAX_KEYS) throw EscrowException(EscrowFailure.BadThreshold)
        return ParsedDescriptor(required, keys)
    }

    fun normalizeKey(raw: String): String = raw.trim().replace("'", "h").filterNot { it.isWhitespace() }

    private fun checkKey(key: String) {
        val m = KEY_FORMAT.matchEntire(key) ?: throw EscrowException(EscrowFailure.InvalidKey)
        val core = m.groupValues[2]
        if (Base58.decodeChecked(core)?.size != 78) throw EscrowException(EscrowFailure.InvalidKey)
        if (!core.startsWith(keyPrefix)) throw EscrowException(EscrowFailure.WrongNetwork)
    }

    private fun keyCore(key: String): String = key.substringAfter(']')

    private class ImportSpec(val descriptor: String, val active: Boolean, val internal: Boolean)

    private fun importAll(wallet: String, specs: List<ImportSpec>, lookahead: Int) {
        val requests = specs.map { spec ->
            val info = guard { rpc.call("getdescriptorinfo", listOf(spec.descriptor), null) as Map<*, *> }
            val request = linkedMapOf<String, Any?>(
                "desc" to spec.descriptor + "#" + info["checksum"],
                "timestamp" to "now",
                "active" to spec.active,
                "range" to listOf(0, lookahead),
            )
            if (spec.active) request["internal"] = spec.internal
            request
        }
        val results = guard { rpc.call("importdescriptors", listOf(requests), wallet) as List<*> }
        if (results.any { (it as Map<*, *>)["success"] != true }) throw EscrowException(EscrowFailure.ImportFailed)
    }

    private fun masterKey(passphrase: String): String {
        unlock(mainWallet, passphrase, KEY_UNLOCK_SECONDS)
        try {
            val keys = guard { rpc.call("gethdkeys", linkedMapOf("private" to true), mainWallet) as List<*> }
            return keys.map { it as Map<*, *> }.firstOrNull { it["has_private"] == true && it["xprv"] != null }?.get("xprv")?.toString()
                ?: throw EscrowException(EscrowFailure.WalletMissing)
        } finally {
            lockQuietly(mainWallet)
        }
    }

    private fun masterFingerprint(): String {
        val descriptors = guard { (rpc.call("listdescriptors", emptyList<Any?>(), mainWallet) as Map<*, *>)["descriptors"] as List<*> }
        return descriptors.map { (it as Map<*, *>)["desc"].toString() }.firstNotNullOfOrNull { Regex("""\[([0-9a-f]{8})/""").find(it)?.groupValues?.get(1) }
            ?: throw EscrowException(EscrowFailure.WalletMissing)
    }

    private fun unlock(wallet: String, passphrase: String, seconds: Int) {
        try {
            rpc.call("walletpassphrase", listOf(passphrase, seconds), wallet)
        } catch (e: RpcException) {
            throw EscrowException(if (e.code == WRONG_PASSPHRASE) EscrowFailure.WrongPassphrase else if (e.inWarmup) EscrowFailure.NodeNotReady else EscrowFailure.Unknown, e.message ?: "")
        }
    }

    private fun namesOnNode(): Set<String> = runCatching {
        ((rpc.call("listwalletdir", emptyList<Any?>(), null) as Map<*, *>)["wallets"] as List<*>).map { (it as Map<*, *>)["name"].toString() }.toSet()
    }.getOrDefault(emptySet())

    private fun lockQuietly(wallet: String) {
        runCatching { rpc.call("walletlock", emptyList<Any?>(), wallet) }
    }

    private fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: RpcException) {
        throw EscrowException(if (e.inWarmup) EscrowFailure.NodeNotReady else EscrowFailure.Unknown, e.message ?: "")
    }

    companion object {
        const val MAX_KEYS = 15
        const val MAX_NAME = 40
        const val MAX_LABEL = 24
        private const val SETUP_UNLOCK_SECONDS = 60
        private const val RESCAN_UNLOCK_SECONDS = 3600
        private const val KEY_UNLOCK_SECONDS = 10
        private const val ESCROW_LOOKAHEAD = 99
        private const val WRONG_PASSPHRASE = -14
        private val KEY_FROM_DESC = Regex("""wpkh\((\[[0-9a-f]{8}/[0-9h/]+][A-Za-z0-9]+)/0/\*\)""")
        private val KEY_FORMAT = Regex("""^(\[[0-9a-fA-F]{8}(?:/\d+h)*])?([xt]pub[1-9A-HJ-NP-Za-km-z]{100,115})$""")
    }
}
