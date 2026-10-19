package org.elektronnet.mobile.console

import java.io.File
import java.nio.file.Files
import org.elektronnet.mobile.node.Chain
import org.elektronnet.mobile.node.NodeConfig
import org.elektronnet.mobile.node.NodeState
import org.elektronnet.mobile.node.NodeSupervisor
import org.elektronnet.mobile.wallet.Bip39
import org.elektronnet.mobile.wallet.WalletBootstrap
import org.elektronnet.mobile.wallet.WalletRpc
import org.elektronnet.mobile.wallet.asWalletRpc
import org.elektronnet.mobile.wallet.loadWordlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ConsoleRunnerIntegrationTest {
    private val phrase = Bip39.parse("legal winner thank year wave sausage worth useful legal winner thank yellow")
    private val tiers = Tiers.parse(File("../native-build/contract/console-tiers.json").readText())

    private class Env(val rpc: WalletRpc, val runner: ConsoleRunner, val exports: File, val setFree: (Long) -> Unit)

    private fun withEnv(port: Int, block: (Env) -> Unit) {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dir = Files.createTempDirectory("console-it").toFile()
        val supervisor = NodeSupervisor(NodeConfig(File(binary!!), dir, Chain.Regtest, rpcPort = port, p2pPort = port + 1), pollIntervalMs = 100)
        supervisor.start()
        try {
            val end = System.currentTimeMillis() + 60_000
            while (supervisor.state != NodeState.Running) {
                check(System.currentTimeMillis() < end) { "node did not start" }
                Thread.sleep(100)
            }
            val rpc = supervisor.rpc.asWalletRpc()
            WalletBootstrap(rpc, Bip39(loadWordlist()), testnet = true, legacy = true, removeWalletFiles = {}).create(phrase, "correct horse battery")
            rpc.call("createwallet", linkedMapOf("wallet_name" to "miner"), null)
            val miner = rpc.call("getnewaddress", emptyList<Any?>(), "miner").toString()
            rpc.call("generatetoaddress", listOf(110, miner), "miner")
            val exports = File(dir, "exports").apply { mkdirs() }
            var free = Long.MAX_VALUE
            block(Env(rpc, ConsoleRunner(rpc, tiers, FileGuard(exports), { free }, 1024L * 1024 * 1024), exports) { free = it })
        } finally {
            supervisor.stop()
            dir.deleteRecursively()
        }
    }

    private fun ready(plan: Plan) = plan as Plan.Ready
    private fun refused(plan: Plan) = (plan as Plan.Refused).reason

    @Test
    fun openCommandsRunAndHeavyOnesAreMarked() = withEnv(29102) { env ->
        val plan = ready(env.runner.prepare("getblockcount", null))
        assertEquals(Tier.Open, plan.tier)
        assertEquals("110", env.runner.execute(plan).text)
        assertTrue(ready(env.runner.prepare("gettxoutsetinfo", null)).heavy)
        val err = env.runner.execute(ready(env.runner.prepare("getblockhash 99999", null)))
        assertTrue(err.error && err.text.startsWith("error "))
    }

    @Test
    fun blockedAndUnsafeCommandsAreRefusedBeforeTheNodeSeesThem() = withEnv(29112) { env ->
        assertEquals(Refusal.Blocked, refused(env.runner.prepare("stop", null)))
        assertEquals(Refusal.Blocked, refused(env.runner.prepare("loadtxoutset x.dat", null)))
        assertEquals(Refusal.RollbackNotAllowed, refused(env.runner.prepare("dumptxoutset a.dat rollback", null)))
        assertEquals(Refusal.DumpTypeMissing, refused(env.runner.prepare("dumptxoutset a.dat", null)))
        assertEquals(Refusal.NoPassphrase, refused(env.runner.prepare("createwallet nopass", null)))
        assertEquals(Refusal.NoPassphrase, refused(env.runner.prepare("createwallet wallet_name=nopass passphrase=\"\"", null)))
        assertEquals(Refusal.WatchOnly, refused(env.runner.prepare("createwallet watch true false secret", null)))
        assertTrue(env.runner.execute(ready(env.runner.prepare("listwallets", null))).text.contains("elektron"))
    }

    @Test
    fun createWalletNeedsAPassphrase() = withEnv(29122) { env ->
        val plan = ready(env.runner.prepare("createwallet wallet_name=console_made passphrase=\"long enough secret\"", null))
        assertEquals("createwallet wallet_name=console_made passphrase=***", plan.display)
        assertFalse(env.runner.execute(plan).error)
        val info = env.rpc.call("getwalletinfo", emptyList<Any?>(), "console_made") as Map<*, *>
        assertTrue(info.containsKey("unlocked_until"))
    }

    @Test
    fun backupsStayInsideExportsAndNeedAnEncryptedWallet() = withEnv(29132) { env ->
        assertEquals(Refusal.NeedsWallet, refused(env.runner.prepare("backupwallet b.dat", null)))
        assertEquals(Refusal.PathTraversal, refused(env.runner.prepare("backupwallet ../b.dat", "elektron")))
        assertEquals(Refusal.PathOutside, refused(env.runner.prepare("backupwallet /tmp/b.dat", "elektron")))
        val plan = ready(env.runner.prepare("backupwallet b.dat", "elektron"))
        assertFalse(env.runner.execute(plan).error)
        assertTrue(File(env.exports, "b.dat").isFile)
        assertEquals(Refusal.PathExists, refused(env.runner.prepare("backupwallet b.dat", "elektron")))

        env.rpc.call("createwallet", linkedMapOf("wallet_name" to "plain"), null)
        assertEquals(Refusal.WalletNotEncrypted, refused(env.runner.prepare("backupwallet p.dat", "plain")))
        assertFalse(File(env.exports, "p.dat").exists())
    }

    @Test
    fun restoreKeepsAnEncryptedWalletEncryptedAndFlagsAnUnencryptedOne() = withEnv(29142) { env ->
        env.runner.execute(ready(env.runner.prepare("backupwallet good.dat", "elektron")))
        val restored = env.runner.execute(ready(env.runner.prepare("restorewallet restored good.dat", null)))
        assertFalse(restored.error)
        assertNull(restored.needsPassphraseFor)

        env.rpc.call("createwallet", linkedMapOf("wallet_name" to "plain"), null)
        env.rpc.call("backupwallet", listOf(File(env.exports, "plain.dat").path), "plain")
        val plan = ready(env.runner.prepare("restorewallet plain_back plain.dat", null))
        assertEquals(Tier.Danger, plan.tier)
        val result = env.runner.execute(plan)
        assertFalse(result.error)
        assertEquals("plain_back", result.needsPassphraseFor)

        assertEquals(Refusal.PathMissing, refused(env.runner.prepare("restorewallet other missing.dat", null)))
    }

    @Test
    fun utxoDumpGoesToTheAutoDeleteFolderAndChecksStorage() = withEnv(29152) { env ->
        env.setFree(10L * 1024 * 1024)
        assertEquals(Refusal.LowStorage, refused(env.runner.prepare("dumptxoutset snap.dat latest", null)))
        env.setFree(Long.MAX_VALUE)
        val plan = ready(env.runner.prepare("dumptxoutset snap.dat latest", null))
        assertEquals(Tier.Danger, plan.tier)
        assertTrue(plan.heavy)
        assertEquals(File(env.exports.canonicalFile, "dumps/snap.dat").path, plan.autoDeleteFile!!.path)
        val result = env.runner.execute(plan)
        assertFalse(result.error)
        assertTrue(File(env.exports, "dumps/snap.dat").isFile)
        assertEquals(Refusal.PathExists, refused(env.runner.prepare("dumptxoutset snap.dat latest", null)))
    }

    @Test
    fun keyRevealingResultsAreMarkedSensitive() = withEnv(29162) { env ->
        assertFalse(ready(env.runner.prepare("listdescriptors", "elektron")).sensitive)
        val plan = ready(env.runner.prepare("listdescriptors true", "elektron"))
        assertEquals(Tier.Danger, plan.tier)
        assertTrue(plan.sensitive)
        assertTrue(ready(env.runner.prepare("walletpassphrase secret 5", "elektron")).display.contains("***"))
        assertEquals("walletpassphrase *** 5", ready(env.runner.prepare("walletpassphrase secret 5", "elektron")).display)
        val named = ready(env.runner.prepare("getblockhash height=1", null))
        assertEquals(mapOf<String, Any?>("height" to 1L), named.params)
    }
}
