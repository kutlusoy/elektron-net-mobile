package org.elektronnet.mobile.wallet

import org.elektronnet.mobile.node.RpcException

interface AddressStore {
    var current: String?
}

class ReceiveWallet(
    private val rpc: WalletRpc,
    private val store: AddressStore,
    private val walletName: String = WalletBootstrap.DEFAULT_WALLET,
) {
    fun currentAddress(): String {
        AddressReuse.ensure(rpc, walletName)
        store.current?.let { stored -> if (!hasReceived(stored)) return stored }
        return newAddress()
    }

    fun newAddress(): String {
        val address = rpc.call("getnewaddress", linkedMapOf("address_type" to "bech32"), walletName).toString()
        store.current = address
        return address
    }

    private fun hasReceived(address: String): Boolean = try {
        (rpc.call("getreceivedbyaddress", listOf(address, 0), walletName) as? Number)?.toDouble()?.let { it > 0.0 } ?: true
    } catch (e: RpcException) {
        true
    }
}
