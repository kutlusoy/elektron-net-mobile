package org.elektronnet.mobile.wallet

import java.io.File
import java.nio.file.Files
import org.elektronnet.mobile.node.Chain
import org.elektronnet.mobile.node.NodeConfig
import org.elektronnet.mobile.node.NodeState
import org.elektronnet.mobile.node.NodeSupervisor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class WalletBootstrapIntegrationTest {
    private val phrase = Bip39.parse("legal winner thank year wave sausage worth useful legal winner thank yellow")

    private fun withNode(block: (WalletRpc, File) -> Unit) {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dir = Files.createTempDirectory("wb-it").toFile()
        val supervisor = NodeSupervisor(NodeConfig(File(binary!!), dir, Chain.Regtest, rpcPort = 28532, p2pPort = 28533), pollIntervalMs = 100)
        supervisor.start()
        try {
            val end = System.currentTimeMillis() + 60_000
            while (supervisor.state != NodeState.Running) {
                check(System.currentTimeMillis() < end) { "node did not start" }
                Thread.sleep(100)
            }
            block(supervisor.rpc.asWalletRpc(), dir)
        } finally {
            supervisor.stop()
            dir.deleteRecursively()
        }
    }

    private fun bootstrap(rpc: WalletRpc, dir: File) = WalletBootstrap(
        rpc, Bip39(loadWordlist()), testnet = true, legacy = true,
        removeWalletFiles = { File(dir, "regtest/wallets/$it").deleteRecursively() },
    )

    @Test
    fun createsLockedWalletWithBothDescriptorSets() = withNode { rpc, dir ->
        val result = bootstrap(rpc, dir).create(phrase, "correct horse battery")
        assertEquals(BootstrapResult.Created("elektron"), result)
        val info = rpc.call("getwalletinfo", emptyList<Any?>(), "elektron") as Map<*, *>
        assertEquals(0L, info["unlocked_until"])
        val listed = rpc.call("listdescriptors", emptyList<Any?>(), "elektron") as Map<*, *>
        val descriptors = listed["descriptors"] as List<*>
        assertEquals(5, descriptors.size)
        assertEquals(2, descriptors.count { (it as Map<*, *>)["active"] == true })
        val address = rpc.call("getnewaddress", emptyList<Any?>(), "elektron").toString()
        val again = bootstrap(rpc, dir).let { rpc.call("listwallets", emptyList<Any?>(), null) as List<*> }
        assertEquals(listOf("elektron"), again)
        assertTrue(address.isNotEmpty())
    }

    @Test
    fun sameWordsGiveSameFirstAddress() = withNode { rpc, dir ->
        bootstrap(rpc, dir).create(phrase, "correct horse battery")
        val first = rpc.call("getnewaddress", emptyList<Any?>(), "elektron").toString()
        rpc.call("unloadwallet", listOf("elektron"), null)
        File(dir, "regtest/wallets/elektron").deleteRecursively()
        bootstrap(rpc, dir).create(phrase, "another passphrase here")
        assertEquals(first, rpc.call("getnewaddress", emptyList<Any?>(), "elektron").toString())
    }

    @Test
    fun rejectsBadInputBeforeTouchingTheNode() = withNode { rpc, dir ->
        val b = bootstrap(rpc, dir)
        assertEquals(BootstrapFailure.InvalidPhrase, (b.create(phrase.dropLast(1) + "zoo", "correct horse battery") as BootstrapResult.Failed).reason)
        assertEquals(BootstrapFailure.WeakPassphrase, (b.create(phrase, "short") as BootstrapResult.Failed).reason)
        assertEquals(emptyList<Any?>(), rpc.call("listwallets", emptyList<Any?>(), null))
    }

    @Test
    fun secondCreateFailsWithoutDamagingTheFirst() = withNode { rpc, dir ->
        val b = bootstrap(rpc, dir)
        b.create(phrase, "correct horse battery")
        val second = b.create(phrase, "correct horse battery")
        assertEquals(BootstrapFailure.WalletExists, (second as BootstrapResult.Failed).reason)
        assertEquals(listOf("elektron"), rpc.call("listwallets", emptyList<Any?>(), null))
    }

    @Test
    fun recoveryCreatesTheWalletAtOnceAndRescansLater() = withNode { rpc, dir ->
        val b = bootstrap(rpc, dir)
        assertEquals(BootstrapResult.Created("elektron"), b.recover(phrase, "correct horse battery"))
        assertEquals(listOf("elektron"), rpc.call("listwallets", emptyList<Any?>(), null))
        rpc.call("createwallet", linkedMapOf("wallet_name" to "miner"), null)
        rpc.call("generatetoaddress", listOf(1, rpc.call("getnewaddress", emptyList<Any?>(), "miner").toString()), "miner")
        val outcome = b.rescan("correct horse battery")
        assertTrue("outcome $outcome", outcome is RescanOutcome.Full || outcome is RescanOutcome.Partial)
        assertEquals(RescanOutcome.Failed(BootstrapFailure.WrongPassphrase), b.rescan("wrong passphrase!"))
        val info = rpc.call("getwalletinfo", emptyList<Any?>(), "elektron") as Map<*, *>
        assertEquals(0L, info["unlocked_until"])
    }
}
