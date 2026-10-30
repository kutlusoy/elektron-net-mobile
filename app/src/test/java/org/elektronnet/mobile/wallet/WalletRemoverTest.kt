package org.elektronnet.mobile.wallet

import org.elektronnet.mobile.node.RpcException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletRemoverTest {
    private class FakeNode(var loaded: Boolean, val unloads: Boolean) {
        var deleted = false
        val rpc = WalletRpc { method, _, _ ->
            when (method) {
                "listwallets" -> if (loaded) listOf("elektron") else emptyList<String>()
                "unloadwallet" -> { if (unloads) loaded = false; emptyMap<String, Any?>() }
                "listwalletdir" -> mapOf("wallets" to if (deleted) emptyList<Any?>() else listOf(mapOf("name" to "elektron")))
                "getwalletinfo" -> mapOf("txcount" to 0)
                "getbalances" -> mapOf("mine" to mapOf("trusted" to 0.0))
                else -> null
            }
        }
    }

    @Test
    fun removesAWalletThatUnloads() {
        val node = FakeNode(loaded = true, unloads = true)
        val remover = WalletRemover(node.rpc) { node.deleted = true }
        assertNull(remover.remove("elektron", null))
        assertTrue(node.deleted)
    }

    @Test
    fun aNodeThatKeepsTheWalletLoadedIsReportedAsBusy() {
        val node = FakeNode(loaded = true, unloads = false)
        val remover = WalletRemover(node.rpc) { node.deleted = true }.apply { unloadWaitTotalMs = 400 }
        assertEquals(RemoveFailure.Busy, remover.remove("elektron", null))
        assertFalse(node.deleted)
    }

    @Test
    fun filesThatStayAreNotBusy() {
        val node = FakeNode(loaded = false, unloads = true)
        val remover = WalletRemover(node.rpc) { }
        assertEquals(RemoveFailure.Unknown, remover.remove("elektron", null))
    }

    @Test
    fun aWalletBeingRemovedIsNotLoadedAgainByOtherCalls() {
        WalletLoadGuard.block("elektron")
        assertFalse(WalletLoadGuard.allows("elektron"))
        assertTrue(WalletLoadGuard.allows("other"))
        WalletLoadGuard.release("elektron")
        assertTrue(WalletLoadGuard.allows("elektron"))
    }

    @Test
    fun aWalletWithoutPassphraseIsRemovedAfterTheConfirmation() {
        var deleted = false
        var loaded = true
        val rpc = WalletRpc { method, _, _ ->
            when (method) {
                "walletpassphrase" -> throw RpcException(-15, "running with an unencrypted wallet")
                "listwallets" -> if (loaded) listOf("elektron") else emptyList<String>()
                "unloadwallet" -> { loaded = false; emptyMap<String, Any?>() }
                "listwalletdir" -> mapOf("wallets" to if (deleted) emptyList<Any?>() else listOf(mapOf("name" to "elektron")))
                else -> null
            }
        }
        assertNull(WalletRemover(rpc) { deleted = true }.remove("elektron", "any passphrase"))
        assertTrue(deleted)
    }

    @Test
    fun theReasonOfAFailureIsKept() {
        val rpc = WalletRpc { method, _, _ ->
            if (method == "walletpassphrase") throw RpcException(-4, "something else") else null
        }
        val remover = WalletRemover(rpc) { }
        assertEquals(RemoveFailure.Unknown, remover.remove("elektron", "pass"))
        assertTrue(remover.lastError!!.contains("-4"))
    }

    @Test
    fun removesAFundedWalletAfterTheConfirmationWithoutAPassphrase() {
        val node = FakeNode(loaded = true, unloads = true)
        val remover = WalletRemover(node.rpc) { node.deleted = true }
        assertNull(remover.removeConfirmed("elektron"))
        assertTrue(node.deleted)
    }
}
