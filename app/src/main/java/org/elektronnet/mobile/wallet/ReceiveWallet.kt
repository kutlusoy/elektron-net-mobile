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
        store.current?.let { stored -> if (!hasReceived(stored) || fixedAddress() == stored) return stored }
        return newAddress()
    }

    fun newAddress(): String {
        val address = try {
            rpc.call("getnewaddress", linkedMapOf("address_type" to "bech32"), walletName).toString()
        } catch (e: RpcException) {
            // a watch-only wallet made from one address has nothing to derive, it shows that address
            fixedAddress() ?: throw e
        }
        store.current = address
        return address
    }

    private fun fixedAddress(): String? = runCatching {
        val descriptors = ((rpc.call("listdescriptors", emptyList<Any?>(), walletName) as Map<*, *>)["descriptors"] as List<*>)
            .mapNotNull { (it as? Map<*, *>)?.get("desc")?.toString() }

        // 1. Check if there's an explicit addr(...) descriptor (watch-only address)
        descriptors.firstNotNullOfOrNull { desc ->
            Regex("""^addr\(([^)]+)\)""").find(desc)?.groupValues?.get(1)
        } ?: run {
            // 2. Check for single-key non-ranged descriptors (e.g. wpkh, pkh, sh without "/*")
            val nonRanged = descriptors.filter { !it.contains("/*") }
            val targetDesc = nonRanged.firstOrNull { it.startsWith("wpkh(") } ?: nonRanged.firstOrNull()
            targetDesc?.let { desc ->
                (rpc.call("deriveaddresses", listOf(desc), walletName) as? List<*>)?.firstOrNull()?.toString()
            }
        }
    }.getOrNull()

    private fun hasReceived(address: String): Boolean = try {
        (rpc.call("getreceivedbyaddress", listOf(address, 0), walletName) as? Number)?.toDouble()?.let { it > 0.0 } ?: false
    } catch (e: RpcException) {
        false
    }
}
