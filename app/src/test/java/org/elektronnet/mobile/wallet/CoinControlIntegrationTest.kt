package org.elektronnet.mobile.wallet

import java.io.File
import java.nio.file.Files
import org.elektronnet.mobile.node.Chain
import org.elektronnet.mobile.node.NodeConfig
import org.elektronnet.mobile.node.NodeState
import org.elektronnet.mobile.node.NodeSupervisor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class CoinControlIntegrationTest {
    private val pass = "correct horse battery"
    private val phrase = Bip39.parse("legal winner thank year wave sausage worth useful legal winner thank yellow")

    private class Env(val rpc: WalletRpc, val send: SendEngine, val coins: CoinControl, val miner: String, val own: String)

    private fun withEnv(port: Int, block: (Env) -> Unit) {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dir = Files.createTempDirectory("coin-it").toFile()
        val supervisor = NodeSupervisor(NodeConfig(File(binary!!), dir, Chain.Regtest, rpcPort = port, p2pPort = port + 1), pollIntervalMs = 100)
        supervisor.start()
        try {
            val end = System.currentTimeMillis() + 60_000
            while (supervisor.state != NodeState.Running) {
                check(System.currentTimeMillis() < end) { "node did not start" }
                Thread.sleep(100)
            }
            val rpc = supervisor.rpc.asWalletRpc()
            WalletBootstrap(rpc, Bip39(loadWordlist()), testnet = true, legacy = true, removeWalletFiles = {}).create(phrase, pass)
            rpc.call("createwallet", linkedMapOf("wallet_name" to "miner"), null)
            val miner = rpc.call("getnewaddress", emptyList<Any?>(), "miner").toString()
            rpc.call("generatetoaddress", listOf(110, miner), "miner")
            val receive = ReceiveWallet(rpc, object : AddressStore { override var current: String? = null })
            val own = receive.newAddress()
            rpc.call("sendtoaddress", linkedMapOf("address" to own, "amount" to 2.0, "fee_rate" to 10), "miner")
            rpc.call("sendtoaddress", linkedMapOf("address" to receive.newAddress(), "amount" to 3.0, "fee_rate" to 10), "miner")
            rpc.call("generatetoaddress", listOf(1, miner), "miner")
            block(Env(rpc, SendEngine(rpc, AddressCheck(testnet = true, hrp = "bcrt")), CoinControl(rpc), miner, own))
        } finally {
            supervisor.stop()
            dir.deleteRecursively()
        }
    }

    private fun rate(env: Env) = FeeSource(env.rpc).options()[1].lepPerVb

    @Test
    fun listsCoinsAndAddresses() = withEnv(28932) { env ->
        val coins = env.coins.coins()
        assertEquals(listOf(300_000_000L, 200_000_000L), coins.map { it.amountLep })
        assertTrue(coins.none { it.frozen })
        val rows = env.coins.addresses()
        assertTrue(rows.any { it.address == env.own && it.txCount == 1 })
    }

    @Test
    fun freezingKeepsACoinOutOfAutomaticSelection() = withEnv(28942) { env ->
        val big = env.coins.coins().first()
        env.coins.setFrozen(listOf(big), true)
        val after = env.coins.coins()
        assertEquals(2, after.size)
        assertTrue(after.first { it.id == big.id }.frozen)
        assertEquals(big.amountLep, after.first { it.id == big.id }.amountLep)
        val prepared = env.send.prepare(SendRequest(env.miner, 150_000_000L, false, rate(env)))
        assertEquals(1, prepared.review.inputs)
        env.coins.setFrozen(listOf(big), false)
        assertFalse(env.coins.coins().any { it.frozen })
    }

    @Test
    fun selectedCoinsAreTheOnlyInputs() = withEnv(28952) { env ->
        val small = env.coins.coins().last()
        val prepared = env.send.prepare(SendRequest(env.miner, 100_000_000L, false, rate(env), coins = listOf(small)))
        assertEquals(1, prepared.review.inputs)
        val decoded = env.rpc.call("decodepsbt", listOf(prepared.psbt), null) as Map<*, *>
        val vin = ((decoded["tx"] as Map<*, *>)["vin"] as List<*>).single() as Map<*, *>
        assertEquals(small.txid, vin["txid"])
        val txid = env.send.send(prepared, pass)
        assertEquals(64, txid.length)
    }

    @Test
    fun maxWithSelectionSpendsOnlyThatSelection() = withEnv(28962) { env ->
        val big = env.coins.coins().first()
        val prepared = env.send.prepare(SendRequest(env.miner, null, true, rate(env), coins = listOf(big)))
        assertEquals(0L, prepared.review.changeLep)
        assertEquals(big.amountLep, prepared.review.amountLep + prepared.review.feeLep)
    }

    @Test
    fun selectionTooSmallFailsInsteadOfPullingInOtherCoins() = withEnv(28972) { env ->
        val small = env.coins.coins().last()
        try {
            env.send.prepare(SendRequest(env.miner, 250_000_000L, false, rate(env), coins = listOf(small)))
            throw AssertionError("expected a failure")
        } catch (e: SendException) {
            assertEquals(SendFailure.InsufficientFunds, e.failure)
        }
    }

    @Test
    fun labelsStickToAddresses() = withEnv(28982) { env ->
        env.coins.setLabel(env.own, " Alice ")
        assertEquals("Alice", env.coins.addresses().first { it.address == env.own }.label)
    }

    @Test
    fun feeBumpReplacesAPendingPayment() = withEnv(28992) { env ->
        val prepared = env.send.prepare(SendRequest(env.miner, 100_000_000L, false, rate(env)))
        val oldTxid = env.send.send(prepared, pass)
        val bumper = FeeBumper(env.rpc)
        val suggested = bumper.suggestedRate(oldTxid)
        assertTrue(suggested > bumper.currentRate(oldTxid))
        val (oldFee, estimated) = bumper.estimate(oldTxid, suggested)
        assertTrue(estimated > oldFee)
        val newTxid = bumper.bump(oldTxid, suggested, pass)
        val pool = env.rpc.call("getrawmempool", emptyList<Any?>(), null) as List<*>
        assertTrue(pool.contains(newTxid))
        assertFalse(pool.contains(oldTxid))
        try {
            bumper.bump(newTxid, bumper.currentRate(newTxid), pass)
            throw AssertionError("expected FeeTooLow")
        } catch (e: BumpException) {
            assertEquals(BumpFailure.FeeTooLow, e.failure)
        }
        assertTrue((env.rpc.call("getwalletinfo", emptyList<Any?>(), "elektron") as Map<*, *>)["unlocked_until"] == 0L)
    }

    @Test
    fun feeBumpNeedsTheRightPassphrase() = withEnv(29002) { env ->
        val oldTxid = env.send.send(env.send.prepare(SendRequest(env.miner, 100_000_000L, false, rate(env))), pass)
        val bumper = FeeBumper(env.rpc)
        try {
            bumper.bump(oldTxid, bumper.suggestedRate(oldTxid), "wrong passphrase!")
            throw AssertionError("expected WrongPassphrase")
        } catch (e: BumpException) {
            assertEquals(BumpFailure.WrongPassphrase, e.failure)
        }
        assertTrue((env.rpc.call("getrawmempool", emptyList<Any?>(), null) as List<*>).contains(oldTxid))
    }

    @Test
    fun pagedHistoryMatchesTheFullList() = withEnv(29012) { env ->
        repeat(3) {
            env.send.send(env.send.prepare(SendRequest(env.miner, 10_000_000L, false, rate(env))), pass)
            env.rpc.call("generatetoaddress", listOf(1, env.miner), "miner")
        }
        val reader = WalletReader(env.rpc)
        val paged = reader.allTransactions(pageSize = 2)
        val whole = reader.allTransactions()
        assertEquals(whole.map { it.txid }, paged.map { it.txid })
        assertEquals(5, paged.size)
        assertEquals(listOf(TxKind.Sent, TxKind.Sent, TxKind.Sent), paged.filter { it.kind == TxKind.Sent }.map { it.kind })
        assertEquals(paged.map { it.timeSeconds }.sortedDescending(), paged.map { it.timeSeconds })
    }

    @Test
    fun newWalletsAvoidAddressReuse() = withEnv(29022) { env ->
        val info = env.rpc.call("getwalletinfo", emptyList<Any?>(), "elektron") as Map<*, *>
        assertEquals(true, info["avoid_reuse"])
        assertTrue(AddressReuse.ensure(env.rpc, "elektron"))
    }

    @Test
    fun existingWalletsGetTheFlagOnFirstUse() = withEnv(29032) { env ->
        env.rpc.call("setwalletflag", listOf("avoid_reuse", false), "elektron")
        assertEquals(false, (env.rpc.call("getwalletinfo", emptyList<Any?>(), "elektron") as Map<*, *>)["avoid_reuse"])
        env.send.prepare(SendRequest(env.miner, 10_000_000L, false, rate(env)))
        assertEquals(true, (env.rpc.call("getwalletinfo", emptyList<Any?>(), "elektron") as Map<*, *>)["avoid_reuse"])
    }

    @Test
    fun coinsOnAReusedAddressStayOutOfAutomaticSpending() = withEnv(29042) { env ->
        env.send.send(env.send.prepare(SendRequest(env.miner, 100_000_000L, false, rate(env))), pass)
        env.rpc.call("generatetoaddress", listOf(1, env.miner), "miner")
        env.rpc.call("sendtoaddress", linkedMapOf("address" to env.own, "amount" to 1.5, "fee_rate" to 10), "miner")
        env.rpc.call("generatetoaddress", listOf(1, env.miner), "miner")
        val balances = WalletReader(env.rpc).balances()
        assertEquals(150_000_000L, balances.usedLep)
        val coin = env.coins.coins().first { it.reused }
        assertEquals(150_000_000L, coin.amountLep)
        assertEquals(env.own, coin.address)
        val prepared = env.send.prepare(SendRequest(env.miner, null, true, rate(env)))
        assertEquals(balances.trustedLep, prepared.review.amountLep + prepared.review.feeLep)
        val manual = env.send.prepare(SendRequest(env.miner, null, true, rate(env), coins = listOf(coin)))
        assertEquals(coin.amountLep, manual.review.amountLep + manual.review.feeLep)
    }

    @Test
    fun reusedCoinsAreSpentFirstAndChangeGoesToANewAddress() = withEnv(29052) { env ->
        env.send.send(env.send.prepare(SendRequest(env.miner, 100_000_000L, false, rate(env))), pass)
        env.rpc.call("generatetoaddress", listOf(1, env.miner), "miner")
        env.rpc.call("sendtoaddress", linkedMapOf("address" to env.own, "amount" to 1.5, "fee_rate" to 10), "miner")
        env.rpc.call("generatetoaddress", listOf(1, env.miner), "miner")
        val reused = env.coins.coins().first { it.reused }
        val prepared = env.send.prepare(SendRequest(env.miner, 50_000_000L, false, rate(env)))
        assertEquals(1, prepared.review.inputs)
        val decoded = env.rpc.call("decodepsbt", listOf(prepared.psbt), null) as Map<*, *>
        val vin = ((decoded["tx"] as Map<*, *>)["vin"] as List<*>).single() as Map<*, *>
        assertEquals(reused.txid, vin["txid"])
        val outputs = ((decoded["tx"] as Map<*, *>)["vout"] as List<*>).map { ((it as Map<*, *>)["scriptPubKey"] as Map<*, *>)["address"] }
        assertTrue(outputs.none { it == env.own })
        val tooBig = env.send.prepare(SendRequest(env.miner, 200_000_000L, false, rate(env)))
        assertTrue(tooBig.review.inputs >= 1)
        assertFalse(env.rpc.call("decodepsbt", listOf(tooBig.psbt), null).toString().contains(reused.txid) && tooBig.review.inputs == 1)
    }
}
