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

class UtxoRescanIntegrationTest {
    // a payment that is not the first transaction of its block, found by a block rescan and then by a UTXO scan, must survive another block rescan
    @Test
    fun aUtxoScanAfterABlockRescanDoesNotBreakTheNextBlockRescan() {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dir = Files.createTempDirectory("rescan-it").toFile()
        val supervisor = NodeSupervisor(NodeConfig(File(binary!!), dir, Chain.Regtest, rpcPort = 29600, p2pPort = 29601), pollIntervalMs = 100)
        supervisor.start()
        try {
            val end = System.currentTimeMillis() + 60_000
            while (supervisor.state != NodeState.Running) {
                check(System.currentTimeMillis() < end) { "node did not start: ${supervisor.state} ${supervisor.output.tail(12)}" }
                Thread.sleep(100)
            }
            val rpc = supervisor.rpc.asWalletRpc()
            rpc.call("createwallet", linkedMapOf("wallet_name" to "miner"), null)
            val miner = rpc.call("getnewaddress", emptyList<Any?>(), "miner").toString()
            rpc.call("generatetoaddress", listOf(110, miner), "miner")
            rpc.call("createwallet", linkedMapOf("wallet_name" to "w1"), null)
            val a1 = rpc.call("getnewaddress", emptyList<Any?>(), "w1").toString()
            rpc.call("sendtoaddress", linkedMapOf("address" to miner, "amount" to 1.0, "fee_rate" to 10), "miner")
            rpc.call("sendtoaddress", linkedMapOf("address" to a1, "amount" to 5.0, "fee_rate" to 10), "miner")
            val hash = (rpc.call("generatetoaddress", listOf(1, miner), "miner") as List<*>).single().toString()
            val index = ((rpc.call("getblock", listOf(hash, 1), null) as Map<*, *>)["tx"] as List<*>).size
            assertTrue(index >= 3)
            val height = (rpc.call("getblockcount", emptyList<Any?>(), null) as Number).toInt()

            val descs = ((rpc.call("listdescriptors", listOf(true), "w1") as Map<*, *>)["descriptors"] as List<*>).map { it as Map<*, *> }
            rpc.call("createwallet", linkedMapOf("wallet_name" to "w2", "blank" to true), null)
            val requests = descs.map { d -> linkedMapOf("desc" to d["desc"], "timestamp" to 0, "active" to d["active"], "internal" to d["internal"], "range" to listOf(0, 20)) }
            val imported = rpc.call("importdescriptors", listOf(requests), "w2") as List<*>
            assertTrue(imported.all { (it as Map<*, *>)["success"] == true })
            fun trusted() = WalletReader.lep(((rpc.call("getbalances", emptyList<Any?>(), "w2") as Map<*, *>)["mine"] as Map<*, *>)["trusted"])
            assertEquals(500_000_000L, trusted())

            rpc.call("rescanutxoset", emptyList<Any?>(), "w2")
            assertEquals(500_000_000L, trusted())
            try {
                rpc.call("rescanblockchain", listOf(height - 1), "w2")
            } catch (e: java.io.IOException) {
                Thread.sleep(500)
                throw AssertionError("node gone after rescanblockchain: " + supervisor.output.tail(8))
            }
            assertEquals(500_000_000L, trusted())
            assertEquals(NodeState.Running, supervisor.state)
        } finally {
            supervisor.stop()
            dir.deleteRecursively()
        }
    }
}
