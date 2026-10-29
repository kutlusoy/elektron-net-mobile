package org.elektronnet.mobile.wallet

import org.elektronnet.mobile.node.RpcException

class CoinRefresh(private val rpc: WalletRpc) {
    // Re-reads the wallet's coins from the current UTXO set. Needed after a restore and after a restart, because a node
    // before v4.0.6 loses coins that were recovered from the UTXO set when it reloads the wallet file.
    fun refresh(walletName: String): Boolean = try {
        rpc.call("rescanutxoset", emptyList<Any?>(), walletName)
        true
    } catch (e: RpcException) {
        false
    }

    companion object {
        private val VERSION = Regex("""(\d+)\.(\d+)\.(\d+)""")

        // v4.0.6 scans the UTXO set itself when it loads a wallet, a second scan from the app only holds the wallet lock longer
        fun nodeScansItself(subversion: String?): Boolean {
            val parts = subversion?.let { VERSION.find(it) }?.groupValues?.drop(1)?.map { it.toInt() } ?: return false
            return (parts[0] shl 20) + (parts[1] shl 10) + parts[2] >= (4 shl 20) + 6
        }

        fun nodeScansItself(rpc: WalletRpc): Boolean = try {
            nodeScansItself((rpc.call("getnetworkinfo", emptyList<Any?>(), null) as? Map<*, *>)?.get("subversion")?.toString())
        } catch (e: RpcException) {
            false
        }
    }
}
