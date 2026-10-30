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

// Mining rewards of a restored old backup, recovered from the UTXO set, must stay immature until they mature.
class RecoveredCoinbaseIntegrationTest {
    private fun balances(rpc: WalletRpc, wallet: String): Pair<Long, Long> {
        val mine = (rpc.call("getbalances", emptyList<Any?>(), wallet) as Map<*, *>)["mine"] as Map<*, *>
        return WalletReader.lep(mine["trusted"]) to WalletReader.lep(mine["immature"])
    }

    @Test
    fun recoveredMiningRewardsStayImmatureAndCannotBeSpent() {
        val bin = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", bin != null && File(bin).canExecute())
        val dir = Files.createTempDirectory("cb-it").toFile()
        val backups = Files.createTempDirectory("cb-backups").toFile()
        val port = 29830
        val supervisor = NodeSupervisor(NodeConfig(File(bin!!), dir, Chain.Regtest, rpcPort = port, p2pPort = port + 1), pollIntervalMs = 100)
        try {
            supervisor.start()
            val end = System.currentTimeMillis() + 90_000
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
            rpc.call("backupwallet", listOf(File(backups, "old.dat").path), "w1")
            rpc.call("generatetoaddress", listOf(450, miner), "miner")
            assertTrue(((rpc.call("getblockchaininfo", emptyList<Any?>(), null) as Map<*, *>)["pruneheight"] as Number).toInt() > 200)
            rpc.call("generatetoaddress", listOf(20, a1), "w1")
            rpc.call("sendtoaddress", linkedMapOf("address" to a1, "amount" to 3.0, "fee_rate" to 10), "miner")
            rpc.call("generatetoaddress", listOf(1, miner), "miner")

            rpc.call("restorewallet", listOf("w3", File(backups, "old.dat").path), null)
            val live = balances(rpc, "w1")
            val restored = balances(rpc, "w3")
            assertTrue("rewards are immature", live.second > 0)
            assertEquals("balances equal those of the live wallet", live, restored)

            val sent = (rpc.call("sendall", linkedMapOf("recipients" to listOf(miner), "fee_rate" to 10), "w3") as Map<*, *>)["txid"].toString()
            assertTrue("the spend is accepted", (rpc.call("getrawmempool", emptyList<Any?>(), null) as List<*>).contains(sent))

            rpc.call("unloadwallet", listOf("w3"), null)
            rpc.call("loadwallet", listOf("w3"), null)
            assertEquals("immature part survives a reload", live.second, balances(rpc, "w3").second)
        } finally {
            supervisor.stop()
            dir.deleteRecursively()
            backups.deleteRecursively()
        }
    }
}
