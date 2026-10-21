package org.elektronnet.mobile.wallet

class WalletReconciler(
    private val rpc: WalletRpc,
    private val registry: WalletRegistry,
    private val protectedNames: () -> Set<String> = { emptySet() },
) {
    data class Result(val dropped: List<String>, val adopted: List<String>, val loaded: List<String>)

    fun namesOnDisk(): List<String>? = runCatching {
        ((rpc.call("listwalletdir", emptyList<Any?>(), null) as Map<*, *>)["wallets"] as List<*>).map { (it as Map<*, *>)["name"].toString() }
    }.getOrNull()

    // adopt = false only drops entries without a node wallet and loads listed wallets, adopt = true also lists node wallets that are missing
    fun reconcile(adopt: Boolean = true): Result? {
        val onDisk = namesOnDisk() ?: return null
        val keep = protectedNames()
        val dropped = registry.wallets().map { it.name }.filter { it !in onDisk && it !in keep }
        dropped.forEach { registry.remove(it) }
        val known = registry.wallets().map { it.name }.toSet()
        val adopted = if (adopt) onDisk.filter { OWN_NAME.matches(it) && it !in known } else emptyList()
        adopted.forEach { registry.add(it, "") }
        val loadedNow = runCatching { (rpc.call("listwallets", emptyList<Any?>(), null) as List<*>).map { it.toString() }.toSet() }.getOrDefault(emptySet())
        val loaded = registry.wallets().map { it.name }.filter { it in onDisk && it !in loadedNow }.filter {
            runCatching { rpc.call("loadwallet", linkedMapOf("filename" to it, "load_on_startup" to true), null) }.isSuccess
        }
        return Result(dropped, adopted, loaded)
    }

    companion object {
        val OWN_NAME = Regex("^${WalletBootstrap.DEFAULT_WALLET}(_[0-9]+)?$")
    }
}
