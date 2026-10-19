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

class UtxoRecoveryIntegrationTest {
    private fun feeOf(rpc: WalletRpc, txid: String): Long {
        val entry = rpc.call("gettransaction", listOf(txid), "w2") as Map<*, *>
        return Math.abs(WalletReader.lep(entry["fee"]))
    }

    @Test
    fun outputsRecoveredFromThePrunedUtxoSetKeepTheirRealOutpointAndAreSpendable() {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dir = Files.createTempDirectory("recovery-it").toFile()
        val supervisor = NodeSupervisor(NodeConfig(File(binary!!), dir, Chain.Regtest, rpcPort = 29172, p2pPort = 29173), pollIntervalMs = 100)
        supervisor.start()
        try {
            val end = System.currentTimeMillis() + 60_000
            while (supervisor.state != NodeState.Running) {
                check(System.currentTimeMillis() < end) { "node did not start" }
                Thread.sleep(100)
            }
            val rpc = supervisor.rpc.asWalletRpc()
            rpc.call("createwallet", linkedMapOf("wallet_name" to "miner"), null)
            val miner = rpc.call("getnewaddress", emptyList<Any?>(), "miner").toString()
            rpc.call("generatetoaddress", listOf(110, miner), "miner")
            rpc.call("createwallet", linkedMapOf("wallet_name" to "w1"), null)
            val a1 = rpc.call("getnewaddress", emptyList<Any?>(), "w1").toString()
            rpc.call("sendtoaddress", linkedMapOf("address" to a1, "amount" to 5.0, "fee_rate" to 10), "miner")
            rpc.call("sendtoaddress", linkedMapOf("address" to a1, "amount" to 5.0, "fee_rate" to 10), "miner")
            rpc.call("generatetoaddress", listOf(1, miner), "miner")
            val real = (rpc.call("listunspent", emptyList<Any?>(), "w1") as List<*>).map { (it as Map<*, *>)["txid"].toString() + ":" + (it["vout"] as Number).toInt() }.toSet()
            assertEquals(2, real.size)

            rpc.call("generatetoaddress", listOf(400, miner), "miner")
            assertTrue(((rpc.call("getblockchaininfo", emptyList<Any?>(), null) as Map<*, *>)["pruneheight"] as Number).toInt() > 200)
            val descs = ((rpc.call("listdescriptors", listOf(true), "w1") as Map<*, *>)["descriptors"] as List<*>).map { it as Map<*, *> }
            rpc.call("createwallet", linkedMapOf("wallet_name" to "w2", "blank" to true), null)
            val requests = descs.map { d -> linkedMapOf("desc" to d["desc"], "timestamp" to 4102444800L, "active" to d["active"], "internal" to d["internal"], "range" to listOf(0, 20)) }
            val imported = rpc.call("importdescriptors", listOf(requests), "w2") as List<*>
            assertTrue(imported.all { (it as Map<*, *>)["success"] == true })

            rpc.call("rescanutxoset", emptyList<Any?>(), "w2")
            fun outpoints(wallet: String) = (rpc.call("listunspent", emptyList<Any?>(), wallet) as List<*>).map { (it as Map<*, *>)["txid"].toString() + ":" + (it["vout"] as Number).toInt() }.toSet()
            fun trusted(wallet: String) = WalletReader.lep(((rpc.call("getbalances", emptyList<Any?>(), wallet) as Map<*, *>)["mine"] as Map<*, *>)["trusted"])
            assertEquals(1_000_000_000L, trusted("w2"))
            assertEquals(real, outpoints("w2"))

            rpc.call("rescanutxoset", emptyList<Any?>(), "w2")
            assertEquals(1_000_000_000L, trusted("w2"))
            assertEquals(real, outpoints("w2"))

            rpc.call("unloadwallet", listOf("w2"), null)
            rpc.call("loadwallet", listOf("w2"), null)
            assertEquals(1_000_000_000L, trusted("w2"))
            assertEquals(real, outpoints("w2"))

            val funded = rpc.call("walletcreatefundedpsbt", listOf(emptyList<Any?>(), listOf(linkedMapOf(miner to 1.0)), 0, linkedMapOf("fee_rate" to 10)), "w2") as Map<*, *>
            val processed = rpc.call("walletprocesspsbt", listOf(funded["psbt"], true, "ALL", true, true), "w2") as Map<*, *>
            assertEquals(true, processed["complete"])
            val accepted = (rpc.call("testmempoolaccept", listOf(listOf(processed["hex"].toString())), null) as List<*>).single() as Map<*, *>
            assertEquals(true, accepted["allowed"])

            val txid = rpc.call("sendtoaddress", linkedMapOf("address" to miner, "amount" to 1.0, "fee_rate" to 10), "w2").toString()
            assertTrue((rpc.call("getrawmempool", emptyList<Any?>(), null) as List<*>).contains(txid))
            val spent = ((rpc.call("getrawtransaction", listOf(txid, true), null) as Map<*, *>)["vin"] as List<*>).map { (it as Map<*, *>)["txid"].toString() + ":" + (it["vout"] as Number).toInt() }
            assertTrue(spent.all { it in real })
            rpc.call("generatetoaddress", listOf(1, miner), "miner")
            assertEquals(1_000_000_000L - 100_000_000L, trusted("w2") + feeOf(rpc, txid))
            assertTrue(real.none { it in outpoints("w2") && spent.contains(it) })
        } finally {
            supervisor.stop()
            dir.deleteRecursively()
        }
    }
}
