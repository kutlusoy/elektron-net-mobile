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

class WalletRestoreIntegrationTest {
    @Test
    fun backupFilesAreRestoredAndAnUnencryptedOneGetsAPassphrase() {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dir = Files.createTempDirectory("restore-it").toFile()
        val supervisor = NodeSupervisor(NodeConfig(File(binary!!), dir, Chain.Regtest, rpcPort = 29176, p2pPort = 29177), pollIntervalMs = 100)
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

            rpc.call("createwallet", linkedMapOf("wallet_name" to "plain"), null)
            rpc.call("createwallet", linkedMapOf("wallet_name" to "locked", "passphrase" to "correct horse battery"), null)
            for (name in listOf("plain", "locked")) {
                val address = rpc.call("getnewaddress", emptyList<Any?>(), name).toString()
                rpc.call("sendtoaddress", linkedMapOf("address" to address, "amount" to 3.0, "fee_rate" to 10), "miner")
            }
            rpc.call("generatetoaddress", listOf(1, miner), "miner")

            val backups = File(dir, "backups").apply { mkdirs() }
            rpc.call("backupwallet", listOf(File(backups, "plain.dat").path), "plain")
            rpc.call("backupwallet", listOf(File(backups, "locked.dat").path), "locked")
            rpc.call("unloadwallet", listOf("plain"), null)
            rpc.call("unloadwallet", listOf("locked"), null)

            val restore = WalletRestore(rpc) { name -> File(dir, "regtest/wallets/$name").deleteRecursively() }

            val locked = restore.restore(File(backups, "locked.dat"), "restored-locked")
            assertEquals(RestoreResult.Restored("restored-locked", true), locked)
            assertEquals(300_000_000L, balance("restored-locked"))

            val plain = restore.restore(File(backups, "plain.dat"), "restored-plain")
            assertEquals(RestoreResult.Restored("restored-plain", false), plain)
            assertEquals(RestoreFailure.WeakPassphrase, restore.protect("restored-plain", "short"))
            assertEquals(null, restore.protect("restored-plain", "a fresh passphrase 123"))
            val info = rpc.call("getwalletinfo", emptyList<Any?>(), "restored-plain") as Map<*, *>
            assertTrue(info.containsKey("unlocked_until"))
            assertEquals(300_000_000L, balance("restored-plain"))
            rpc.call("walletpassphrase", listOf("a fresh passphrase 123", 60), "restored-plain")
            rpc.call("walletlock", emptyList<Any?>(), "restored-plain")

            val missing = restore.restore(File(backups, "nope.dat"), "restored-none")
            assertEquals(RestoreResult.Failed(RestoreFailure.InvalidFile), missing)
            val garbage = File(backups, "garbage.dat").apply { writeText("this is not a wallet") }
            val broken = restore.restore(garbage, "restored-garbage")
            assertTrue(broken is RestoreResult.Failed)
            assertTrue(!File(dir, "regtest/wallets/restored-garbage").exists())

            val again = restore.restore(File(backups, "locked.dat"), "restored-locked")
            assertEquals(RestoreResult.Failed(RestoreFailure.NameTaken), again)
            assertEquals(300_000_000L, balance("restored-locked"))

            restore.discard("restored-plain")
            assertTrue(!File(dir, "regtest/wallets/restored-plain").exists())
        } finally {
            supervisor.stop()
            dir.deleteRecursively()
        }
    }
}
