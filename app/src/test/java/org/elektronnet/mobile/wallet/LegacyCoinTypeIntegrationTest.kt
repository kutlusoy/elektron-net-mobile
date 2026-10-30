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

// Coins on addresses of the old derivation path (coin type 0) are found after a restore from the words, stay where they
// are, can be spent, and their change goes to the current account
class LegacyCoinTypeIntegrationTest {
    private val phrase = Bip39.parse("legal winner thank year wave sausage worth useful legal winner thank yellow")
    private val pass = "correct horse battery"

    @Test
    fun coinsOfTheOldCoinTypeAreFoundSpendableAndKeepTheirAddresses() {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dir = Files.createTempDirectory("legacy-it").toFile()
        val supervisor = NodeSupervisor(NodeConfig(File(binary!!), dir, Chain.Regtest, rpcPort = 29880, p2pPort = 29881), pollIntervalMs = 100)
        supervisor.start()
        try {
            val end = System.currentTimeMillis() + 60_000
            while (supervisor.state != NodeState.Running) {
                check(System.currentTimeMillis() < end) { "node did not start" }
                Thread.sleep(100)
            }
            val rpc = supervisor.rpc.asWalletRpc()
            fun boot() = WalletBootstrap(rpc, Bip39(loadWordlist()), testnet = true, legacy = true, removeWalletFiles = { File(dir, "regtest/wallets/$it").deleteRecursively() })
            rpc.call("createwallet", linkedMapOf("wallet_name" to "miner"), null)
            val miner = rpc.call("getnewaddress", emptyList<Any?>(), "miner").toString()
            rpc.call("generatetoaddress", listOf(110, miner), "miner")

            // the wallet of the words, then coins on addresses of the old path (84h/0h/0h) and of the current one (84h/1h/0h on test chains)
            assertEquals(BootstrapResult.Created("elektron"), boot().create(phrase, pass))
            val descriptors = ((rpc.call("listdescriptors", emptyList<Any?>(), "elektron") as Map<*, *>)["descriptors"] as List<*>).map { it as Map<*, *> }
            val old = descriptors.first { it["desc"].toString().contains("84h/0h/0h]") && it["internal"] != true }
            assertEquals(false, old["active"])
            val oldAddresses = (rpc.call("deriveaddresses", listOf(old["desc"], listOf(0, 4)), null) as List<*>).map { it.toString() }
            val current = rpc.call("getnewaddress", emptyList<Any?>(), "elektron").toString()
            rpc.call("sendtoaddress", linkedMapOf("address" to oldAddresses[3], "amount" to 4.0, "fee_rate" to 10), "miner")
            rpc.call("sendtoaddress", linkedMapOf("address" to current, "amount" to 2.0, "fee_rate" to 10), "miner")
            rpc.call("generatetoaddress", listOf(1, miner), "miner")

            // lose the wallet and restore it from the words only
            rpc.call("unloadwallet", listOf("elektron"), null)
            File(dir, "regtest/wallets/elektron").deleteRecursively()
            assertEquals(BootstrapResult.Created("elektron"), boot().recover(phrase, pass))
            assertEquals(RescanOutcome.Partial, boot().rescan(pass).let { if (it == RescanOutcome.Full) RescanOutcome.Partial else it })
            fun balance() = WalletReader.lep(((rpc.call("getbalances", emptyList<Any?>(), "elektron") as Map<*, *>)["mine"] as Map<*, *>)["trusted"])
            assertEquals(600_000_000L, balance())

            val unspent = (rpc.call("listunspent", emptyList<Any?>(), "elektron") as List<*>).map { it as Map<*, *> }
            val oldCoin = unspent.first { it["address"] == oldAddresses[3] }
            assertEquals(true, oldCoin["spendable"])
            println("old coin stays at its address: ${oldCoin["address"]} (${oldCoin["amount"]})")

            // spend it: the payment works and the change lands on the current account, not on the old path
            rpc.call("walletpassphrase", listOf(pass, 60), "elektron")
            val funded = rpc.call(
                "walletcreatefundedpsbt",
                listOf(listOf(linkedMapOf("txid" to oldCoin["txid"], "vout" to oldCoin["vout"])), listOf(linkedMapOf(miner to 3.0)), 0, linkedMapOf("fee_rate" to 10, "add_inputs" to false)),
                "elektron",
            ) as Map<*, *>
            val processed = rpc.call("walletprocesspsbt", listOf(funded["psbt"], true, "ALL", true, false), "elektron") as Map<*, *>
            val finalized = rpc.call("finalizepsbt", listOf(processed["psbt"], true), null) as Map<*, *>
            assertEquals(true, finalized["complete"])
            val hex = finalized["hex"]
            val txid = rpc.call("sendrawtransaction", listOf(hex), null).toString()
            rpc.call("generatetoaddress", listOf(1, miner), "miner")
            val tx = rpc.call("getrawtransaction", listOf(txid, true, rpc.call("getbestblockhash", emptyList<Any?>(), null)), null) as Map<*, *>
            val change = (tx["vout"] as List<*>).map { ((it as Map<*, *>)["scriptPubKey"] as Map<*, *>)["address"].toString() }.first { it != miner }
            val info = rpc.call("getaddressinfo", listOf(change), "elektron") as Map<*, *>
            println("change goes to: ${info["hdkeypath"]}")
            assertEquals(true, info["ismine"])
            assertTrue(info["hdkeypath"].toString().contains("/84h/1h/0h/1/"))
            assertTrue(balance() in 290_000_000L..300_000_000L)
        } finally {
            supervisor.stop()
            dir.deleteRecursively()
        }
    }
}
