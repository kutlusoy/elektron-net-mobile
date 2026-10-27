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

// Wallets (and backups) written by node v4.0.5 after a recovery from the UTXO set hold transactions without inputs,
// which no version can read. The new node must open them and credit the coins again from the UTXO set.
class OldWalletHealingIntegrationTest {
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

    private fun outpoints(rpc: WalletRpc, wallet: String) =
        (rpc.call("listunspent", emptyList<Any?>(), wallet) as List<*>).map { (it as Map<*, *>)["txid"].toString() + ":" + (it["vout"] as Number).toInt() }.toSet()

    private fun trusted(rpc: WalletRpc, wallet: String) = WalletReader.lep(((rpc.call("getbalances", emptyList<Any?>(), wallet) as Map<*, *>)["mine"] as Map<*, *>)["trusted"])

    @Test
    fun walletsAndBackupsOfTheOldNodeAreOpenedAndTheirCoinsCreditedAgain() {
        val old = System.getenv("ELEKTROND_BIN_OLD")
        val new = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN_OLD or ELEKTROND_BIN not set", old != null && new != null && File(old).canExecute() && File(new).canExecute())
        val dir = Files.createTempDirectory("heal-it").toFile()
        val backups = Files.createTempDirectory("heal-backups").toFile()
        val port = 29800
        try {
            val first = start(old!!, dir, port)
            try {
                val rpc = first.rpc.asWalletRpc()
                rpc.call("createwallet", linkedMapOf("wallet_name" to "miner"), null)
                val miner = rpc.call("getnewaddress", emptyList<Any?>(), "miner").toString()
                rpc.call("generatetoaddress", listOf(110, miner), "miner")
                rpc.call("createwallet", linkedMapOf("wallet_name" to "w1"), null)
                val a1 = rpc.call("getnewaddress", emptyList<Any?>(), "w1").toString()
                val descs = ((rpc.call("listdescriptors", listOf(true), "w1") as Map<*, *>)["descriptors"] as List<*>).map { it as Map<*, *> }
                rpc.call("createwallet", linkedMapOf("wallet_name" to "w2", "blank" to true), null)
                val requests = descs.map { d -> linkedMapOf("desc" to d["desc"], "timestamp" to "now", "active" to d["active"], "internal" to d["internal"], "range" to listOf(0, 20)) }
                rpc.call("importdescriptors", listOf(requests), "w2")
                rpc.call("unloadwallet", listOf("w2"), null)
                rpc.call("sendtoaddress", linkedMapOf("address" to a1, "amount" to 5.0, "fee_rate" to 10), "miner")
                rpc.call("sendtoaddress", linkedMapOf("address" to a1, "amount" to 3.0, "fee_rate" to 10), "miner")
                rpc.call("generatetoaddress", listOf(1, miner), "miner")
                rpc.call("generatetoaddress", listOf(400, miner), "miner")
                assertTrue(((rpc.call("getblockchaininfo", emptyList<Any?>(), null) as Map<*, *>)["pruneheight"] as Number).toInt() > 200)
                rpc.call("loadwallet", listOf("w2"), null)
                assertEquals(800_000_000L, trusted(rpc, "w2"))
                rpc.call("backupwallet", listOf(File(backups, "stale.dat").path), "w2")

                // the keys of w1 get a new coin and spend an old one while the backup rests
                rpc.call("sendtoaddress", linkedMapOf("address" to a1, "amount" to 2.0, "fee_rate" to 10), "miner")
                rpc.call("generatetoaddress", listOf(1, miner), "miner")
                rpc.call("sendtoaddress", linkedMapOf("address" to miner, "amount" to 4.0, "fee_rate" to 10), "w1")
                rpc.call("generatetoaddress", listOf(300, miner), "miner")
                rpc.call("backupwallet", listOf(File(backups, "current.dat").path), "w2")
                rpc.call("unloadwallet", listOf("w2"), null)
            } finally {
                first.stop()
            }

            val second = start(new!!, dir, port)
            try {
                val rpc = second.rpc.asWalletRpc()
                rpc.call("loadwallet", listOf("miner"), null)
                rpc.call("loadwallet", listOf("w1"), null)
                val truth = outpoints(rpc, "w1")
                val truthBalance = trusted(rpc, "w1")
                assertTrue(truth.isNotEmpty() && truthBalance > 0)
                fun check(label: String, wallet: String) {
                    println("$label: trusted=${trusted(rpc, wallet)} truth=$truthBalance utxos=${outpoints(rpc, wallet).size}/${truth.size}")
                    assertEquals("$label balance", truthBalance, trusted(rpc, wallet))
                    assertEquals("$label outpoints", truth, outpoints(rpc, wallet))
                    assertTrue((rpc.call("listtransactions", listOf("*", 100), wallet) as List<*>).none { (it as Map<*, *>)["txid"].toString().isEmpty() })
                }

                rpc.call("loadwallet", listOf("w2"), null)
                check("in place", "w2")
                rpc.call("unloadwallet", listOf("w2"), null)
                val dropsBefore = second.output.tail(5000).count { it.contains("Dropped unreadable") }
                rpc.call("loadwallet", listOf("w2"), null)
                check("in place again", "w2")
                assertEquals("nothing is dropped twice", dropsBefore, second.output.tail(5000).count { it.contains("Dropped unreadable") })

                rpc.call("restorewallet", listOf("w3", File(backups, "current.dat").path), null)
                check("current backup", "w3")
                rpc.call("restorewallet", listOf("w4", File(backups, "stale.dat").path), null)
                check("stale backup", "w4")

                // a healed wallet works: it signs and sends, and the coins are gone after the block
                val txid = rpc.call("sendtoaddress", linkedMapOf("address" to rpc.call("getnewaddress", emptyList<Any?>(), "miner").toString(), "amount" to 1.0, "fee_rate" to 10), "w4").toString()
                assertTrue((rpc.call("getrawmempool", emptyList<Any?>(), null) as List<*>).contains(txid))
                rpc.call("generatetoaddress", listOf(1, rpc.call("getnewaddress", emptyList<Any?>(), "miner").toString()), "miner")
                assertTrue(trusted(rpc, "w4") < truthBalance - 100_000_000L + 1_000_000L)
                rpc.call("unloadwallet", listOf("w4"), null)
                rpc.call("loadwallet", listOf("w4"), null)
                println("after spend and reload: " + trusted(rpc, "w4"))
            } finally {
                second.stop()
            }
        } finally {
            dir.deleteRecursively()
            backups.deleteRecursively()
        }
    }
}
