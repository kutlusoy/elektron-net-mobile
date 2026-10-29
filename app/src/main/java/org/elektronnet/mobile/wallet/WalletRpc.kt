package org.elektronnet.mobile.wallet

import org.elektronnet.mobile.node.RpcClient
import org.elektronnet.mobile.node.RpcException

fun interface WalletRpc {
    fun call(method: String, params: Any, wallet: String?): Any?
}

// wallets that are being removed: calls for them must not load them again, polling of the balance would undo the removal
object WalletLoadGuard {
    private val blocked: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    fun block(name: String) { blocked.add(name) }

    fun release(name: String) { blocked.remove(name) }

    fun allows(name: String): Boolean = name !in blocked
}

private const val WALLET_NOT_FOUND = -18
private const val LOAD_TIMEOUT_MS = 120_000

// A wallet that is on the node's disk but not loaded (for example after an interrupted setup) is loaded on first use
// autoLoad = false for background work, which must not bring back a wallet that is being removed
fun RpcClient.asWalletRpc(timeoutMs: Int = 30_000, autoLoad: Boolean = true) = WalletRpc { method, params, wallet ->
    try {
        call(method, params, wallet, timeoutMs)
    } catch (e: RpcException) {
        if (autoLoad && wallet != null && WalletLoadGuard.allows(wallet) && e.code == WALLET_NOT_FOUND && loadListed(wallet)) call(method, params, wallet, timeoutMs) else throw e
    }
}

private fun RpcClient.loadListed(wallet: String): Boolean = try {
    call("loadwallet", linkedMapOf("filename" to wallet, "load_on_startup" to true), null, LOAD_TIMEOUT_MS)
    true
} catch (e: RpcException) {
    false
}
