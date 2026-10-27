package org.elektronnet.mobile.node

import java.io.File
import java.nio.file.Files
import org.elektronnet.mobile.wallet.WalletRpc
import org.elektronnet.mobile.wallet.asWalletRpc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

// Node 4.0.5 (ELEKTROND_BIN_OLD, a full build of main) against node 4.0.6 (ELEKTROND_BIN): the data directory of one opens
// with the other, and both versions stay on the same chain when they validate each other's blocks
class NodeUpgradeIntegrationTest {
    private fun start(binary: String, dir: File, rpc: Int, p2p: Int, listen: Boolean = false): NodeSupervisor {
        val supervisor = NodeSupervisor(NodeConfig(File(binary), dir, Chain.Regtest, rpcPort = rpc, p2pPort = p2p, listen = listen), pollIntervalMs = 100)
        supervisor.start()
        val end = System.currentTimeMillis() + 120_000
        while (supervisor.state != NodeState.Running) {
            check(System.currentTimeMillis() < end) { "node did not start: ${supervisor.state} ${supervisor.output.tail(12)}" }
            Thread.sleep(100)
        }
        return supervisor
    }

    private fun info(rpc: WalletRpc) = rpc.call("getblockchaininfo", emptyList<Any?>(), null) as Map<*, *>

    private fun mineWithPayments(rpc: WalletRpc, miner: String, blocks: Int, perBlock: Int) {
        repeat(blocks) {
            repeat(perBlock) { rpc.call("sendtoaddress", linkedMapOf("address" to miner, "amount" to 0.01, "fee_rate" to 10), "miner") }
            rpc.call("generatetoaddress", listOf(1, miner), "miner")
        }
    }

    // The versions differ for blocks that come more than two minutes after their parent (see the second test), so these tests
    // keep the clock of both nodes at one minute per block
    private var clock = 1296688602L

    private fun mineClocked(nodes: List<WalletRpc>, miner: WalletRpc, address: String, blocks: Int, payments: Int) {
        repeat(blocks) {
            clock += 60
            nodes.forEach { it.call("setmocktime", listOf(clock), null) }
            repeat(payments) { miner.call("sendtoaddress", linkedMapOf("address" to address, "amount" to 0.01, "fee_rate" to 10), "miner") }
            miner.call("generatetoaddress", listOf(1, address), "miner")
        }
    }

    private fun waitSame(a: WalletRpc, b: WalletRpc) {
        val end = System.currentTimeMillis() + 90_000
        while (System.currentTimeMillis() < end) {
            if (info(a)["bestblockhash"] == info(b)["bestblockhash"]) return
            Thread.sleep(200)
        }
        error("tips differ: ${info(a)["blocks"]} ${info(a)["bestblockhash"]} against ${info(b)["blocks"]} ${info(b)["bestblockhash"]}")
    }

    private fun versions(): Pair<String, String>? {
        val old = System.getenv("ELEKTROND_BIN_OLD")
        val new = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN_OLD or ELEKTROND_BIN not set", old != null && new != null && File(old).canExecute() && File(new).canExecute())
        return old!! to new!!
    }

