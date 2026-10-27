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

// Edge cases of opening wallets and backups that node v4.0.5 wrote after a recovery from the UTXO set
class OldWalletHealingEdgeCasesIntegrationTest {
    private val pass = "correct horse battery staple"

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

    private var p1: String? = null
    private fun p1State(rpc: WalletRpc, wallet: String, label: String) {
        val id = p1 ?: return
        val conf = runCatching { (rpc.call("gettransaction", listOf(id), wallet) as Map<*, *>)["confirmations"] }.getOrElse { it.message }
        println("P1 $label [$wallet]: confirmations=$conf")
    }

    private fun blank(rpc: WalletRpc, name: String, descs: List<Map<*, *>>, encrypted: Boolean) {
        val options = linkedMapOf<String, Any?>("wallet_name" to name, "blank" to true)
        if (encrypted) options["passphrase"] = pass
        rpc.call("createwallet", options, null)
        if (encrypted) rpc.call("walletpassphrase", listOf(pass, 600), name)
        val requests = descs.map { d -> linkedMapOf("desc" to d["desc"], "timestamp" to "now", "active" to d["active"], "internal" to d["internal"], "range" to listOf(0, 40)) }
        val imported = rpc.call("importdescriptors", listOf(requests), name) as List<*>
        assertTrue(imported.all { (it as Map<*, *>)["success"] == true })
        if (encrypted) rpc.call("walletlock", emptyList<Any?>(), name)
    }

