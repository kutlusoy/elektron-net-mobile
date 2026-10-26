package org.elektronnet.mobile.wallet

import java.io.File
import java.nio.file.Files
import org.elektronnet.mobile.node.Chain
import org.elektronnet.mobile.node.NodeConfig
import org.elektronnet.mobile.node.NodeState
import org.elektronnet.mobile.node.NodeSupervisor
import org.elektronnet.mobile.node.RpcException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

// two real nodes: A mines and sends, B only validates what it receives, so a bad attestation or any other consensus problem stops B
class OpReturnConsensusIntegrationTest {
    private class Net(val a: WalletRpc, val b: WalletRpc, val miner: String)

    private fun start(binary: String, dir: File, rpc: Int, p2p: Int, listen: Boolean, extra: List<String>): NodeSupervisor {
        val supervisor = NodeSupervisor(NodeConfig(File(binary), dir, Chain.Regtest, rpcPort = rpc, p2pPort = p2p, listen = listen, extraArgs = extra), pollIntervalMs = 100)
        supervisor.start()
        val end = System.currentTimeMillis() + 60_000
        while (supervisor.state != NodeState.Running) {
            check(System.currentTimeMillis() < end) { "node did not start: ${supervisor.state} ${supervisor.output.tail(12)}" }
            Thread.sleep(100)
        }
        return supervisor
    }

    private fun withNet(port: Int, standard: Boolean, block: (Net) -> Unit) {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dirA = Files.createTempDirectory("opr-a").toFile()
        val dirB = Files.createTempDirectory("opr-b").toFile()
        val extra = if (standard) listOf("-acceptnonstdtxn=0") else emptyList()
        val a = start(binary!!, dirA, port, port + 1, true, extra)
        val b = start(binary, dirB, port + 4, port + 6, false, extra)
        try {
            val ra = a.rpc.asWalletRpc()
            val rb = b.rpc.asWalletRpc()
            rb.call("addnode", listOf("127.0.0.1:${port + 1}", "onetry"), null)
            ra.call("createwallet", linkedMapOf("wallet_name" to "miner"), null)
            val miner = ra.call("getnewaddress", emptyList<Any?>(), "miner").toString()
            // past every activation height of regtest (attestation 50, intra-block fix 110) and coinbase maturity
            ra.call("generatetoaddress", listOf(130, miner), "miner")
            val net = Net(ra, rb, miner)
            waitSame(net)
            block(net)
        } finally {
            a.stop()
            b.stop()
            dirA.deleteRecursively()
            dirB.deleteRecursively()
        }
    }

