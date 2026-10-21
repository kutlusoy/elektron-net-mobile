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
}