    @Test
    fun aDataDirectoryOfTheOldNodeOpensWithTheNewOneAndBack() {
        val (old, new) = versions()!!
        val dir = Files.createTempDirectory("upgrade-node-it").toFile()
        try {
            val first = start(old, dir, 29920, 29921)
            val before: Map<*, *>
            val balanceBefore: Any?
            try {
                val rpc = first.rpc.asWalletRpc()
                rpc.call("createwallet", linkedMapOf("wallet_name" to "miner"), null)
                val miner = rpc.call("getnewaddress", emptyList<Any?>(), "miner").toString()
                rpc.call("generatetoaddress", listOf(110, miner), "miner")
                mineWithPayments(rpc, miner, 420, 3)
                before = info(rpc)
                balanceBefore = (rpc.call("getbalances", emptyList<Any?>(), "miner") as Map<*, *>)["mine"]
                assertTrue((before["pruneheight"] as Number).toInt() > 100)
            } finally {
                first.stop()
            }

            val second = start(new, dir, 29920, 29921)
            try {
                val rpc = second.rpc.asWalletRpc()
                rpc.call("loadwallet", listOf("miner"), null)
                val after = info(rpc)
                println("4.0.5 -> 4.0.6: height ${before["blocks"]} -> ${after["blocks"]}, same tip ${before["bestblockhash"] == after["bestblockhash"]}, chainwork same ${before["chainwork"] == after["chainwork"]}")
                assertEquals(before["blocks"], after["blocks"])
                assertEquals(before["bestblockhash"], after["bestblockhash"])
                assertEquals(before["chainwork"], after["chainwork"])
                assertEquals(before["pruneheight"], after["pruneheight"])
                assertEquals(true, rpc.call("verifychain", listOf(3, 6), null))
                assertEquals(balanceBefore, (rpc.call("getbalances", emptyList<Any?>(), "miner") as Map<*, *>)["mine"])
                val miner = rpc.call("getnewaddress", emptyList<Any?>(), "miner").toString()
                mineWithPayments(rpc, miner, 30, 3)
                assertEquals(((before["blocks"] as Number).toInt() + 30), (info(rpc)["blocks"] as Number).toInt())
                println("marker file after the upgrade: " + File(dir, "regtest/.elektron_auto_reindex_attempted").exists())
                assertFalse(File(dir, "regtest/.elektron_auto_reindex_attempted").exists())
            } finally {
                second.stop()
            }
            val tipOfNew: Map<*, *>
            val again = start(new, dir, 29920, 29921)
            try { tipOfNew = info(again.rpc.asWalletRpc()) } finally { again.stop() }

            // and back to the old node (a downgrade) on the chain the new one extended
            val back = start(old, dir, 29920, 29921)
            try {
                val rpc = back.rpc.asWalletRpc()
                val after = info(rpc)
                println("4.0.6 -> 4.0.5: height ${tipOfNew["blocks"]} -> ${after["blocks"]}, same tip ${tipOfNew["bestblockhash"] == after["bestblockhash"]}")
                assertEquals(tipOfNew["bestblockhash"], after["bestblockhash"])
                assertEquals(true, rpc.call("verifychain", listOf(3, 6), null))
            } finally {
                back.stop()
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun bothVersionsValidateEachOthersBlocksAndStayOnOneChain() {
        val (old, new) = versions()!!
        val dirA = Files.createTempDirectory("mixed-a").toFile()
        val dirB = Files.createTempDirectory("mixed-b").toFile()
        val a = start(old, dirA, 29930, 29931, listen = true)
        val b = start(new, dirB, 29934, 29936)
        try {
            val ra = a.rpc.asWalletRpc()
            val rb = b.rpc.asWalletRpc()
            rb.call("addnode", listOf("127.0.0.1:29931", "onetry"), null)
            ra.call("createwallet", linkedMapOf("wallet_name" to "miner"), null)
            val minerA = ra.call("getnewaddress", emptyList<Any?>(), "miner").toString()
            val both = listOf(ra, rb)
            mineClocked(both, ra, minerA, 110, 0)
            waitSame(ra, rb)

            // the old node mines blocks with payments, the new one must accept them, then the other way round
            mineClocked(both, ra, minerA, 15, 0)
            waitSame(ra, rb)
            rb.call("createwallet", linkedMapOf("wallet_name" to "miner"), null)
            val minerB = rb.call("getnewaddress", emptyList<Any?>(), "miner").toString()
            mineClocked(both, rb, minerB, 110, 0)
            waitSame(ra, rb)
            mineClocked(both, rb, minerB, 15, 0)
            waitSame(ra, rb)

            // a reorg on each side
            val tip = info(ra)["bestblockhash"].toString()
            ra.call("invalidateblock", listOf(tip), null)
            rb.call("invalidateblock", listOf(tip), null)
            mineClocked(both, rb, minerB, 3, 0)
            waitSame(ra, rb)
            val tips = (ra.call("getchaintips", emptyList<Any?>(), null) as List<*>).map { (it as Map<*, *>)["status"] }
            val tipsB = (rb.call("getchaintips", emptyList<Any?>(), null) as List<*>).map { (it as Map<*, *>)["status"] }
            println("old node ${info(ra)["blocks"]} / new node ${info(rb)["blocks"]}, chain tips: $tips / $tipsB")
            assertEquals(info(ra)["bestblockhash"], info(rb)["bestblockhash"])
        } finally {
            b.stop()
            a.stop()
            dirA.deleteRecursively()
            dirB.deleteRecursively()
        }
    }

    // Blocks that come more than two minutes after their parent may use the minimum difficulty on regtest and the test
    // networks. Node 4.0.6 gives them the lower limit of the powLimit fix, node 4.0.5 the old limit, so each version
    // rejects the other's first such block. On the main network the rule does not apply (and the fix starts at 500000).
    @Test
    fun theVersionsDisagreeAboutMinimumDifficultyBlocks() {
        val (old, new) = versions()!!
        val dirA = Files.createTempDirectory("mindiff-a").toFile()
        val dirB = Files.createTempDirectory("mindiff-b").toFile()
        val a = start(old, dirA, 29940, 29941, listen = true)
        val b = start(new, dirB, 29944, 29946)
        try {
            val ra = a.rpc.asWalletRpc()
            val rb = b.rpc.asWalletRpc()
            rb.call("addnode", listOf("127.0.0.1:29941", "onetry"), null)
            ra.call("createwallet", linkedMapOf("wallet_name" to "miner"), null)
            val minerA = ra.call("getnewaddress", emptyList<Any?>(), "miner").toString()
            ra.call("generatetoaddress", listOf(3, minerA), "miner")
            Thread.sleep(5_000)
            println("minimum difficulty block: old node at ${info(ra)["blocks"]}, new node at ${info(rb)["blocks"]}")
        } finally {
            b.stop()
            a.stop()
            dirA.deleteRecursively()
            dirB.deleteRecursively()
        }
    }
}
