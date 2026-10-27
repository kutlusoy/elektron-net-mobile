package org.elektronnet.mobile.wallet

import org.elektronnet.mobile.node.RpcException

enum class RemoveFailure { WrongPassphrase, NodeNotReady, Unknown }

class WalletRemover(private val rpc: WalletRpc, private val removeWalletFiles: (String) -> Unit) {
    /**
     * Unloads a wallet until the node reports it as not loaded, deletes its files and checks with the node that the wallet is
     * gone from the wallet folder. A wallet the node still lists is not reported as removed.
     */
    fun unloadAndDelete(walletName: String): Boolean {
        repeat(UNLOAD_TRIES) { attempt ->
            val loaded = runCatching { (rpc.call("listwallets", emptyList<Any?>(), null) as List<*>).map { it.toString() }.contains(walletName) }.getOrDefault(true)
            if (!loaded) return@repeat
            runCatching { rpc.call("unloadwallet", linkedMapOf("wallet_name" to walletName, "load_on_startup" to false), null) }
            if (attempt < UNLOAD_TRIES - 1) Thread.sleep(UNLOAD_WAIT_MS)
        }
        repeat(DELETE_TRIES) {
            if (runCatching { removeWalletFiles(walletName) }.isSuccess && existsOnNode(walletName) != true) return true
            Thread.sleep(UNLOAD_WAIT_MS)
        }
        return false
    }

    fun isUnused(walletName: String): Boolean = existsOnNode(walletName) == false || runCatching {
        val info = rpc.call("getwalletinfo", emptyList<Any?>(), walletName) as Map<*, *>
        val mine = (rpc.call("getbalances", emptyList<Any?>(), walletName) as Map<*, *>)["mine"] as Map<*, *>
        (info["txcount"] as? Number)?.toLong() == 0L && mine.values.all { (it as? Number)?.toDouble() == 0.0 }
    }.getOrDefault(false)

    fun existsOnNode(walletName: String): Boolean? = runCatching {
        ((rpc.call("listwalletdir", emptyList<Any?>(), null) as Map<*, *>)["wallets"] as List<*>).any { (it as Map<*, *>)["name"] == walletName }
    }.getOrNull()

    // a watch-only wallet holds no keys, so it can be removed without a passphrase even when it shows a balance
    fun remove(walletName: String, passphrase: String?, watchOnly: Boolean = false): RemoveFailure? {
        if (existsOnNode(walletName) == false) return if (unloadAndDelete(walletName)) null else RemoveFailure.Unknown
        if (passphrase == null) {
            if (!watchOnly && !isUnused(walletName)) return RemoveFailure.WrongPassphrase
        } else {
            try {
                rpc.call("walletpassphrase", listOf(passphrase, CHECK_SECONDS), walletName)
            } catch (e: RpcException) {
                return when {
                    e.code == WRONG_PASSPHRASE -> RemoveFailure.WrongPassphrase
                    e.inWarmup -> RemoveFailure.NodeNotReady
                    else -> RemoveFailure.Unknown
                }
            }
            WalletLock.lock(rpc, walletName, passphrase)
        }
        return if (unloadAndDelete(walletName)) null else RemoveFailure.Unknown
    }

    private companion object {
        const val CHECK_SECONDS = 5
        const val UNLOAD_TRIES = 10
        const val DELETE_TRIES = 3
        const val UNLOAD_WAIT_MS = 300L
        const val WRONG_PASSPHRASE = -14
    }
}
