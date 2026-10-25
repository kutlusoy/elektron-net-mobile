package org.elektronnet.mobile.wallet

import org.elektronnet.mobile.node.Json
import org.elektronnet.mobile.node.RpcException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletSetupTest {
    private val phrase = Bip39.parse("legal winner thank year wave sausage worth useful legal winner thank yellow")
    private val bip39 = Bip39(loadWordlist())
    private val pass = "correct horse battery"

    private class FakeNode(var warmups: Int = 0, var utxoRescan: Boolean = false, var passphraseOk: Boolean = true) {
        val calls = mutableListOf<String>()
        val rpc = WalletRpc { method, _, _ ->
            calls += method
            when (method) {
                "createwallet" -> if (warmups-- > 0) throw RpcException(-28, "Loading block index") else Json.parse("""{"name":"elektron"}""")
                "walletpassphrase" -> if (passphraseOk) null else throw RpcException(-14, "wrong")
                "getdescriptorinfo" -> Json.parse("""{"checksum":"abcd1234"}""")
                "importdescriptors" -> Json.parse("""[{"success":true},{"success":true},{"success":true},{"success":true},{"success":true}]""")
                "getwalletinfo" -> Json.parse("""{"unlocked_until":0}""")
                "getblockchaininfo" -> Json.parse("""{"pruneheight":5}""")
                "help" -> if (utxoRescan) "rescanutxoset ..." else "help: unknown command: rescanutxoset"
                else -> null
            }
        }
    }

    private fun setup(node: FakeNode, available: () -> Boolean = { true }) =
        WalletSetup { if (available()) WalletBootstrap(node.rpc, bip39, testnet = true, removeWalletFiles = {}) else null }

    @Test
    fun createWaitsForTheNodeAndThenSucceeds() {
        var ready = false
        val node = FakeNode()
        val s = setup(node) { ready }
        s.begin(SetupRequest(phrase, pass, restore = false))
        assertEquals(SetupStatus.Waiting, s.step(false))
        assertEquals(SetupStatus.Waiting, s.step(false))
        ready = true
        assertEquals(SetupStatus.Ready(), s.step(false))
        assertTrue(node.calls.contains("createwallet"))
        assertFalse(s.hasRescanPassphrase)
    }

    @Test
    fun nodeWarmupKeepsWaitingInsteadOfFailing() {
        val node = FakeNode(warmups = 2)
        val s = setup(node)
        s.begin(SetupRequest(phrase, pass, false))
        assertEquals(SetupStatus.Waiting, s.step(false))
        assertEquals(SetupStatus.Waiting, s.step(false))
        assertEquals(SetupStatus.Ready(), s.step(false))
    }

    @Test
    fun invalidInputFailsAtOnce() {
        val s = setup(FakeNode())
        s.begin(SetupRequest(phrase.dropLast(1) + "zoo", pass, false))
        assertEquals(SetupStatus.Failed(BootstrapFailure.InvalidPhrase), s.step(false))
        s.retry()
        assertEquals(SetupStatus.Waiting, s.status)
        s.cancel()
        assertEquals(SetupStatus.None, s.status)
    }

    @Test
    fun restoreCreatesAtOnceAndRescansWhenSynced() {
        val node = FakeNode()
        val s = setup(node)
        s.begin(SetupRequest(phrase, pass, restore = true))
        assertEquals(SetupStatus.NeedsRescan, s.step(nodeSynced = false))
        assertFalse(node.calls.contains("rescanblockchain"))
        assertTrue(s.hasRescanPassphrase)
        assertEquals(SetupStatus.NeedsRescan, s.step(nodeSynced = false))
        assertEquals(SetupStatus.Ready(partialRestore = true), s.step(nodeSynced = true))
        assertTrue(node.calls.contains("rescanblockchain"))
        assertFalse(node.calls.contains("rescanutxoset"))
        assertFalse(s.hasRescanPassphrase)
        assertEquals(listOf("walletlock", "getwalletinfo"), node.calls.takeLast(2))
    }

    @Test
    fun fullRestoreWhenTheNodeCanScanTheUtxoSet() {
        val node = FakeNode(utxoRescan = true)
        val s = setup(node)
        s.begin(SetupRequest(phrase, pass, restore = true))
        s.step(false)
        assertEquals(SetupStatus.Ready(partialRestore = false), s.step(true))
        assertTrue(node.calls.contains("rescanutxoset"))
    }

    @Test
    fun resumedRescanNeedsThePassphraseAgain() {
        val node = FakeNode()
        val s = setup(node)
        s.markRescanPending()
        assertEquals(SetupStatus.NeedsRescan, s.step(true))
        assertFalse(s.hasRescanPassphrase)
        node.passphraseOk = false
        s.resumeRescan("wrong passphrase!")
        assertEquals(SetupStatus.NeedsRescan, s.step(true))
        assertFalse(s.hasRescanPassphrase)
        node.passphraseOk = true
        s.resumeRescan(pass)
        assertEquals(SetupStatus.Ready(true), s.step(true))
    }
}
