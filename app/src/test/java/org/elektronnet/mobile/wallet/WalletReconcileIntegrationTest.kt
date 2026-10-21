package org.elektronnet.mobile.wallet

import java.io.File
import java.nio.file.Files
import org.elektronnet.mobile.node.Chain
import org.elektronnet.mobile.node.NodeConfig
import org.elektronnet.mobile.node.NodeState
import org.elektronnet.mobile.node.NodeSupervisor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class WalletReconcileIntegrationTest {
    @Test
    fun theWalletListFollowsTheNodeAndRestoredWalletsStayUsable() {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dir = Files.createTempDirectory("reconcile-it").toFile()
        val supervisor = NodeSupervisor(NodeConfig(File(binary!!), dir, Chain.Regtest, rpcPort = 29230, p2pPort = 29231), pollIntervalMs = 100)
        supervisor.start()
        try {
            val end = System.currentTimeMillis() + 60_000
            while (supervisor.state != NodeState.Running) {
                check(System.currentTimeMillis() < end) { "node did not start" }
                Thread.sleep(100)
            }
            val rpc = supervisor.rpc.asWalletRpc()
            fun balance(wallet: String) = WalletReader.lep(((rpc.call("getbalances", emptyList<Any?>(), wallet) as Map<*, *>)["mine"] as Map<*, *>)["trusted"])
            rpc.call("createwallet", linkedMapOf("wallet_name" to "miner"), null)
            val miner = rpc.call("getnewaddress", emptyList<Any?>(), "miner").toString()
            rpc.call("generatetoaddress", listOf(110, miner), "miner")
            rpc.call("createwallet", linkedMapOf("wallet_name" to "elektron", "passphrase" to "correct horse battery"), null)
            rpc.call("createwallet", linkedMapOf("wallet_name" to "elektron_2", "passphrase" to "correct horse battery"), null)
            rpc.call("createwallet", linkedMapOf("wallet_name" to "escrow_1"), null)
            rpc.call("unloadwallet", listOf("elektron_2"), null)

            val registry = WalletRegistry(MemoryWalletRegistryStore())
            registry.add("ghost", "")
            registry.add("pending", "")
            registry.add("elektron", "main")
            val reconciler = WalletReconciler(rpc, registry) { setOf("pending") }
            val quiet = reconciler.reconcile(adopt = false)!!
            assertEquals(listOf("ghost"), quiet.dropped)
            assertTrue(quiet.adopted.isEmpty())
            assertTrue(quiet.loaded.isEmpty())
            assertFalse(registry.wallets().any { it.name == "elektron_2" })
            registry.add("ghost", "")
            val result = reconciler.reconcile()!!
            assertEquals(listOf("ghost"), result.dropped)
            assertEquals(listOf("elektron_2"), result.adopted)
            assertEquals(listOf("elektron_2"), result.loaded)
            assertEquals(listOf("pending", "elektron", "elektron_2"), registry.wallets().map { it.name })
            assertEquals("main", registry.wallets().first { it.name == "elektron" }.label)
            assertTrue("elektron_2" in (rpc.call("listwallets", emptyList<Any?>(), null) as List<*>))
            assertFalse(registry.wallets().any { it.name == "escrow_1" || it.name == "miner" })

            val again = reconciler.reconcile()!!
            assertTrue(again.dropped.isEmpty() && again.adopted.isEmpty() && again.loaded.isEmpty())

            val remover = WalletRemover(rpc) { name -> File(dir, "regtest/wallets/$name").deleteRecursively() }
            assertEquals(false, remover.existsOnNode("ghost"))
            assertTrue(remover.isUnused("ghost"))
            assertEquals(null, remover.remove("ghost", null))

            val address = rpc.call("getnewaddress", emptyList<Any?>(), "elektron").toString()
            rpc.call("sendtoaddress", linkedMapOf("address" to address, "amount" to 2.0, "fee_rate" to 10), "miner")
            rpc.call("generatetoaddress", listOf(1, miner), "miner")
            assertEquals(200_000_000L, balance("elektron"))
            assertTrue(CoinRefresh(rpc).refresh("elektron"))
            assertEquals(200_000_000L, balance("elektron"))
            assertFalse(CoinRefresh(rpc).refresh("does-not-exist"))
            assertEquals(RemoveFailure.WrongPassphrase, remover.remove("elektron", "wrong passphrase 123"))
            assertEquals(null, remover.remove("elektron", "correct horse battery"))
            assertFalse(File(dir, "regtest/wallets/elektron").exists())
        } finally {
            supervisor.stop()
            dir.deleteRecursively()
        }
    }
}
