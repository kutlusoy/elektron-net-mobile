package org.elektronnet.mobile.wallet

import java.io.File
import java.nio.file.Files
import org.elektronnet.mobile.node.Chain
import org.elektronnet.mobile.node.NodeConfig
import org.elektronnet.mobile.node.NodeState
import org.elektronnet.mobile.node.NodeSupervisor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class MultiWalletIntegrationTest {
    private val first = Bip39.parse("legal winner thank year wave sausage worth useful legal winner thank yellow")
    private val second = Bip39.parse("letter advice cage absurd amount doctor acoustic avoid letter advice cage above")

    @Test
    fun twoWalletsStayIndependent() {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dir = Files.createTempDirectory("multi-it").toFile()
        val supervisor = NodeSupervisor(NodeConfig(File(binary!!), dir, Chain.Regtest, rpcPort = 29032, p2pPort = 29033), pollIntervalMs = 100)
        supervisor.start()
        try {
            val end = System.currentTimeMillis() + 60_000
            while (supervisor.state != NodeState.Running) {
                check(System.currentTimeMillis() < end) { "node did not start" }
                Thread.sleep(100)
            }
            val rpc = supervisor.rpc.asWalletRpc()
            val words = Bip39(loadWordlist())
            fun boot(name: String) = WalletBootstrap(rpc, words, testnet = true, legacy = true, removeWalletFiles = {}, walletName = name)
            assertTrue(boot(WalletBootstrap.DEFAULT_WALLET).create(first, "first passphrase!") is BootstrapResult.Created)
            assertTrue(boot("elektron_2").create(second, "second passphrase!") is BootstrapResult.Created)

            fun receive(name: String) = ReceiveWallet(rpc, object : AddressStore { override var current: String? = null }, name)
            val a = receive(WalletBootstrap.DEFAULT_WALLET).newAddress()
            val b = receive("elektron_2").newAddress()
            assertNotEquals(a, b)

            rpc.call("createwallet", linkedMapOf("wallet_name" to "miner"), null)
            val miner = rpc.call("getnewaddress", emptyList<Any?>(), "miner").toString()
            rpc.call("generatetoaddress", listOf(110, miner), "miner")
            rpc.call("sendtoaddress", linkedMapOf("address" to b, "amount" to 4.0, "fee_rate" to 10), "miner")
            rpc.call("generatetoaddress", listOf(1, miner), "miner")

            assertEquals(0L, WalletReader(rpc, WalletBootstrap.DEFAULT_WALLET).balances().trustedLep)
            assertEquals(400_000_000L, WalletReader(rpc, "elektron_2").balances().trustedLep)
            assertEquals(1, CoinControl(rpc, "elektron_2").coins().size)
            assertEquals(0, CoinControl(rpc, WalletBootstrap.DEFAULT_WALLET).coins().size)

            val engine = SendEngine(rpc, AddressCheck(testnet = true, hrp = "bcrt"), "elektron_2")
            val rate = FeeSource(rpc).options()[1].lepPerVb
            val prepared = engine.prepare(SendRequest(miner, 100_000_000L, false, rate))
            try {
                engine.send(prepared, "first passphrase!")
                throw AssertionError("the other wallet's passphrase must not unlock this wallet")
            } catch (e: SendException) {
                assertEquals(SendFailure.WrongPassphrase, e.failure)
            }
            assertEquals(64, engine.send(prepared, "second passphrase!").length)

            val walletDir = File(dir, "regtest/wallets/elektron_2")
            assertTrue(walletDir.exists())
            val remover = WalletRemover(rpc) { File(dir, "regtest/wallets/$it").deleteRecursively() }
            assertEquals(RemoveFailure.WrongPassphrase, remover.remove("elektron_2", "first passphrase!"))
            assertTrue((rpc.call("listwallets", emptyList<Any?>(), null) as List<*>).contains("elektron_2"))
            assertTrue(walletDir.exists())
            assertEquals(null, remover.remove("elektron_2", "second passphrase!"))
            assertTrue(!(rpc.call("listwallets", emptyList<Any?>(), null) as List<*>).contains("elektron_2"))
            assertTrue(!walletDir.exists())
            assertTrue((rpc.call("listwallets", emptyList<Any?>(), null) as List<*>).contains(WalletBootstrap.DEFAULT_WALLET))
        } finally {
            supervisor.stop()
            dir.deleteRecursively()
        }
    }
}
