package org.elektronnet.mobile.wallet

import java.io.File
import org.elektronnet.mobile.node.RpcException

enum class RestoreFailure { InvalidFile, NameTaken, WeakPassphrase, NodeNotReady, Unknown }

sealed interface RestoreResult {
    data class Restored(val walletName: String, val encrypted: Boolean) : RestoreResult
    data class Failed(val reason: RestoreFailure, val detail: String = "") : RestoreResult
}

class WalletRestore(
    private val rpc: WalletRpc,
    private val removeWalletFiles: (String) -> Unit,
) {
    fun restore(file: File, walletName: String): RestoreResult {
        if (!file.isFile || file.length() == 0L) return RestoreResult.Failed(RestoreFailure.InvalidFile)
        try {
            val existing = ((rpc.call("listwalletdir", emptyList<Any?>(), null) as Map<*, *>)["wallets"] as? List<*>).orEmpty().map { (it as Map<*, *>)["name"].toString() }
            if (walletName in existing) return RestoreResult.Failed(RestoreFailure.NameTaken)
            rpc.call(
                "restorewallet",
                linkedMapOf("wallet_name" to walletName, "backup_file" to file.absolutePath, "load_on_startup" to true),
                null,
            )
            val info = rpc.call("getwalletinfo", emptyList<Any?>(), walletName) as Map<*, *>
            return RestoreResult.Restored(walletName, info.containsKey("unlocked_until"))
        } catch (e: RpcException) {
            runCatching { rpc.call("unloadwallet", listOf(walletName), null) }
            runCatching { removeWalletFiles(walletName) }
            val reason = if (e.inWarmup) RestoreFailure.NodeNotReady else RestoreFailure.InvalidFile
            return RestoreResult.Failed(reason, e.message ?: "")
        }
    }

    fun protect(walletName: String, passphrase: String): RestoreFailure? {
        if (!PassphraseCheck.acceptable(passphrase)) return RestoreFailure.WeakPassphrase
        return try {
            rpc.call("encryptwallet", listOf(passphrase), walletName)
            null
        } catch (e: RpcException) {
            if (e.inWarmup) RestoreFailure.NodeNotReady else RestoreFailure.Unknown
        }
    }

    fun discard(walletName: String) {
        runCatching { rpc.call("unloadwallet", listOf(walletName), null) }
        runCatching { removeWalletFiles(walletName) }
    }
}
