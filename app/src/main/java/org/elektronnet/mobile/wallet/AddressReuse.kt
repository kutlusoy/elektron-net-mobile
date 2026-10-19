package org.elektronnet.mobile.wallet

import org.elektronnet.mobile.node.RpcException

object AddressReuse {
    private const val FLAG = "avoid_reuse"

    fun ensure(rpc: WalletRpc, walletName: String): Boolean = try {
        val info = rpc.call("getwalletinfo", emptyList<Any?>(), walletName) as? Map<*, *>
        if (info?.get(FLAG) == true) {
            true
        } else {
            rpc.call("setwalletflag", listOf(FLAG, true), walletName)
            true
        }
    } catch (e: RpcException) {
        false
    }
}
