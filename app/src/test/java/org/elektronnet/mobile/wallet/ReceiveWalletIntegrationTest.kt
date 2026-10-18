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

class ReceiveWalletIntegrationTest {
    private class MemoryStore : AddressStore {
        override var current: String? = null
    }

    @Test
    fun reusesUnusedAddressAndRotatesAfterPayment() {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dir = Files.createTempDirectory("rw-it").toFile()
        val supervisor = NodeSupervisor(NodeConfig(File(binary!!), dir, Chain.Regtest, rpcPort = 28632, p2pPort = 28633), pollIntervalMs = 100)
        supervisor.start()
        try {
            val end = System.currentTimeMillis() + 60_000
            while (supervisor.state != NodeState.Running) {
                check(System.currentTimeMillis() < end) { "node did not start" }
                Thread.sleep(100)
            }
            val rpc = supervisor.rpc.asWalletRpc()
            val phrase = Bip39.parse("legal winner thank year wave sausage worth useful legal winner thank yellow")
            val boot = WalletBootstrap(rpc, Bip39(loadWordlist()), testnet = true, legacy = true, removeWalletFiles = {})
            assertEquals(BootstrapResult.Created("elektron"), boot.create(phrase, "correct horse battery"))

            val store = MemoryStore()
            val receive = ReceiveWallet(rpc, store)
            val first = receive.currentAddress()
            assertEquals(first, receive.currentAddress())
            assertEquals(first, store.current)
            assertTrue(first.startsWith("bcrt1q") || first.contains("1q"))

            rpc.call("createwallet", linkedMapOf("wallet_name" to "miner"), null)
            val minerAddress = rpc.call("getnewaddress", emptyList<Any?>(), "miner").toString()
            rpc.call("generatetoaddress", listOf(110, minerAddress), "miner")
            rpc.call("sendtoaddress", linkedMapOf("address" to first, "amount" to 1.0, "fee_rate" to 10), "miner")

            val second = receive.currentAddress()
            assertNotEquals(first, second)
            assertEquals(second, receive.currentAddress())
        } finally {
            supervisor.stop()
            dir.deleteRecursively()
        }
    }
}