    private fun waitSame(net: Net) {
        val end = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < end) {
            val ha = net.a.call("getbestblockhash", emptyList<Any?>(), null)
            val hb = net.b.call("getbestblockhash", emptyList<Any?>(), null)
            if (ha == hb) return
            Thread.sleep(200)
        }
        error("node B did not reach the tip of node A: " + net.b.call("getblockchaininfo", emptyList<Any?>(), null))
    }

    private fun hex(size: Int, seed: Int) = buildString { var x = seed; repeat(size) { x = (x * 1103515245 + 12345) and 0x7fffffff; append("%02x".format((x shr 16) and 0xff)) } }

    private fun pushScript(data: String): String {
        val n = data.length / 2
        val push = when {
            n <= 75 -> "%02x".format(n)
            n <= 255 -> "4c%02x".format(n)
            else -> "4d%02x%02x".format(n and 0xff, n shr 8)
        }
        return "6a" + push + data
    }

    private fun varint(n: Int) = if (n < 253) "%02x".format(n) else "fd%02x%02x".format(n and 0xff, n shr 8)

    private fun le64(v: Long) = (0 until 8).joinToString("") { "%02x".format((v shr (8 * it)) and 0xff) }

    // The RPC cannot express several "data" outputs, so the extra ones are spliced into a one-input raw transaction.
    private fun send(net: Net, vararg data: String): String {
        val utxo = (net.a.call("listunspent", listOf(1), "miner") as List<*>).map { it as Map<*, *> }
            .first { (it["amount"] as Number).toDouble() > 1.0 }
        val raw = net.a.call(
            "createrawtransaction",
            listOf(
                listOf(linkedMapOf("txid" to utxo["txid"], "vout" to utxo["vout"])),
                listOf(linkedMapOf<String, Any?>("data" to data.first()), linkedMapOf<String, Any?>(net.miner to 0.5)),
            ),
            null,
        ).toString()
        val countAt = (4 + 1 + 32 + 4 + 1 + 4) * 2
        assertEquals("02", raw.substring(countAt, countAt + 2))
        val extra = data.drop(1).joinToString("") { val script = pushScript(it); le64(0) + varint(script.length / 2) + script }
        val spliced = raw.substring(0, countAt) + "%02x".format(2 + data.size - 1) +
            raw.substring(countAt + 2, raw.length - 8) + extra + raw.substring(raw.length - 8)
        val funded = net.a.call("fundrawtransaction", listOf(spliced, linkedMapOf("fee_rate" to 10, "changePosition" to 1)), "miner") as Map<*, *>
        val signed = net.a.call("signrawtransactionwithwallet", listOf(funded["hex"]), "miner") as Map<*, *>
        assertEquals(true, signed["complete"])
        return net.a.call("sendrawtransaction", listOf(signed["hex"], 0), null).toString()
    }

    private fun mineAndCompare(net: Net): Map<*, *> {
        val hash = (net.a.call("generatetoaddress", listOf(1, net.miner), "miner") as List<*>).single().toString()
        waitSame(net)
        assertEquals(hash, net.b.call("getbestblockhash", emptyList<Any?>(), null))
        val tips = net.b.call("getchaintips", emptyList<Any?>(), null) as List<*>
        assertTrue(tips.none { (it as Map<*, *>)["status"] == "invalid" })
        return net.b.call("getblock", listOf(hash, 2), null) as Map<*, *>
    }

    private fun dataOutputs(block: Map<*, *>): Int = (block["tx"] as List<*>).sumOf { tx ->
        ((tx as Map<*, *>)["vout"] as List<*>).count { ((it as Map<*, *>)["scriptPubKey"] as Map<*, *>)["type"] == "nulldata" }
    }

    @Test
    fun aTextMessageAndSeveralSizesAreAcceptedByAnotherNode() = withNet(29400, standard = false) { net ->
        val sizes = listOf(80, 520, 1000, 9_999, 10_001, 50_000, 99_000)
        sizes.forEach { send(net, hex(it, it)) }
        val block = mineAndCompare(net)
        assertEquals(sizes.size, dataOutputs(block) - 2) // the coinbase carries two OP_RETURN outputs itself
        assertEquals((net.a.call("getblockcount", emptyList<Any?>(), null)), net.b.call("getblockcount", emptyList<Any?>(), null))
    }

    @Test
    fun chainedTransactionsWithMessagesInOneBlockKeepTheAttestationValid() = withNet(29410, standard = false) { net ->
        repeat(6) { i -> send(net, hex(40 + i, i), hex(60, 100 + i)) }
        val mempool = net.a.call("getrawmempool", emptyList<Any?>(), null) as List<*>
        assertEquals(6, mempool.size)
        val block = mineAndCompare(net)
        assertEquals(12, dataOutputs(block) - 2)
        assertEquals(0, (net.a.call("getrawmempool", emptyList<Any?>(), null) as List<*>).size)
    }

    @Test
    fun aReorgOfABlockWithMessagesIsAcceptedOnBothNodes() = withNet(29420, standard = false) { net ->
        send(net, hex(80, 5))
        send(net, hex(2000, 6))
        val block = mineAndCompare(net)
        val hash = block["hash"].toString()
        net.a.call("invalidateblock", listOf(hash), null)
        net.b.call("invalidateblock", listOf(hash), null)
        assertEquals(129L + 1, (net.a.call("getblockcount", emptyList<Any?>(), null) as Number).toLong() - 0L)
        net.a.call("reconsiderblock", listOf(hash), null)
        net.b.call("reconsiderblock", listOf(hash), null)
        waitSame(net)
        assertEquals(hash, net.b.call("getbestblockhash", emptyList<Any?>(), null))
        // and the chain keeps growing afterwards
        mineAndCompare(net)
    }

    @Test
    fun theNodeWithRealStandardnessRulesAcceptsUpToTheLimitAndRefusesMore() = withNet(29430, standard = true) { net ->
        send(net, hex(80, 1))
        val limit = 99_000
        send(net, hex(limit, 2))
        val refused = try {
            send(net, hex(100_100, 3))
            false
        } catch (e: RpcException) {
            true
        }
        assertTrue(refused)
        val block = mineAndCompare(net)
        assertEquals(2, dataOutputs(block) - 2)
    }

    @Test
    fun twoMessagesInOneTransactionAreWithinTheAggregateLimit() = withNet(29440, standard = true) { net ->
        send(net, hex(30_000, 1), hex(30_000, 2), hex(30_000, 3))
        val block = mineAndCompare(net)
        assertEquals(3, dataOutputs(block) - 2)
        assertFalse((block["tx"] as List<*>).isEmpty())
    }
}
