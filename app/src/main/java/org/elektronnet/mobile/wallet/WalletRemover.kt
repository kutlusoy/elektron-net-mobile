package org.elektronnet.mobile.wallet

import java.io.IOException
import org.elektronnet.mobile.node.RpcException

enum class RemoveFailure { WrongPassphrase, NodeNotReady, Busy, Unknown }

class WalletRemover(private val rpc: WalletRpc, private val removeWalletFiles: (String) -> Unit) {
    internal var unloadWaitTotalMs: Long = UNLOAD_WAIT_TOTAL_MS

    @Volatile
    private var stillLoaded = false

    // the last error the node or the file removal reported, for the message under the dialog
    @Volatile
    var lastError: String? = null
        private set

    /**
     * Unloads a wallet until the node reports it as not loaded, deletes its files and checks with the node that the wallet is
     * gone from the wallet folder. A wallet the node still lists is not reported as removed. A node that is busy with the
     * wallet (a UTXO scan holds its lock) is given time to finish.
     */
    fun unloadAndDelete(walletName: String): Boolean {
        stillLoaded = false
        val deadline = System.currentTimeMillis() + unloadWaitTotalMs
        while (isLoaded(walletName)) {
            if (System.currentTimeMillis() > deadline) {
                stillLoaded = true
                return false
            }
            runCatching { rpc.call("unloadwallet", linkedMapOf("wallet_name" to walletName, "load_on_startup" to false), null) }
                .onFailure { lastError = "unloadwallet: ${it.message}" }
            Thread.sleep(UNLOAD_WAIT_MS)
        }
        repeat(DELETE_TRIES) {
            val deleted = runCatching { removeWalletFiles(walletName) }.onFailure { lastError = "delete: ${it.message}" }.isSuccess
            if (deleted && existsOnNode(walletName) != true) return true
            if (deleted) lastError = "the node still lists the wallet"
            Thread.sleep(UNLOAD_WAIT_MS)
        }
        return false
    }

    // true when the node cannot tell, so the wallet is treated as still loaded
    private fun isLoaded(walletName: String): Boolean =
        runCatching { (rpc.call("listwallets", emptyList<Any?>(), null) as List<*>).map { it.toString() }.contains(walletName) }.getOrDefault(true)

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
        if (existsOnNode(walletName) == false) return if (unloadAndDelete(walletName)) null else failure()
        if (passphrase == null) {
            if (!watchOnly && !isUnused(walletName)) return RemoveFailure.WrongPassphrase
        } else {
            var encrypted = true
            try {
                rpc.call("walletpassphrase", listOf(passphrase, CHECK_SECONDS), walletName)
            } catch (e: RpcException) {
                lastError = "walletpassphrase: ${e.message} (${e.code})"
                when {
                    e.code == WRONG_PASSPHRASE -> return RemoveFailure.WrongPassphrase
                    e.inWarmup -> return RemoveFailure.NodeNotReady
                    // a wallet without a passphrase has nothing to check, the confirmation of the dialog is all there is
                    e.code == NOT_ENCRYPTED -> encrypted = false
                    else -> return RemoveFailure.Unknown
                }
            } catch (e: IOException) {
                lastError = "walletpassphrase: ${e.message}"
                return RemoveFailure.Busy
            }
            if (encrypted) WalletLock.lock(rpc, walletName, passphrase)
        }
        return if (unloadAndDelete(walletName)) null else failure()
    }

    private fun failure(): RemoveFailure = if (stillLoaded) RemoveFailure.Busy else RemoveFailure.Unknown

    private companion object {
        const val CHECK_SECONDS = 5
        const val UNLOAD_WAIT_TOTAL_MS = 120_000L
        const val DELETE_TRIES = 3
        const val UNLOAD_WAIT_MS = 300L
        const val WRONG_PASSPHRASE = -14
        const val NOT_ENCRYPTED = -15
    }
}
