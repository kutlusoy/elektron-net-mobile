package org.elektronnet.mobile.wallet

import java.io.File
import java.math.BigDecimal
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

class ExportAndCpfpIntegrationTest {
    private val pass = "correct horse battery"
    private val phrase = Bip39.parse("legal winner thank year wave sausage worth useful legal winner thank yellow")

    private class Env(val rpc: WalletRpc, val miner: String, val own: String, val dir: File)

    private fun withEnv(port: Int, block: (Env) -> Unit) {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dir = Files.createTempDirectory("cpfp-it").toFile()
        val supervisor = NodeSupervisor(NodeConfig(File(binary!!), dir, Chain.Regtest, rpcPort = port, p2pPort = port + 1), pollIntervalMs = 100)
        supervisor.start()
        try {
            val end = System.currentTimeMillis() + 60_000
            while (supervisor.state != NodeState.Running) {
                check(System.currentTimeMillis() < end) { "node did not start" }
                Thread.sleep(100)
            }
            val rpc = supervisor.rpc.asWalletRpc()
            WalletBootstrap(rpc, Bip39(loadWordlist()), testnet = true, legacy = true, removeWalletFiles = {}).create(phrase, pass)
            rpc.call("createwallet", linkedMapOf("wallet_name" to "miner"), null)
            val miner = rpc.call("getnewaddress", emptyList<Any?>(), "miner").toString()
            rpc.call("generatetoaddress", listOf(110, miner), "miner")
            val own = ReceiveWallet(rpc, object : AddressStore { override var current: String? = null }).newAddress()
            block(Env(rpc, miner, own, dir))
        } finally {
            supervisor.stop()
            dir.deleteRecursively()
        }
    }

    private fun lowFeePayment(env: Env): String =
        env.rpc.call("sendtoaddress", linkedMapOf("address" to env.own, "amount" to 1.0, "fee_rate" to 1), "miner").toString()

    @Test
    fun cpfpRaisesThePackageRate() = withEnv(29102) { env ->
        val parent = lowFeePayment(env)
        val cpfp = Cpfp(env.rpc)
        val target = BigDecimal("25")
        val plan = cpfp.plan(parent, target)
        assertTrue(plan.sendLep < plan.inputLep)
        val child = cpfp.accelerate(parent, target, pass)
        val pool = env.rpc.call("getrawmempool", emptyList<Any?>(), null) as List<*>
        assertTrue(pool.contains(child) && pool.contains(parent))
        val entry = env.rpc.call("getmempoolentry", listOf(child), null) as Map<*, *>
        val fees = WalletReader.lep((entry["fees"] as Map<*, *>)["ancestor"])
        val rate = BigDecimal(fees).divide(BigDecimal((entry["ancestorsize"] as Number).toInt()), 2, java.math.RoundingMode.HALF_UP)
        assertTrue("package rate $rate", rate >= BigDecimal("24"))
        assertTrue((env.rpc.call("getwalletinfo", emptyList<Any?>(), "elektron") as Map<*, *>)["unlocked_until"] == 0L)
        env.rpc.call("generatetoaddress", listOf(1, env.miner), "miner")
        assertTrue((env.rpc.call("getrawmempool", emptyList<Any?>(), null) as List<*>).isEmpty())
    }

    @Test
    fun cpfpRefusesWhenNotPendingOrNotRaising() = withEnv(29112) { env ->
        val parent = lowFeePayment(env)
        val cpfp = Cpfp(env.rpc)
        try {
            cpfp.plan(parent, BigDecimal("0.5"))
            throw AssertionError("expected FeeTooLow")
        } catch (e: CpfpException) {
            assertEquals(CpfpFailure.FeeTooLow, e.failure)
        }
        try {
            cpfp.accelerate(parent, BigDecimal("25"), "wrong passphrase!")
            throw AssertionError("expected WrongPassphrase")
        } catch (e: CpfpException) {
            assertEquals(CpfpFailure.WrongPassphrase, e.failure)
        }
        env.rpc.call("generatetoaddress", listOf(1, env.miner), "miner")
        try {
            cpfp.plan(parent, BigDecimal("25"))
            throw AssertionError("expected NotPending")
        } catch (e: CpfpException) {
            assertEquals(CpfpFailure.NotPending, e.failure)
        }
    }

    @Test
    fun cpfpSuggestionExceedsTheCurrentRate() = withEnv(29122) { env ->
        val parent = lowFeePayment(env)
        val cpfp = Cpfp(env.rpc)
        assertTrue(cpfp.suggestedRate(parent) > BigDecimal.ONE)
    }

    @Test
    fun descriptorExportHasNoPrivateKeys() = withEnv(29132) { env ->
        val text = WalletExport(env.rpc).descriptorText()
        assertTrue(text.contains("wpkh("))
        assertFalse(text.contains("prv"))
        assertTrue(WalletExport(env.rpc).publicDescriptors().any { it.internal })
    }

    @Test
    fun encryptedBackupIsWrittenOnceAndNotReadable() = withEnv(29142) { env ->
        val export = WalletExport(env.rpc)
        val target = File(env.dir, "exports/wallets/" + WalletExport.backupName("elektron", 1L))
        val file = export.backup(target)
        assertTrue(file.length() > 0)
        val bytes = file.readBytes()
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("legal winner"))
        try {
            export.backup(target)
            throw AssertionError("expected TargetExists")
        } catch (e: ExportException) {
            assertEquals(ExportFailure.TargetExists, e.failure)
        }
    }

    @Test
    fun unusedWalletsCanBeRemovedWithoutThePassphraseButUsedOnesCannot() = withEnv(29152) { env ->
        val removed = mutableListOf<String>()
        val remover = WalletRemover(env.rpc) { removed.add(it); deleteWalletDir(File(env.dir, "regtest/wallets/$it")) }
        assertTrue(remover.isUnused("elektron"))
        lowFeePayment(env)
        assertFalse(remover.isUnused("elektron"))
        assertEquals(RemoveFailure.WrongPassphrase, remover.remove("elektron", null))
        assertTrue(removed.isEmpty())
        env.rpc.call("createwallet", linkedMapOf("wallet_name" to "second", "passphrase" to "another long phrase"), null)
        assertTrue(remover.isUnused("second"))
        assertEquals(null, remover.remove("second", null))
        assertEquals(listOf("second"), removed)
    }
}
