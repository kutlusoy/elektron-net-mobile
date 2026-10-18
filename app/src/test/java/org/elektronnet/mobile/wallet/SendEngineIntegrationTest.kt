package org.elektronnet.mobile.wallet

import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import org.elektronnet.mobile.node.Chain
import org.elektronnet.mobile.node.NodeConfig
import org.elektronnet.mobile.node.NodeState
import org.elektronnet.mobile.node.NodeSupervisor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

class FeePresetsTest {
    @Test
    fun presetsAreMonotoneAndRespectTheFloor() {
        val floor = BigDecimal("1.0")
        val none = FeePresets.derive(emptyMap(), floor)
        assertEquals(listOf(FeeLevel.Slow, FeeLevel.Normal, FeeLevel.Fast), none.map { it.level })
        assertTrue(none.all { it.lepPerVb == BigDecimal("1.0") && !it.estimated })
        val mixed = FeePresets.derive(mapOf(FeeLevel.Slow to BigDecimal("0.5"), FeeLevel.Normal to BigDecimal("3.04"), FeeLevel.Fast to BigDecimal("2.0")), floor)
        assertEquals(listOf("1.0", "3.1", "3.1"), mixed.map { it.lepPerVb.toPlainString() })
        assertEquals(listOf(true, true, true), mixed.map { it.estimated })
    }

    @Test
    fun unitConversion() {
        assertEquals(0, BigDecimal("2").compareTo(FeePresets.lepPerVbFromBtcPerKvb(BigDecimal("0.00002"))))
        assertEquals(null, FeePresets.lepPerVbFromBtcPerKvb("x"))
        assertEquals(0, BigDecimal("0.00002").compareTo(FeePresets.btcPerKvb(BigDecimal("2"))))
    }
}

class SendEngineIntegrationTest {
    private val pass = "correct horse battery"
    private val phrase = Bip39.parse("legal winner thank year wave sausage worth useful legal winner thank yellow")

    private class Env(val rpc: WalletRpc, val engine: SendEngine, val minerAddress: String)

