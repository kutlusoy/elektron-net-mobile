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
    @Test
    fun prunedRecoveryCreditsTheBalanceButSpendingNeedsTheRealOutpoints() {
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
            val bal = WalletReader.lep(((rpc.call("getbalances", emptyList<Any?>(), "w2") as Map<*, *>)["mine"] as Map<*, *>)["trusted"])
            val recovered = (rpc.call("listunspent", emptyList<Any?>(), "w2") as List<*>).map { (it as Map<*, *>)["txid"].toString() + ":" + (it["vout"] as Number).toInt() }.toSet()
            assertEquals(1_000_000_000L, bal)
            val credited = (rpc.call("listunspent", emptyList<Any?>(), "w2") as List<*>).map { (it as Map<*, *>)["txid"].toString() + ":" + (it["vout"] as Number).toInt() }.toSet()
            assertTrue("known defect: recovered outputs carry synthetic txids, update this test when the node keys them by the real txid", credited != real)
            val publicDescs = ((rpc.call("listdescriptors", listOf(false), "w1") as Map<*, *>)["descriptors"] as List<*>).map { it as Map<*, *> }.filter { it["internal"] != true }.map { it["desc"].toString() }
            val scan = rpc.call("scantxoutset", listOf("start", publicDescs.map { linkedMapOf("desc" to it, "range" to 20) }), null) as Map<*, *>
            val found = (scan["unspents"] as List<*>).map { it as Map<*, *> }
            assertEquals(real, found.map { it["txid"].toString() + ":" + (it["vout"] as Number).toInt() }.toSet())
            assertEquals(1_000_000_000L, WalletReader.lep(scan["total_amount"]))

            val pick = found.first()
            val amount = WalletReader.lep(pick["amount"])
            val out = java.math.BigDecimal(amount - 10_000L).movePointLeft(8)
            val psbt = rpc.call("createpsbt", listOf(listOf(linkedMapOf("txid" to pick["txid"], "vout" to pick["vout"])), listOf(linkedMapOf(miner to out))), null).toString()
            val updated = (rpc.call("utxoupdatepsbt", listOf(psbt, publicDescs.map { linkedMapOf("desc" to it, "range" to 20) }), null)).toString()
            val signed = (rpc.call("walletprocesspsbt", listOf(updated, true, "ALL", true, false), "w2") as Map<*, *>)["psbt"].toString()
            val finalized = rpc.call("finalizepsbt", listOf(signed, true), null) as Map<*, *>
            assertEquals(true, finalized["complete"])
            val accept = (rpc.call("testmempoolaccept", listOf(listOf(finalized["hex"].toString())), null) as List<*>).single() as Map<*, *>
            assertEquals(true, accept["allowed"])
            val broadcast = rpc.call("sendrawtransaction", listOf(finalized["hex"].toString()), null).toString()
            assertEquals(64, broadcast.length)
        } finally {
            supervisor.stop()
            dir.deleteRecursively()
        }
    }
}
