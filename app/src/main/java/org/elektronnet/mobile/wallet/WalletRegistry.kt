package org.elektronnet.mobile.wallet

import org.elektronnet.mobile.node.Json

data class OwnWallet(
    val name: String,
    val label: String,
    val watchOnly: Boolean = false,
    val coin: String = inferCoin(name, label),
) {
    companion object {
        fun inferCoin(name: String, label: String, explicit: String? = null): String {
            if (!explicit.isNullOrBlank()) return explicit.uppercase()
            return if (name.contains("btc", ignoreCase = true) || label.contains("btc", ignoreCase = true)) "BTC" else "ELEK"
        }
    }
}

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
            (Json.parse(text) as List<*>).map {
                val m = it as Map<*, *>
                val name = m["name"].toString()
                val label = m["label"].toString()
                val watch = m["watch"] == true
                val coin = OwnWallet.inferCoin(name, label, m["coin"]?.toString())
                OwnWallet(name, label, watch, coin)
            }
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
        val found = all.firstOrNull { it.name == store.active }
        if (found != null) return found
        val fallback = all.firstOrNull() ?: OwnWallet(WalletBootstrap.DEFAULT_WALLET, "")
        store.active = fallback.name
        return fallback
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
    fun add(name: String, label: String, watchOnly: Boolean = false, coin: String? = null): OwnWallet {
        val entry = OwnWallet(name, label.trim(), watchOnly, OwnWallet.inferCoin(name, label, coin))
        save(wallets().filter { it.name != name } + entry)
        return entry
    }

    @Synchronized
    fun changeName(old: String, new: String) {
        save(wallets().map { if (it.name == old) it.copy(name = new) else it })
        if (store.active == old) store.active = new
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
        store.json = Json.stringify(list.map {
            linkedMapOf<String, Any?>(
                "name" to it.name,
                "label" to it.label,
                "coin" to it.coin,
            ).also { m -> if (it.watchOnly) m["watch"] = true }
        })
    }
}
