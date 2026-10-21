package org.elektronnet.mobile.wallet

import org.elektronnet.mobile.node.RpcClient
import org.elektronnet.mobile.node.RpcException

fun interface WalletRpc {
    fun call(method: String, params: Any, wallet: String?): Any?
}

private const val WALLET_NOT_FOUND = -18
private const val LOAD_TIMEOUT_MS = 120_000

// A wallet that is on the node's disk but not loaded (for example after an interrupted setup) is loaded on first use
fun RpcClient.asWalletRpc(timeoutMs: Int = 30_000) = WalletRpc { method, params, wallet ->
    try {
        call(method, params, wallet, timeoutMs)
    } catch (e: RpcException) {
        if (wallet != null && e.code == WALLET_NOT_FOUND && loadListed(wallet)) call(method, params, wallet, timeoutMs) else throw e
    }
}

private fun RpcClient.loadListed(wallet: String): Boolean = try {
    call("loadwallet", linkedMapOf("filename" to wallet, "load_on_startup" to true), null, LOAD_TIMEOUT_MS)
    true
} catch (e: RpcException) {
    false
}
