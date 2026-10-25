package org.elektronnet.mobile.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletLockTest {
    private class Odd(val info: Any?, val fails: Boolean = false) : WalletRpc {
        val calls = mutableListOf<String>()
        override fun call(method: String, params: Any, wallet: String?): Any? {
            calls.add(method)
            if (method == "getwalletinfo") {
                if (fails) throw org.elektronnet.mobile.node.RpcException(-1, "down")
                return info
            }
            return null
        }
    }

    private class Node(var lockWorksFrom: Int) : WalletRpc {
        val calls = mutableListOf<String>()
        private var locks = 0
        private var unlocked = true
        override fun call(method: String, params: Any, wallet: String?): Any? {
            calls.add(method)
            when (method) {
                "walletlock" -> if (++locks >= lockWorksFrom) unlocked = false
                "getwalletinfo" -> return mapOf("unlocked_until" to if (unlocked) 1_000L else 0L)
            }
            return null
        }
    }

    @Test
    fun aConfirmedLockIsDoneAfterOneCall() {
        val node = Node(1)
        assertTrue(WalletLock.lock(node, "w"))
        assertEquals(listOf("walletlock", "getwalletinfo"), node.calls)
    }

    @Test
    fun aFailedLockIsTriedAgain() {
        val node = Node(2)
        assertTrue(WalletLock.lock(node, "w"))
        assertEquals(2, node.calls.count { it == "walletlock" })
    }

    @Test
    fun anUnconfirmedLockShrinksTheUnlockWindowWhenThePassphraseIsKnown() {
        val node = Node(99)
        assertFalse(WalletLock.lock(node, "w", "secret passphrase"))
        assertEquals("walletpassphrase", node.calls.last())
        val without = Node(99)
        assertFalse(WalletLock.lock(without, "w"))
        assertFalse(without.calls.contains("walletpassphrase"))
    }

    @Test
    fun anUnknownAnswerIsNotAConfirmedLock() {
        assertFalse(WalletLock.lock(Odd(null), "w"))
        assertFalse(WalletLock.lock(Odd("text"), "w"))
        assertFalse(WalletLock.lock(Odd(mapOf("unlocked_until" to null)), "w"))
        assertFalse(WalletLock.lock(Odd(mapOf("unlocked_until" to "0")), "w"))
        assertFalse(WalletLock.lock(Odd(null, fails = true), "w"))
        assertFalse(WalletLock.lock(Odd(mapOf("unlocked_until" to 5L)), "w"))
    }

    @Test
    fun aWalletWithoutPassphraseHasNothingToLock() {
        assertTrue(WalletLock.lock(Odd(mapOf("walletname" to "w")), "w"))
    }
}
