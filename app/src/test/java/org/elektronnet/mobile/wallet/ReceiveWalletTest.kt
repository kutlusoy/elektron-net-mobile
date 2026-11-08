package org.elektronnet.mobile.wallet

import org.elektronnet.mobile.node.RpcException
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiveWalletTest {
    private class MemoryAddressStore : AddressStore {
        override var current: String? = null
    }

    private class MockWalletRpc(
        private val handler: (method: String, params: Any) -> Any?,
    ) : WalletRpc {
        override fun call(method: String, params: Any, wallet: String?): Any? =
            handler(method, params)
    }

    @Test
    fun derivesAddressFromSingleKeyDescriptorWhenNoKeypoolAvailable() {
        val store = MemoryAddressStore()
        val rpc = MockWalletRpc { method, params ->
            when (method) {
                "getwalletinfo" -> mapOf("avoid_reuse" to true)
                "getnewaddress" -> throw RpcException(-4, "Error: This wallet has no available keys")
                "listdescriptors" -> mapOf(
                    "descriptors" to listOf(
                        mapOf(
                            "desc" to "pkh(031b84c5567b126440995d3ed5aaba0565d71e1834604819ff9c17f5e9d5dd078f)#n5kjh793",
                            "active" to false,
                        ),
                        mapOf(
                            "desc" to "wpkh(031b84c5567b126440995d3ed5aaba0565d71e1834604819ff9c17f5e9d5dd078f)#c94napag",
                            "active" to false,
                        ),
                    ),
                )
                "deriveaddresses" -> listOf("be1q0xcqpzrky6eff2g52qdye53xkk9jxkvr6p27vz")
                "getreceivedbyaddress" -> 0.0
                else -> null
            }
        }
        val wallet = ReceiveWallet(rpc, store, "elektron")
        val address = wallet.currentAddress()

        assertEquals("be1q0xcqpzrky6eff2g52qdye53xkk9jxkvr6p27vz", address)
        assertEquals("be1q0xcqpzrky6eff2g52qdye53xkk9jxkvr6p27vz", store.current)
    }

    @Test
    fun reusesCurrentAddressWhenRpcFailsDuringScan() {
        val store = MemoryAddressStore()
        store.current = "be1q0xcqpzrky6eff2g52qdye53xkk9jxkvr6p27vz"

        val rpc = MockWalletRpc { method, _ ->
            when (method) {
                "getwalletinfo" -> mapOf("avoid_reuse" to true)
                "getreceivedbyaddress" -> throw RpcException(-4, "Wallet is currently rescanning")
                "listdescriptors" -> mapOf(
                    "descriptors" to listOf(
                        mapOf("desc" to "wpkh(031b84c5567b126440995d3ed5aaba0565d71e1834604819ff9c17f5e9d5dd078f)#c94napag"),
                    ),
                )
                "deriveaddresses" -> listOf("be1q0xcqpzrky6eff2g52qdye53xkk9jxkvr6p27vz")
                else -> null
            }
        }
        val wallet = ReceiveWallet(rpc, store, "elektron")
        val address = wallet.currentAddress()

        assertEquals("be1q0xcqpzrky6eff2g52qdye53xkk9jxkvr6p27vz", address)
    }

    @Test
    fun returnsWatchOnlyAddrDescriptorDirectly() {
        val store = MemoryAddressStore()
        val expectedAddress = "be1qwatchonlytestaddress"
        val rpc = MockWalletRpc { method, _ ->
            when (method) {
                "getwalletinfo" -> mapOf("avoid_reuse" to true)
                "getnewaddress" -> throw RpcException(-4, "Can't get a new address from a watch-only wallet")
                "listdescriptors" -> mapOf(
                    "descriptors" to listOf(
                        mapOf("desc" to "addr($expectedAddress)#checksum"),
                    ),
                )
                "getreceivedbyaddress" -> 0.0
                else -> null
            }
        }
        val wallet = ReceiveWallet(rpc, store, "watch")
        val address = wallet.currentAddress()

        assertEquals(expectedAddress, address)
        assertEquals(expectedAddress, store.current)
    }
}
