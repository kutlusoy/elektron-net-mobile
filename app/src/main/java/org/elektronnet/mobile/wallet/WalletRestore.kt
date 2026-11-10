package org.elektronnet.mobile.wallet

import java.io.File
import org.elektronnet.mobile.node.RpcException

enum class RestoreFailure {
    InvalidFile,
    NameTaken,
    WeakPassphrase,
    NodeNotReady,
    NotRemoved,
    Unknown,
    IncorrectPassphrase,
}

sealed interface RestoreResult {
    data class Restored(val walletName: String, val encrypted: Boolean) : RestoreResult
    data class Failed(val reason: RestoreFailure, val detail: String = "") : RestoreResult
    data class NeedsPassphrase(val file: File, val walletName: String) : RestoreResult
}

class WalletRestore(
    private val rpc: WalletRpc,
    private val testnet: Boolean,
    private val removeWalletFiles: (String) -> Unit,
) {
    constructor(rpc: WalletRpc, removeWalletFiles: (String) -> Unit) : this(rpc, false, removeWalletFiles)
    fun restore(file: File, walletName: String, passphrase: String? = null): RestoreResult {
        if (!file.isFile || file.length() == 0L) return RestoreResult.Failed(RestoreFailure.InvalidFile)
        try {
            val existing = ((rpc.call("listwalletdir", emptyList<Any?>(), null) as Map<*, *>)["wallets"] as? List<*>).orEmpty().map { (it as Map<*, *>)["name"].toString() }
            if (walletName in existing) return RestoreResult.Failed(RestoreFailure.NameTaken)

            val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
            if (ElectrumWalletParser.isElectrumWallet(raw)) {
                return restoreElectrum(file, raw, walletName, passphrase)
            }

            rpc.call(
                "restorewallet",
                linkedMapOf("wallet_name" to walletName, "backup_file" to file.absolutePath, "load_on_startup" to true),
                null,
            )
            val info = rpc.call("getwalletinfo", emptyList<Any?>(), walletName) as Map<*, *>
            return RestoreResult.Restored(walletName, info.containsKey("unlocked_until"))
        } catch (e: RpcException) {
            WalletRemover(rpc, removeWalletFiles).unloadAndDelete(walletName)
            val reason = if (e.inWarmup) RestoreFailure.NodeNotReady else RestoreFailure.InvalidFile
            return RestoreResult.Failed(reason, e.message ?: "")
        }
    }

    private fun restoreElectrum(file: File, raw: String, walletName: String, passphrase: String?): RestoreResult {
        if (ElectrumWalletParser.isElectrumEncrypted(raw) && passphrase == null) {
            return RestoreResult.NeedsPassphrase(file, walletName)
        }
        val content = try {
            ElectrumWalletParser.parse(raw, passphrase.orEmpty())
        } catch (e: InvalidElectrumPasswordException) {
            return RestoreResult.Failed(RestoreFailure.IncorrectPassphrase, e.message ?: "")
        } catch (e: Exception) {
            return RestoreResult.Failed(RestoreFailure.InvalidFile, e.message ?: "")
        }

        var created = false
        try {
            val isEncrypted = !passphrase.isNullOrEmpty()
            val createParams = linkedMapOf<String, Any?>(
                "wallet_name" to walletName,
                "blank" to true,
                "descriptors" to true,
                "avoid_reuse" to true,
                "load_on_startup" to true,
            )
            if (isEncrypted) {
                createParams["passphrase"] = passphrase
            }
            rpc.call("createwallet", createParams, null)
            created = true

            if (isEncrypted) {
                rpc.call("walletpassphrase", listOf(passphrase, 300), walletName)
            }

            val specs = when (content) {
                is ElectrumWalletContent.Seed -> {
                    val seedBytes = ElectrumSeed.toSeed(content.words, content.passphrase)
                    val master = Bip32.masterExtendedKey(seedBytes, testnet)
                    Descriptors.forElectrumSeed(master, content.type)
                }
                is ElectrumWalletContent.MasterKey -> {
                    Descriptors.forElectrumSeed(content.xprv, content.type)
                }
                is ElectrumWalletContent.ImportedKeys -> {
                    val list = mutableListOf<DescriptorSpec>()
                    for (wif in content.wifKeys) {
                        list += DescriptorSpec("wpkh($wif)", active = false, internal = false, legacy = false)
                        list += DescriptorSpec("pkh($wif)", active = false, internal = false, legacy = true, signingOnly = true)
                    }
                    list
                }
            }

            val requests = specs.map { item ->
                val info = rpc.call("getdescriptorinfo", listOf(item.descriptor), null) as Map<*, *>
                val descriptorWithChecksum = info["descriptor"].toString()
                val request = linkedMapOf<String, Any?>(
                    "desc" to descriptorWithChecksum,
                    "timestamp" to 0,
                )
                if (info["isrange"] == true) {
                    request["range"] = listOf(0, WalletBootstrap.RECOVERY_LOOKAHEAD)
                    request["active"] = item.active
                    request["internal"] = item.internal
                }
                request
            }

            val results = rpc.call("importdescriptors", listOf(requests), walletName) as List<*>
            val failure = results.firstOrNull { (it as Map<*, *>)["success"] != true }
            if (failure != null) {
                val error = (failure as Map<*, *>)["error"] as? Map<*, *>
                throw IllegalStateException(error?.get("message")?.toString() ?: "import failed")
            }

            val pruneHeight = runCatching {
                ((rpc.call("getblockchaininfo", emptyList<Any?>(), null) as? Map<*, *>)?.get("pruneheight") as? Number)?.toLong() ?: 0L
            }.getOrDefault(0L)
            runCatching { rpc.call("rescanblockchain", listOf(pruneHeight), walletName) }
            runCatching { rpc.call("rescanutxoset", emptyList<Any?>(), walletName) }

            return RestoreResult.Restored(walletName, isEncrypted)
        } catch (e: Exception) {
            if (created) WalletRemover(rpc, removeWalletFiles).unloadAndDelete(walletName)
            return RestoreResult.Failed(RestoreFailure.InvalidFile, e.message ?: "")
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

    // true when the wallet is gone from the node and from the disk
    fun discard(walletName: String): Boolean = WalletRemover(rpc, removeWalletFiles).unloadAndDelete(walletName)
}