    private fun withEnv(port: Int, block: (Env) -> Unit) {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dir = Files.createTempDirectory("send-it").toFile()
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
            val own = ReceiveWallet(rpc, object : AddressStore { override var current: String? = null }).newAddress()
            rpc.call("sendtoaddress", linkedMapOf("address" to own, "amount" to 5.0, "fee_rate" to 10), "miner")
            rpc.call("generatetoaddress", listOf(1, miner), "miner")
            block(Env(rpc, SendEngine(rpc, AddressCheck(testnet = true, hrp = "bcrt")), miner))
        } finally {
            supervisor.stop()
            dir.deleteRecursively()
        }
    }

    private fun failure(block: () -> Unit): SendFailure {
        try {
            block()
        } catch (e: SendException) {
            return e.failure
        }
        fail("no failure raised")
        throw IllegalStateException()
    }

    private fun locked(env: Env) = (env.rpc.call("getwalletinfo", emptyList<Any?>(), "elektron") as Map<*, *>)["unlocked_until"] == 0L

    private fun rate(env: Env) = FeeSource(env.rpc).options()[1].lepPerVb

    @Test
    fun sendsAndRelocks() = withEnv(28832) { env ->
        val request = SendRequest(env.minerAddress, 150_000_000L, false, rate(env))
        val prepared = env.engine.prepare(request)
        val review = prepared.review
        assertEquals(150_000_000L, review.amountLep)
        assertTrue(review.feeLep > 0 && review.changeLep > 0)
        assertEquals(review.amountLep + review.feeLep, review.totalLep)
        assertEquals(5L * 100_000_000L, review.amountLep + review.feeLep + review.changeLep)
        assertTrue(locked(env))

        val txid = env.engine.send(prepared, pass)
        assertEquals(64, txid.length)
        assertTrue(locked(env))
        val pool = env.rpc.call("getrawmempool", emptyList<Any?>(), null) as List<*>
        assertTrue(pool.contains(txid))
        val sent = WalletReader(env.rpc).transactions().first { it.txid == txid }
        assertEquals(TxKind.Sent, sent.kind)
        assertEquals(-(review.amountLep + review.feeLep), sent.netLep)
    }

    @Test
    fun wrongPassphraseSendsNothing() = withEnv(28842) { env ->
        val prepared = env.engine.prepare(SendRequest(env.minerAddress, 100_000_000L, false, rate(env)))
        assertEquals(SendFailure.WrongPassphrase, failure { env.engine.send(prepared, "wrong passphrase!") })
        assertTrue(locked(env))
        assertEquals(emptyList<Any?>(), env.rpc.call("getrawmempool", emptyList<Any?>(), null))
    }

    @Test
    fun maxSpendsEverythingMinusFee() = withEnv(28852) { env ->
        val prepared = env.engine.prepare(SendRequest(env.minerAddress, null, true, rate(env)))
        assertEquals(0L, prepared.review.changeLep)
        assertEquals(5L * 100_000_000L, prepared.review.amountLep + prepared.review.feeLep)
        assertEquals(prepared.grossLep, prepared.review.totalLep)
        env.engine.send(prepared, pass)
        env.rpc.call("generatetoaddress", listOf(1, env.minerAddress), "miner")
        assertEquals(0L, WalletReader(env.rpc).balances().trustedLep)
    }

    @Test
    fun rejectsBadRequests() = withEnv(28862) { env ->
        val r = rate(env)
        assertEquals(SendFailure.InsufficientFunds, failure { env.engine.prepare(SendRequest(env.minerAddress, 100L * 100_000_000L, false, r)) })
        assertEquals(SendFailure.InvalidAddress, failure { env.engine.prepare(SendRequest("nonsense", 1_000_000L, false, r)) })
        assertEquals(SendFailure.ForeignNetwork, failure { env.engine.prepare(SendRequest(Bech32.encode("bc", 0, ByteArray(20) { 1 }), 1_000_000L, false, r)) })
        assertEquals(SendFailure.LegacyNotConfirmed, failure { env.engine.prepare(SendRequest("mipcBbFg9gMiCh81Kj8tqqdgoZub1ZJRfn", 1_000_000L, false, r)) })
        assertEquals(SendFailure.FeeTooLow, failure { env.engine.prepare(SendRequest(env.minerAddress, 1_000_000L, false, BigDecimal("0.01"))) })
        assertEquals(SendFailure.InvalidAmount, failure { env.engine.prepare(SendRequest(env.minerAddress, 0L, false, r)) })
        assertTrue(locked(env))
    }

    @Test
    fun validationCatchesTamperedPsbts() = withEnv(28872) { env ->
        val r = rate(env)
        val prepared = env.engine.prepare(SendRequest(env.minerAddress, 100_000_000L, false, r))

        val otherAddress = env.rpc.call("getnewaddress", emptyList<Any?>(), "miner").toString()
        val wrongDestination = PreparedSend(prepared.psbt, prepared.request.copy(address = otherAddress), prepared.review, prepared.policyFloor, prepared.grossLep)
        assertEquals(SendFailure.PsbtMismatch, failure { env.engine.validate(wrongDestination) })

        val wrongAmount = PreparedSend(prepared.psbt, prepared.request, prepared.review, prepared.policyFloor, prepared.grossLep + 1)
        assertEquals(SendFailure.PsbtMismatch, failure { env.engine.validate(wrongAmount) })

        val stolenChange = env.rpc.call(
            "walletcreatefundedpsbt",
            linkedMapOf(
                "inputs" to emptyList<Any?>(),
                "outputs" to listOf(linkedMapOf(env.minerAddress to BigDecimal("1.0"))),
                "locktime" to 0,
                "options" to linkedMapOf("fee_rate" to r, "changeAddress" to otherAddress),
            ),
            "elektron",
        ) as Map<*, *>
        val diverted = PreparedSend(stolenChange["psbt"].toString(), prepared.request, prepared.review, prepared.policyFloor, 100_000_000L)
        assertEquals(SendFailure.PsbtMismatch, failure { env.engine.validate(diverted) })

        assertEquals(SendFailure.PsbtMismatch, failure { env.engine.send(wrongAmount, pass) })
        assertTrue(locked(env))
        assertFalse((env.rpc.call("getrawmempool", emptyList<Any?>(), null) as List<*>).isNotEmpty())
    }

    @Test
    fun feeOptionsAreUsable() = withEnv(28882) { env ->
        val options = FeeSource(env.rpc).options()
        assertEquals(3, options.size)
        assertTrue(options[0].lepPerVb <= options[1].lepPerVb && options[1].lepPerVb <= options[2].lepPerVb)
        assertTrue(options.all { it.lepPerVb >= FeeSource(env.rpc).floor() })
    }
}
