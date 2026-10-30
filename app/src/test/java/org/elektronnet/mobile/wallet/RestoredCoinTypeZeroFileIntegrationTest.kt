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

// A wallet file written by another wallet (here with coin type 0 descriptors) is restored as it is: nothing is converted
// to coin type 1370, the coins stay on their addresses and can be spent, and the change goes to the file's own change chain
class RestoredCoinTypeZeroFileIntegrationTest {
    private val phrase = Bip39.parse("legal winner thank year wave sausage worth useful legal winner thank yellow")
    private val pass = "correct horse battery"

    @Test
    fun aWalletFileWithCoinTypeZeroIsRestoredAsItIsAndSpendable() {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dir = Files.createTempDirectory("cointype-file-it").toFile()
        val supervisor = NodeSupervisor(NodeConfig(File(binary!!), dir, Chain.Regtest, rpcPort = 29890, p2pPort = 29891), pollIntervalMs = 100)
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

            // a descriptor wallet of another program: coin type 0, no coin type 1370 anywhere
            val master = Bip32.masterExtendedKey(Bip39.toSeed(phrase), testnet = true)
            rpc.call("createwallet", linkedMapOf("wallet_name" to "qt", "blank" to true), null)
            val requests = listOf(0, 1).map { chain ->
                val desc = "wpkh($master/84h/0h/0h/$chain/*)"
                val info = rpc.call("getdescriptorinfo", listOf(desc), null) as Map<*, *>
                linkedMapOf("desc" to desc + "#" + info["checksum"], "timestamp" to "now", "active" to true, "internal" to (chain == 1), "range" to listOf(0, 50))
            }
            assertTrue(((rpc.call("importdescriptors", listOf(requests), "qt") as List<*>).all { (it as Map<*, *>)["success"] == true }))
            val coinAddress = rpc.call("getnewaddress", emptyList<Any?>(), "qt").toString()
            rpc.call("sendtoaddress", linkedMapOf("address" to coinAddress, "amount" to 6.0, "fee_rate" to 10), "miner")
            rpc.call("generatetoaddress", listOf(1, miner), "miner")
            val backup = File(dir, "qt.dat")
            rpc.call("backupwallet", listOf(backup.path), "qt")
            rpc.call("unloadwallet", listOf("qt"), null)

            // restore it the way the app does and give it a passphrase
            val restore = WalletRestore(rpc) { name -> deleteWalletDir(File(dir, "regtest/wallets/$name")) }
            assertEquals(RestoreResult.Restored("elektron_2", false), restore.restore(backup, "elektron_2"))
            assertEquals(null, restore.protect("elektron_2", pass))
            val paths = ((rpc.call("listdescriptors", emptyList<Any?>(), "elektron_2") as Map<*, *>)["descriptors"] as List<*>).map { (it as Map<*, *>)["desc"].toString() }
            println("descriptors of the restored wallet: " + paths.map { it.substringAfter("[").substringBefore("]").substringAfter("/") })
            assertTrue(paths.none { it.contains("1370h") })
            assertTrue(paths.all { it.contains("/84h/0h/0h]") })

            fun trusted() = WalletReader.lep(((rpc.call("getbalances", emptyList<Any?>(), "elektron_2") as Map<*, *>)["mine"] as Map<*, *>)["trusted"])
            assertEquals(600_000_000L, trusted())
            assertEquals(coinAddress, (rpc.call("listunspent", emptyList<Any?>(), "elektron_2") as List<*>).map { (it as Map<*, *>)["address"].toString() }.single())

            // sending with the same engine the app uses
            val engine = SendEngine(rpc, AddressCheck(testnet = true, hrp = "bcrt"), "elektron_2")
            val prepared = engine.prepare(SendRequest(miner, 250_000_000L, false, FeeSource(rpc).options()[1].lepPerVb))
            val txid = engine.send(prepared, pass)
            rpc.call("generatetoaddress", listOf(1, miner), "miner")
            val tx = rpc.call("getrawtransaction", listOf(txid, true, rpc.call("getbestblockhash", emptyList<Any?>(), null)), null) as Map<*, *>
            val change = (tx["vout"] as List<*>).map { ((it as Map<*, *>)["scriptPubKey"] as Map<*, *>)["address"].toString() }.first { it != miner }
            val info = rpc.call("getaddressinfo", listOf(change), "elektron_2") as Map<*, *>
            println("change goes to: ${info["hdkeypath"]}")
            assertTrue(info["hdkeypath"].toString().contains("/84h/0h/0h/1/"))
            assertTrue(trusted() in 340_000_000L..350_000_000L)
        } finally {
            supervisor.stop()
            dir.deleteRecursively()
        }
    }
}