    @Test
    fun edgeCasesOfOldWalletsAndBackups() {
        val old = System.getenv("ELEKTROND_BIN_OLD")
        val new = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN_OLD or ELEKTROND_BIN not set", old != null && new != null && File(old).canExecute() && File(new).canExecute())
        val dir = Files.createTempDirectory("heal-edge-it").toFile()
        val backups = Files.createTempDirectory("heal-edge-backups").toFile()
        val port = 29820
        try {
            val first = start(old!!, dir, port)
            try {
                val rpc = first.rpc.asWalletRpc()
                rpc.call("createwallet", linkedMapOf("wallet_name" to "miner"), null)
                val miner = rpc.call("getnewaddress", emptyList<Any?>(), "miner").toString()
                rpc.call("generatetoaddress", listOf(110, miner), "miner")
                rpc.call("createwallet", linkedMapOf("wallet_name" to "w1"), null)
                val addresses = (1..4).map { rpc.call("getnewaddress", emptyList<Any?>(), "w1").toString() }
                val descs = ((rpc.call("listdescriptors", listOf(true), "w1") as Map<*, *>)["descriptors"] as List<*>).map { it as Map<*, *> }
                blank(rpc, "mixed", descs, encrypted = false)
                blank(rpc, "enc", descs, encrypted = true)
                rpc.call("unloadwallet", listOf("enc"), null)

                // the first payment arrives while the wallet "mixed" is loaded, so it knows that transaction for real
                rpc.call("sendtoaddress", linkedMapOf("address" to addresses[0], "amount" to 4.0, "fee_rate" to 10), "miner")
                var seen: List<*> = emptyList<Any?>()
                val until = System.currentTimeMillis() + 15_000
                while (seen.isEmpty() && System.currentTimeMillis() < until) {
                    seen = rpc.call("listtransactions", listOf("*", 10), "mixed") as List<*>
                    if (seen.isEmpty()) Thread.sleep(200)
                }
                p1 = (seen.single() as Map<*, *>)["txid"].toString()
                // the wallet is closed while this payment is still unconfirmed, so its file never learns about the block
                rpc.call("unloadwallet", listOf("mixed"), null)
                rpc.call("generatetoaddress", listOf(1, miner), "miner")

                // many coins, equal amounts on one address, other positions in the block, change outputs
                repeat(3) { rpc.call("sendtoaddress", linkedMapOf("address" to addresses[1], "amount" to 2.0, "fee_rate" to 10), "miner") }
                rpc.call("sendtoaddress", linkedMapOf("address" to miner, "amount" to 1.0, "fee_rate" to 10), "miner")
                addresses.drop(2).forEach { rpc.call("sendtoaddress", linkedMapOf("address" to it, "amount" to 1.5, "fee_rate" to 10), "miner") }
                rpc.call("sendmany", linkedMapOf("amounts" to linkedMapOf(addresses[0] to 0.7, addresses[2] to 0.7, miner to 0.1), "fee_rate" to 10), "miner")
                rpc.call("generatetoaddress", listOf(1, miner), "miner")
                rpc.call("generatetoaddress", listOf(400, miner), "miner")
                assertTrue(((rpc.call("getblockchaininfo", emptyList<Any?>(), null) as Map<*, *>)["pruneheight"] as Number).toInt() > 200)
                rpc.call("loadwallet", listOf("mixed"), null)
                rpc.call("loadwallet", listOf("enc"), null)
                println("old node: mixed=${trusted(rpc, "mixed")} enc=${trusted(rpc, "enc")} truth=${trusted(rpc, "w1")}")
                rpc.call("backupwallet", listOf(File(backups, "mixed.dat").path), "mixed")
                File(backups, "mixed.dat").copyTo(File("/tmp/dbg-old-mixed.dat"), overwrite = true)
                File("/tmp/dbg-p1.txt").writeText(p1 ?: "")
                rpc.call("backupwallet", listOf(File(backups, "enc.dat").path), "enc")
                rpc.call("unloadwallet", listOf("mixed"), null)
                rpc.call("unloadwallet", listOf("enc"), null)
            } finally {
                first.stop()
            }

            val second = start(new!!, dir, port)
            try {
                val rpc = second.rpc.asWalletRpc()
                listOf("miner", "w1").forEach { rpc.call("loadwallet", listOf(it), null) }
                val miner = rpc.call("getnewaddress", emptyList<Any?>(), "miner").toString()
                assertTrue(outpoints(rpc, "w1").size >= 8)
                // w1 sees every transaction of these keys, so it is the reference at any moment
                fun same(label: String, wallet: String) {
                    val truth = outpoints(rpc, "w1")
                    val truthBalance = trusted(rpc, "w1")
                    println("$label: trusted=${trusted(rpc, wallet)} truth=$truthBalance utxos=${outpoints(rpc, wallet).size}/${truth.size}")
                    assertEquals("$label balance", truthBalance, trusted(rpc, wallet))
                    assertEquals("$label outpoints", truth, outpoints(rpc, wallet))
                }

                // in place, mixed real and unreadable transactions, then a block rescan that used to stop the node
                rpc.call("loadwallet", listOf("mixed"), null)
                p1State(rpc, "mixed", "after heal")
                same("mixed in place", "mixed")
                val pruneHeight = ((rpc.call("getblockchaininfo", emptyList<Any?>(), null) as Map<*, *>)["pruneheight"] as Number).toInt()
                rpc.call("rescanblockchain", listOf(pruneHeight), "mixed")
                p1State(rpc, "mixed", "after rescanblockchain")
                same("mixed after block rescan", "mixed")
                rpc.call("rescanutxoset", emptyList<Any?>(), "mixed")
                p1State(rpc, "mixed", "after rescanutxoset")
                rpc.call("rescanblockchain", listOf(pruneHeight), "mixed")
                p1State(rpc, "mixed", "after second rescanblockchain")
                same("mixed after both rescans", "mixed")
                assertEquals(NodeState.Running, second.state)

                // encrypted wallet: opened locked, heals, still asks for the passphrase, then signs and sends
                rpc.call("loadwallet", listOf("enc"), null)
                same("encrypted in place", "enc")
                val locked = try { rpc.call("sendtoaddress", linkedMapOf("address" to miner, "amount" to 0.5), "enc"); false } catch (e: Exception) { true }
                assertTrue("a locked wallet cannot send", locked)
                rpc.call("walletpassphrase", listOf(pass, 60), "enc")
                val txid = rpc.call("sendtoaddress", linkedMapOf("address" to miner, "amount" to 1.0, "fee_rate" to 10), "enc").toString()
                rpc.call("walletlock", emptyList<Any?>(), "enc")
                assertTrue((rpc.call("getrawmempool", emptyList<Any?>(), null) as List<*>).contains(txid))
                rpc.call("generatetoaddress", listOf(1, miner), "miner")
                rpc.call("unloadwallet", listOf("enc"), null)
                rpc.call("loadwallet", listOf("enc"), null)
                println("encrypted after spend and reload: ${trusted(rpc, "enc")}")

                // the same backup restored twice, the encrypted backup too; the backups themselves stay as they are
                rpc.call("restorewallet", listOf("r1", File(backups, "mixed.dat").path), null)
                rpc.call("restorewallet", listOf("r2", File(backups, "mixed.dat").path), null)
                println("restored: r1=${trusted(rpc, "r1")} r2=${trusted(rpc, "r2")}")
                assertEquals(trusted(rpc, "r1"), trusted(rpc, "r2"))
                assertEquals(outpoints(rpc, "r1"), outpoints(rpc, "r2"))
                rpc.call("restorewallet", listOf("r3", File(backups, "enc.dat").path), null)
                assertTrue(trusted(rpc, "r3") > 0)

                // a reorg of the block that holds a new payment: the coin goes and comes back
                val before = trusted(rpc, "r1")
                rpc.call("sendtoaddress", linkedMapOf("address" to rpc.call("getnewaddress", emptyList<Any?>(), "w1").toString(), "amount" to 2.0, "fee_rate" to 10), "miner")
                val hash = (rpc.call("generatetoaddress", listOf(1, miner), "miner") as List<*>).single().toString()
                assertTrue(trusted(rpc, "r1") > before || trusted(rpc, "r1") == before)
                val after = trusted(rpc, "r1")
                rpc.call("invalidateblock", listOf(hash), null)
                assertTrue(trusted(rpc, "r1") <= after)
                rpc.call("reconsiderblock", listOf(hash), null)
                assertEquals(after, trusted(rpc, "r1"))

                // everything survives a restart of the node
                p1State(rpc, "mixed", "before reload")
                listOf("mixed", "r1", "r3").forEach {
                    rpc.call("unloadwallet", listOf(it), null)
                    if (it == "mixed") File(dir, "regtest/wallets/mixed/wallet.dat").copyTo(File("/tmp/dbg-mixed-after-unload.dat"), overwrite = true)
                    rpc.call("loadwallet", listOf(it), null)
                }
                p1State(rpc, "mixed", "after reload")
                run {
                    val w1 = (rpc.call("listunspent", emptyList<Any?>(), "w1") as List<*>).map { it as Map<*, *> }
                    val mixed = outpoints(rpc, "mixed")
                    println("DEBUG missing in mixed: " + w1.filter { (it["txid"].toString() + ":" + (it["vout"] as Number).toInt()) !in mixed }.map { it["txid"].toString().take(10) + ":" + it["vout"] + " amount=" + it["amount"] + " conf=" + it["confirmations"] })
                    println("DEBUG extra in mixed: " + (rpc.call("listunspent", emptyList<Any?>(), "mixed") as List<*>).map { it as Map<*, *> }.filter { (it["txid"].toString() + ":" + (it["vout"] as Number).toInt()) !in w1.map { w -> w["txid"].toString() + ":" + (w["vout"] as Number).toInt() } }.map { it["txid"].toString().take(10) + ":" + it["vout"] + " amount=" + it["amount"] })
                    w1.filter { (it["txid"].toString() + ":" + (it["vout"] as Number).toInt()) !in mixed }.forEach { m ->
                        val id = m["txid"].toString()
                        println("DEBUG gettxout: " + rpc.call("gettxout", listOf(id, m["vout"], true), null))
                        println("DEBUG mixed gettransaction: " + runCatching { (rpc.call("gettransaction", listOf(id, true, true), "mixed") as Map<*, *>).filterKeys { k -> k in setOf("confirmations", "abandoned", "details", "walletconflicts", "replaced_by_txid", "trusted") } }.getOrElse { e -> e.message })
                        println("DEBUG mixed listunspent0: " + (rpc.call("listunspent", listOf(0), "mixed") as List<*>).count { (it as Map<*, *>)["txid"] == id })
                        println("DEBUG mixed lockunspent: " + rpc.call("listlockunspent", emptyList<Any?>(), "mixed"))
                    }
                    println("DEBUG log: " + second.output.tail(20000).filter { it.contains("[mixed]") && (it.contains("marked") || it.contains("Dropped") || it.contains("UTXO") || it.contains("spend")) }.joinToString(" || ") { it.substringAfter("Z ").take(150) })
                }
                same("mixed after reload", "mixed")
                val drops = second.output.tail(20000).count { it.contains("Dropped unreadable recovered transaction") }
                println("unreadable records dropped in total: $drops")
                assertTrue(drops > 0)
            } finally {
                second.stop()
            }

            val third = start(new, dir, port)
            try {
                val rpc = third.rpc.asWalletRpc()
                listOf("miner", "w1", "mixed", "enc").forEach { rpc.call("loadwallet", listOf(it), null) }
                same3(rpc, "mixed", "w1")
                assertEquals("no record is dropped after the first healing", 0, third.output.tail(20000).count { it.contains("Dropped unreadable recovered transaction") })
            } finally {
                third.stop()
            }
        } finally {
            dir.deleteRecursively()
            backups.deleteRecursively()
        }
    }

    private fun same3(rpc: WalletRpc, wallet: String, truthWallet: String) {
        assertEquals(trusted(rpc, truthWallet), trusted(rpc, wallet))
        assertEquals(outpoints(rpc, truthWallet), outpoints(rpc, wallet))
    }
}
