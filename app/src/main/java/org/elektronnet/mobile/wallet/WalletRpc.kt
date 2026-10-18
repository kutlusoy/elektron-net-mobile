package org.elektronnet.mobile.wallet

import org.elektronnet.mobile.node.RpcClient

fun interface WalletRpc {
    fun call(method: String, params: Any, wallet: String?): Any?
}

fun RpcClient.asWalletRpc() = WalletRpc { method, params, wallet -> call(method, params, wallet) }
