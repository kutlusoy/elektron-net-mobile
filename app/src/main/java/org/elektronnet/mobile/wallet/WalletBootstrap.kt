package org.elektronnet.mobile.wallet

import org.elektronnet.mobile.node.RpcException

sealed class BootstrapResult {
    data class Created(val walletName: String) : BootstrapResult()
    data class Failed(val reason: BootstrapFailure, val detail: String) : BootstrapResult()
}

enum class BootstrapFailure { InvalidPhrase, WeakPassphrase, WrongPassphrase, NodeNotReady, WalletExists, ImportFailed, RecoveryUnsupported, Unknown }

sealed interface RescanOutcome {
    data object Full : RescanOutcome
    data object Partial : RescanOutcome
    data class Failed(val reason: BootstrapFailure) : RescanOutcome
}

class WalletBootstrap(
    private val rpc: WalletRpc,
    private val bip39: Bip39,
    private val testnet: Boolean,
    private val legacy: Boolean = !testnet,
    private val removeWalletFiles: (String) -> Unit,
    private val walletName: String = DEFAULT_WALLET,
) {
    fun create(mnemonic: List<String>, passphrase: String): BootstrapResult =
        run(mnemonic, passphrase, recover = false)

    fun recover(mnemonic: List<String>, passphrase: String): BootstrapResult = run(mnemonic, passphrase, recover = true)

    // an empty passphrase stands for a watch-only wallet, which has no keys to unlock
    fun rescan(passphrase: String): RescanOutcome {
        var unlocked = false
        try {
            if (passphrase.isNotEmpty()) {
                try {
                    rpc.call("walletpassphrase", listOf(passphrase, RECOVERY_UNLOCK_SECONDS), walletName)
                    unlocked = true
                } catch (e: RpcException) {
                    return RescanOutcome.Failed(if (e.code == WRONG_PASSPHRASE) BootstrapFailure.WrongPassphrase else if (e.inWarmup) BootstrapFailure.NodeNotReady else BootstrapFailure.Unknown)
                }
            }
            val pruneHeight = (rpc.call("getblockchaininfo", emptyList<Any?>(), null) as Map<*, *>)["pruneheight"] as? Long ?: 0L
            rpc.call("rescanblockchain", listOf(pruneHeight), walletName)
            if (!supportsUtxoRescan()) return RescanOutcome.Partial
            rpc.call("rescanutxoset", emptyList<Any?>(), walletName)
            return RescanOutcome.Full
        } catch (e: RpcException) {
            return RescanOutcome.Failed(if (e.inWarmup) BootstrapFailure.NodeNotReady else BootstrapFailure.Unknown)
        } finally {
            if (unlocked) WalletLock.lock(rpc, walletName, passphrase)
        }
    }

    // a wallet without private keys: it can show balance and history and prepare payments, but only another device can sign them
    fun createWatchOnly(source: WatchSource): BootstrapResult {
        var created = false
        try {
            rpc.call(
                "createwallet",
                linkedMapOf(
                    "wallet_name" to walletName,
                    "disable_private_keys" to true,
                    "blank" to true,
                    "descriptors" to true,
                    "avoid_reuse" to true,
                    "load_on_startup" to true,
                ),
                null,
            )
            created = true
            val wanted = when (source) {
                is WatchSource.Address -> listOf(WatchDescriptor("addr(${source.address})", internal = false))
                is WatchSource.Descriptors -> source.items
            }
            val requests = wanted.map { item ->
                val info = rpc.call("getdescriptorinfo", listOf(item.descriptor), null) as Map<*, *>
                if (info["hasprivatekeys"] == true) throw ImportException("private keys")
                val request = linkedMapOf<String, Any?>("desc" to info["descriptor"].toString(), "timestamp" to "now")
                if (info["isrange"] == true) {
                    request["range"] = listOf(0, RECOVERY_LOOKAHEAD)
                    request["active"] = true
                    request["internal"] = item.internal
                }
                request
            }
            val results = rpc.call("importdescriptors", listOf(requests), walletName) as List<*>
            val failure = results.firstOrNull { (it as Map<*, *>)["success"] != true }
            if (failure != null) {
                val error = (failure as Map<*, *>)["error"] as? Map<*, *>
                throw ImportException(error?.get("message")?.toString() ?: "import failed")
            }
            return BootstrapResult.Created(walletName)
        } catch (e: ImportException) {
            abort(created)
            return BootstrapResult.Failed(BootstrapFailure.ImportFailed, e.message ?: "")
        } catch (e: RpcException) {
            abort(created && e.code != WALLET_EXISTS)
            val reason = when {
                e.inWarmup -> BootstrapFailure.NodeNotReady
                e.code == WALLET_EXISTS -> BootstrapFailure.WalletExists
                else -> BootstrapFailure.ImportFailed
            }
            return BootstrapResult.Failed(reason, e.message ?: "")
        }
    }

    fun supportsUtxoRescan(): Boolean {
        val help = rpc.call("help", listOf("rescanutxoset"), null)?.toString() ?: return false
        return !help.contains("help: unknown command", ignoreCase = true)
    }

    private fun run(mnemonic: List<String>, passphrase: String, recover: Boolean): BootstrapResult {
        if (!bip39.validate(mnemonic)) return BootstrapResult.Failed(BootstrapFailure.InvalidPhrase, "")
        if (!PassphraseCheck.acceptable(passphrase)) return BootstrapResult.Failed(BootstrapFailure.WeakPassphrase, "")
        var created = false
        var unlocked = false
        try {
            rpc.call(
                "createwallet",
                linkedMapOf(
                    "wallet_name" to walletName,
                    "blank" to true,
                    "passphrase" to passphrase,
                    "descriptors" to true,
                    "avoid_reuse" to true,
                    "load_on_startup" to true,
                ),
                null,
            )
            created = true
            rpc.call("walletpassphrase", listOf(passphrase, if (recover) RECOVERY_UNLOCK_SECONDS else CREATE_UNLOCK_SECONDS), walletName)
            unlocked = true
            val master = Bip32.masterExtendedKey(Bip39.toSeed(mnemonic), testnet)
            val requests = Descriptors.forWallet(master, testnet, legacy).map { spec ->
                val info = rpc.call("getdescriptorinfo", listOf(spec.descriptor), null) as Map<*, *>
                val request = linkedMapOf<String, Any?>(
                    "desc" to spec.descriptor + "#" + info["checksum"],
                    "timestamp" to "now",
                    "active" to spec.active,
                    "range" to listOf(0, if (recover) RECOVERY_LOOKAHEAD else CREATE_LOOKAHEAD),
                )
                if (spec.active) request["internal"] = spec.internal
                request
            }
            val results = rpc.call("importdescriptors", listOf(requests), walletName) as List<*>
            val failure = results.firstOrNull { (it as Map<*, *>)["success"] != true }
            if (failure != null) {
                val error = (failure as Map<*, *>)["error"] as? Map<*, *>
                throw ImportException(error?.get("message")?.toString() ?: "import failed")
            }
            return BootstrapResult.Created(walletName)
        } catch (e: ImportException) {
            abort(created)
            return BootstrapResult.Failed(BootstrapFailure.ImportFailed, e.message ?: "")
        } catch (e: RpcException) {
            abort(created && e.code != WALLET_EXISTS)
            val reason = when {
                e.inWarmup -> BootstrapFailure.NodeNotReady
                e.code == WALLET_EXISTS -> BootstrapFailure.WalletExists
                else -> BootstrapFailure.Unknown
            }
            return BootstrapResult.Failed(reason, e.message ?: "")
        } finally {
            if (unlocked) WalletLock.lock(rpc, walletName, passphrase)
        }
    }

    private fun abort(created: Boolean) {
        if (!created) return
        runCatching { rpc.call("unloadwallet", listOf(walletName), null) }
        runCatching { removeWalletFiles(walletName) }
    }

    private class ImportException(message: String) : Exception(message)

    companion object {
        const val DEFAULT_WALLET = "elektron"
        const val WALLET_EXISTS = -4
        const val WRONG_PASSPHRASE = -14
        const val CREATE_UNLOCK_SECONDS = 60
        const val RECOVERY_UNLOCK_SECONDS = 1200
        const val CREATE_LOOKAHEAD = 199
        const val RECOVERY_LOOKAHEAD = 999
    }
}
