package org.elektronnet.mobile.wallet

import org.elektronnet.mobile.node.Json

data class OwnWallet(val name: String, val label: String)

interface WalletRegistryStore {
    var json: String?
    var active: String?
}

class MemoryWalletRegistryStore : WalletRegistryStore {
    override var json: String? = null
    override var active: String? = null
}

class WalletRegistry(private val store: WalletRegistryStore) {
    @Synchronized
    fun wallets(): List<OwnWallet> {
        val text = store.json ?: return emptyList()
        return runCatching {
            (Json.parse(text) as List<*>).map { OwnWallet((it as Map<*, *>)["name"].toString(), it["label"].toString()) }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun ensureDefault(): OwnWallet {
        val existing = wallets()
        existing.firstOrNull { it.name == WalletBootstrap.DEFAULT_WALLET }?.let { return it }
        val entry = OwnWallet(WalletBootstrap.DEFAULT_WALLET, "")
        save(listOf(entry) + existing)
        return entry
    }

    @Synchronized
    fun active(): OwnWallet {
        val all = wallets()
        return all.firstOrNull { it.name == store.active } ?: all.firstOrNull() ?: OwnWallet(WalletBootstrap.DEFAULT_WALLET, "")
    }

    @Synchronized
    fun setActive(name: String) {
        if (wallets().any { it.name == name }) store.active = name
    }

    @Synchronized
    fun nextName(alsoTaken: Set<String> = emptySet()): String {
        val taken = wallets().map { it.name }.toSet() + alsoTaken
        if (WalletBootstrap.DEFAULT_WALLET !in taken) return WalletBootstrap.DEFAULT_WALLET
        var n = 2
        while ("${WalletBootstrap.DEFAULT_WALLET}_$n" in taken) n++
        return "${WalletBootstrap.DEFAULT_WALLET}_$n"
    }

    @Synchronized
    fun add(name: String, label: String): OwnWallet {
        val entry = OwnWallet(name, label.trim())
        save(wallets().filter { it.name != name } + entry)
        return entry
    }

    @Synchronized
    fun rename(name: String, label: String) {
        save(wallets().map { if (it.name == name) it.copy(label = label.trim()) else it })
    }

    @Synchronized
    fun remove(name: String) {
        save(wallets().filter { it.name != name })
        if (store.active == name) store.active = null
    }

    private fun save(list: List<OwnWallet>) {
        store.json = Json.stringify(list.map { linkedMapOf("name" to it.name, "label" to it.label) })
    }
}
