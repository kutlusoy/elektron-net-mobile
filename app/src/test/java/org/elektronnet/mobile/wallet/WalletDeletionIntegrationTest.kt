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

// A wallet that was removed in the app must be gone from the disk: not in the wallet folder, not after a restart of the
// node, and the "reload wallets" button must not bring it back
class WalletDeletionIntegrationTest {
    private val pass = "correct horse battery"

    private fun start(binary: String, dir: File, port: Int): NodeSupervisor {
        val supervisor = NodeSupervisor(NodeConfig(File(binary), dir, Chain.Regtest, rpcPort = port, p2pPort = port + 1), pollIntervalMs = 100)
        supervisor.start()
        val end = System.currentTimeMillis() + 90_000
        while (supervisor.state != NodeState.Running) {
            check(System.currentTimeMillis() < end) { "node did not start: ${supervisor.state} ${supervisor.output.tail(12)}" }
            Thread.sleep(100)
        }
        return supervisor
    }

    private fun listed(rpc: WalletRpc) = ((rpc.call("listwalletdir", emptyList<Any?>(), null) as Map<*, *>)["wallets"] as List<*>).map { (it as Map<*, *>)["name"].toString() }.toSet()

    @Test
    fun removedWalletsStayGone() {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dir = Files.createTempDirectory("delete-it").toFile()
        val port = 29860
        try {
            var node = start(binary!!, dir, port)
            var rpc = node.rpc.asWalletRpc()
            rpc.call("createwallet", linkedMapOf("wallet_name" to "miner"), null)
            val miner = rpc.call("getnewaddress", emptyList<Any?>(), "miner").toString()
            rpc.call("generatetoaddress", listOf(110, miner), "miner")
            rpc.call("createwallet", linkedMapOf("wallet_name" to "plain"), null)
            rpc.call("createwallet", linkedMapOf("wallet_name" to "locked", "passphrase" to pass), null)
            for (name in listOf("plain", "locked")) {
                rpc.call("sendtoaddress", linkedMapOf("address" to rpc.call("getnewaddress", emptyList<Any?>(), name).toString(), "amount" to 3.0, "fee_rate" to 10), "miner")
            }
            rpc.call("generatetoaddress", listOf(1, miner), "miner")
            val backups = File(dir, "backups").apply { mkdirs() }
            rpc.call("backupwallet", listOf(File(backups, "plain.dat").path), "plain")
            rpc.call("backupwallet", listOf(File(backups, "locked.dat").path), "locked")
            rpc.call("unloadwallet", listOf("plain"), null)
            rpc.call("unloadwallet", listOf("locked"), null)

            val registry = WalletRegistry(MemoryWalletRegistryStore())
            val remove: (String) -> Unit = { name -> deleteWalletDir(File(dir, "regtest/wallets/$name")) }
            val restore = WalletRestore(rpc, remove)
            val remover = WalletRemover(rpc, remove)

            // an encrypted backup, restored, listed and removed with its passphrase
            assertEquals(RestoreResult.Restored("elektron_2", true), restore.restore(File(backups, "locked.dat"), "elektron_2"))
            registry.add("elektron_2", "")
            assertTrue(File(dir, "regtest/wallets/elektron_2").isDirectory)
            assertEquals(null, remover.remove("elektron_2", pass))
            registry.remove("elektron_2")
            println("after removing the encrypted one: dir=${File(dir, "regtest/wallets/elektron_2").exists()} listed=${listed(rpc)}")
            assertFalse(File(dir, "regtest/wallets/elektron_2").exists())
            assertFalse("elektron_2" in listed(rpc))

            // an unencrypted backup that is restored and then thrown away instead of getting a passphrase
            assertEquals(RestoreResult.Restored("elektron_3", false), restore.restore(File(backups, "plain.dat"), "elektron_3"))
            assertTrue(restore.discard("elektron_3"))
            println("after discarding the unencrypted one: dir=${File(dir, "regtest/wallets/elektron_3").exists()} listed=${listed(rpc)}")
            assertFalse(File(dir, "regtest/wallets/elektron_3").exists())
            assertFalse("elektron_3" in listed(rpc))

            // an unencrypted backup that got a passphrase and is removed later
            assertEquals(RestoreResult.Restored("elektron_4", false), restore.restore(File(backups, "plain.dat"), "elektron_4"))
            assertEquals(null, restore.protect("elektron_4", pass))
            registry.add("elektron_4", "")
            assertEquals(null, remover.remove("elektron_4", pass))
            registry.remove("elektron_4")
            assertFalse(File(dir, "regtest/wallets/elektron_4").exists())
            assertFalse("elektron_4" in listed(rpc))

            // a removal that does not reach the wallet files (wrong folder) is not reported as done and the wallet stays usable
            assertEquals(RestoreResult.Restored("elektron_5", false), restore.restore(File(backups, "plain.dat"), "elektron_5"))
            assertEquals(null, restore.protect("elektron_5", pass))
            registry.add("elektron_5", "")
            assertEquals(RemoveFailure.Unknown, WalletRemover(rpc) { _ -> }.remove("elektron_5", pass))
            assertTrue("elektron_5" in listed(rpc))
            // the reload button leaves a wallet alone that is being removed right now
            rpc.call("loadwallet", listOf("elektron_5"), null)
            rpc.call("unloadwallet", listOf("elektron_5"), null)
            assertTrue("elektron_5" !in (rpc.call("listwallets", emptyList<Any?>(), null) as List<*>))
            val skipped = WalletReconciler(rpc, registry) { setOf("elektron_5") }.reconcile(adopt = true)!!
            assertEquals(emptyList<String>(), skipped.loaded)
            assertTrue("elektron_5" !in (rpc.call("listwallets", emptyList<Any?>(), null) as List<*>))
            // with the real files removal it goes, and a wallet that is still loaded is unloaded first
            rpc.call("loadwallet", listOf("elektron_5"), null)
            assertEquals(null, remover.remove("elektron_5", pass))
            registry.remove("elektron_5")
            assertFalse(File(dir, "regtest/wallets/elektron_5").exists())
            assertFalse("elektron_5" in listed(rpc))

            // "reload wallets" does not find anything to bring back
            val reconciled = WalletReconciler(rpc, registry).reconcile(adopt = true)!!
            println("reconcile: adopted=${reconciled.adopted} dropped=${reconciled.dropped} loaded=${reconciled.loaded}")
            assertEquals(emptyList<String>(), reconciled.adopted)

            // and neither does a restart of the node
            node.stop()
            node = start(binary, dir, port)
            rpc = node.rpc.asWalletRpc()
            println("after the restart: listed=${listed(rpc)} loaded=${rpc.call("listwallets", emptyList<Any?>(), null)}")
            assertTrue(listed(rpc).none { it.startsWith("elektron_") })
            val again = WalletReconciler(rpc, registry).reconcile(adopt = true)!!
            assertEquals(emptyList<String>(), again.adopted)
            println("wallet folder: " + File(dir, "regtest/wallets").list()?.sorted() + " settings: " + File(dir, "regtest/settings.json").takeIf { it.exists() }?.readText()?.take(200))
            node.stop()
        } finally {
            dir.deleteRecursively()
        }
    }
}
