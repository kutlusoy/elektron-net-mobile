package org.elektronnet.mobile.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletLockTest {
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
}
