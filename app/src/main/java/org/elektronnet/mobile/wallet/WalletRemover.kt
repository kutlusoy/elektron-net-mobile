package org.elektronnet.mobile.wallet

import org.elektronnet.mobile.node.RpcException

enum class RemoveFailure { WrongPassphrase, NodeNotReady, Unknown }

class WalletRemover(private val rpc: WalletRpc, private val removeWalletFiles: (String) -> Unit) {
    fun remove(walletName: String, passphrase: String): RemoveFailure? {
        try {
            rpc.call("walletpassphrase", listOf(passphrase, CHECK_SECONDS), walletName)
        } catch (e: RpcException) {
            return when {
                e.code == WRONG_PASSPHRASE -> RemoveFailure.WrongPassphrase
                e.inWarmup -> RemoveFailure.NodeNotReady
                else -> RemoveFailure.Unknown
            }
        }
        runCatching { rpc.call("walletlock", emptyList<Any?>(), walletName) }
        try {
            rpc.call("unloadwallet", linkedMapOf("wallet_name" to walletName, "load_on_startup" to false), null)
        } catch (e: RpcException) {
            return if (e.inWarmup) RemoveFailure.NodeNotReady else RemoveFailure.Unknown
        }
        return if (runCatching { removeWalletFiles(walletName) }.isSuccess) null else RemoveFailure.Unknown
    }

    private companion object {
        const val CHECK_SECONDS = 5
        const val WRONG_PASSPHRASE = -14
    }
}
