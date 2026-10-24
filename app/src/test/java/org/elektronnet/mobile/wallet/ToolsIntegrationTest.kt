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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

private const val PASS = "correct horse battery"

private class Party(val tag: String, val rpc: WalletRpc, wordlist: List<String>, entropyByte: Int, dir: File) {
    val main = "dev$tag"
    val store = MemoryEscrowStore()
    val bip39 = Bip39(wordlist)
    val phrase = bip39.fromEntropy(ByteArray(16) { (entropyByte + it).toByte() })
    val manager = EscrowManager(rpc, store, testnet = true, mainWallet = main, walletPrefix = "esc$tag", removeWalletFiles = { File(dir, "regtest/wallets/$it").deleteRecursively() })

    init {
        val result = WalletBootstrap(rpc, bip39, testnet = true, legacy = true, removeWalletFiles = {}, walletName = main).create(phrase, PASS)
        check(result is BootstrapResult.Created) { "bootstrap failed $result" }
    }
}

private class Env(val rpc: WalletRpc, val miner: String, val words: List<String>, val dir: File) {
    val addresses = AddressCheck(testnet = true, hrp = "bcrt")
    fun mine(n: Int) = rpc.call("generatetoaddress", listOf(n, miner), "miner")
    fun fund(address: String, amount: Double) {
        rpc.call("sendtoaddress", linkedMapOf("address" to address, "amount" to amount, "fee_rate" to 10), "miner")
        mine(1)
    }
    fun pool() = rpc.call("getrawmempool", emptyList<Any?>(), null) as List<*>
}

private fun withEnv(port: Int, block: (Env) -> Unit) {
    val binary = System.getenv("ELEKTROND_BIN")
    assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
    val dir = Files.createTempDirectory("tools-it").toFile()
    val supervisor = NodeSupervisor(NodeConfig(File(binary!!), dir, Chain.Regtest, rpcPort = port, p2pPort = port + 1), pollIntervalMs = 100)
    supervisor.start()
    try {
        val end = System.currentTimeMillis() + 60_000
        while (supervisor.state != NodeState.Running) {
            check(System.currentTimeMillis() < end) { "node did not start" }
            Thread.sleep(100)
        }
        val rpc = supervisor.rpc.asWalletRpc(timeoutMs = 120_000)
        rpc.call("createwallet", linkedMapOf("wallet_name" to "miner"), null)
        val miner = rpc.call("getnewaddress", emptyList<Any?>(), "miner").toString()
        rpc.call("generatetoaddress", listOf(110, miner), "miner")
        block(Env(rpc, miner, loadWordlist(), dir))
    } finally {
        supervisor.stop()
        dir.deleteRecursively()
    }
}

private fun <T> expect(block: () -> T): Exception {
    try {
        block()
    } catch (e: Exception) {
        return e
    }
    fail("no exception raised")
    throw IllegalStateException()
}

class EscrowIntegrationTest {
    private fun setUp(env: Env): Triple<Party, Party, Party> {
        val a = Party("A", env.rpc, env.words, 10, env.dir)
        val b = Party("B", env.rpc, env.words, 50, env.dir)
        val c = Party("C", env.rpc, env.words, 90, env.dir)
        val keyA = a.manager.createKey("Deal 1", "Buyer", PASS)
        val keyB = b.manager.createKey("Deal 1", "Seller", PASS)
        val keyC = c.manager.createKey("Deal 1", "Arbiter", PASS)
        a.manager.activate(keyA.id, 2, listOf(Cosigner("Seller", keyB.myKey), Cosigner("Arbiter", keyC.myKey)), PASS)
        b.manager.activate(keyB.id, 2, listOf(Cosigner("Buyer", keyA.myKey), Cosigner("Arbiter", keyC.myKey)), PASS)
        c.manager.activate(keyC.id, 2, listOf(Cosigner("Buyer", keyA.myKey), Cosigner("Seller", keyB.myKey)), PASS)
        return Triple(a, b, c)
    }

    @Test
    fun allPartiesDeriveTheSameEscrow() = withEnv(29010) { env ->
        val (a, b, c) = setUp(env)
        val ra = a.manager.list().single()
        val rb = b.manager.list().single()
        val rc = c.manager.list().single()
        assertEquals(ra.descriptor, rb.descriptor)
        assertEquals(ra.descriptor, rc.descriptor)
        assertEquals(ra.currentAddress, rb.currentAddress)
        assertEquals(ra.currentAddress, rc.currentAddress)
        assertTrue(ra.descriptor!!.startsWith("wsh(sortedmulti(2,"))
        assertFalse(ra.descriptor!!.contains("prv"))
        assertNotEquals(ra.myKey, rb.myKey)
        assertEquals(listOf("devA", "escA-1"), a.manager.walletNames())
    }

    @Test
    fun twoOfThreePaymentFlow() = withEnv(29020) { env ->
        val (a, b, _) = setUp(env)
        val escrow = a.manager.list().single()
        env.fund(escrow.currentAddress!!, 3.0)
        assertEquals(300_000_000L, a.manager.reader(escrow.id).balances().trustedLep)

        val engine = SendEngine(env.rpc, env.addresses, escrow.walletName)
        val rate = FeeSource(env.rpc).options()[1].lepPerVb
        val prepared = engine.prepare(SendRequest(env.miner, 100_000_000L, false, rate))
        val signedA = engine.sign(prepared, PASS)
        assertFalse(signedA.complete)
        assertTrue(env.pool().isEmpty())

        val bTools = PsbtTools(env.rpc) { b.manager.walletNames() }
        val view = bTools.inspect(signedA.psbt)
        assertEquals(PsbtStatus.NeedsSignatures, view.status)
        assertEquals(1, view.signaturesHave)
        assertEquals(2, view.signaturesNeed)
        assertEquals(listOf("escB-1"), view.signableBy)
        assertEquals(2, view.outputs.size)
        assertEquals(1, view.outputs.count { it.owner == "escB-1" })
        assertEquals(1, view.outputs.count { it.owner == null })
        assertEquals(100_000_000L, view.outputs.single { it.owner == null }.amountLep)
        assertEquals(-(100_000_000L + view.feeLep!!), view.netByWallet["escB-1"])
        assertTrue(view.warnings.isEmpty())

        assertEquals(SendFailure.WrongPassphrase, (expect { bTools.sign(signedA.psbt, "escB-1", "wrong passphrase!") } as SendException).failure)
        val signedB = bTools.sign(signedA.psbt, "escB-1", PASS)
        val combined = bTools.combine(listOf(signedA.psbt, signedB))
        val ready = bTools.inspect(combined)
        assertEquals(PsbtStatus.ReadyToFinalize, ready.status)
        assertEquals(SendFailure.NothingToSign, (expect { bTools.sign(combined, "escB-1", PASS) } as SendException).failure)

        val txid = bTools.finalizeAndBroadcast(combined)
        assertTrue(env.pool().contains(txid))
        env.mine(1)
        assertEquals(TxStatus.Confirming, WalletReader(env.rpc, escrow.walletName).transactions().first { it.txid == txid }.status)
        assertEquals(PsbtStatus.Final, bTools.inspect(env.rpc.call("finalizepsbt", listOf(combined, false), null).let { (it as Map<*, *>)["psbt"]?.toString() ?: combined }).status)
    }

    @Test
    fun oneSignatureCannotSpend() = withEnv(29030) { env ->
        val (a, _, _) = setUp(env)
        val escrow = a.manager.list().single()
        env.fund(escrow.currentAddress!!, 1.0)
        val engine = SendEngine(env.rpc, env.addresses, escrow.walletName)
        val prepared = engine.prepare(SendRequest(env.miner, 50_000_000L, false, FeeSource(env.rpc).options()[1].lepPerVb))
        val signed = engine.sign(prepared, PASS)
        val tools = PsbtTools(env.rpc) { a.manager.walletNames() }
        assertEquals(SendFailure.Unknown, (expect { tools.finalizeAndBroadcast(signed.psbt) } as SendException).failure)
        assertEquals(SendFailure.Unknown, (expect { engine.send(prepared, PASS) } as SendException).failure)
        assertTrue(env.pool().isEmpty())
    }

    @Test
    fun backupAndRestore() = withEnv(29040) { env ->
        val (a, _, c) = setUp(env)
        val escrow = c.manager.list().single()
        val backup = c.manager.backupText(escrow.id)
        assertTrue(backup.contains(escrow.descriptor!!))
        env.fund(escrow.currentAddress!!, 1.0)
        assertEquals(EscrowFailure.HasFunds, (expect { c.manager.removeEscrow(escrow.id) } as EscrowException).failure)
        c.manager.removeEscrow(escrow.id, force = true)
        assertTrue(c.manager.list().isEmpty())

        val labels = a.manager.list().single().cosigners.associate { it.key to it.label } + (a.manager.list().single().myKey to "Buyer")
        val restored = c.manager.restore("Deal 1 restored", escrow.descriptor!!, labels, PASS)
        assertEquals(escrow.account, restored.account)
        assertEquals(escrow.descriptor, restored.descriptor)
        assertEquals(escrow.myKey, restored.myKey)
        c.manager.rescan(restored.id, PASS)
        assertEquals(100_000_000L, c.manager.reader(restored.id).balances().trustedLep)
        assertNotEquals(escrow.currentAddress, c.manager.address(restored.id))
        val outsider = Party("D", env.rpc, env.words, 130, env.dir)
        assertEquals(EscrowFailure.NotOurKey, (expect { outsider.manager.restore("x", escrow.descriptor!!, emptyMap(), PASS) } as EscrowException).failure)
        assertEquals(EscrowFailure.DuplicateKey, (expect { a.manager.restore("x", escrow.descriptor!!, emptyMap(), PASS) } as EscrowException).failure)
    }

    @Test
    fun validation() = withEnv(29050) { env ->
        val a = Party("A", env.rpc, env.words, 10, env.dir)
        val b = Party("B", env.rpc, env.words, 50, env.dir)
        val keyA = a.manager.createKey("Deal", "Buyer", PASS)
        val keyB = b.manager.createKey("Deal", "Seller", PASS)
        fun fail(required: Int, others: List<Cosigner>) = (expect { a.manager.activate(keyA.id, required, others, PASS) } as EscrowException).failure
        assertEquals(EscrowFailure.BadThreshold, fail(1, listOf(Cosigner("S", keyB.myKey))))
        assertEquals(EscrowFailure.BadThreshold, fail(3, listOf(Cosigner("S", keyB.myKey))))
        assertEquals(EscrowFailure.DuplicateKey, fail(2, listOf(Cosigner("Me", keyA.myKey))))
        assertEquals(EscrowFailure.DuplicateKey, fail(2, listOf(Cosigner("S", keyB.myKey), Cosigner("S2", keyB.myKey))))
        assertEquals(EscrowFailure.InvalidKey, fail(2, listOf(Cosigner("S", "nonsense"))))
        assertEquals(EscrowFailure.InvalidKey, fail(2, listOf(Cosigner("S", keyB.myKey.dropLast(2) + "xx"))))
        val mainnetStyle = Base58.encodeChecked(byteArrayOf(0x04, 0x88.toByte(), 0xB2.toByte(), 0x1E) + ByteArray(74) { 2 })
        assertEquals(EscrowFailure.WrongNetwork, fail(2, listOf(Cosigner("S", mainnetStyle))))
        assertEquals(EscrowFailure.WrongPassphrase, (expect { a.manager.activate(keyA.id, 2, listOf(Cosigner("S", keyB.myKey)), "wrong passphrase!") } as EscrowException).failure)
        assertEquals(EscrowFailure.NameMissing, (expect { a.manager.createKey("  ", "x", PASS) } as EscrowException).failure)
        a.manager.activate(keyA.id, 2, listOf(Cosigner("S", keyB.myKey)), PASS)
        assertEquals(EscrowFailure.NotKeyOnly, (expect { a.manager.activate(keyA.id, 2, listOf(Cosigner("S", keyB.myKey)), PASS) } as EscrowException).failure)
        assertEquals(EscrowFailure.InvalidDescriptor, (expect { a.manager.parseDescriptor("wsh(multi(2,a,b))") } as EscrowException).failure)
    }

    @Test
    fun psbtInputIsValidated() = withEnv(29060) { env ->
        val tools = PsbtTools(env.rpc) { listOf("miner") }
        for (bad in listOf("", "hello", "cHNidP8", "cHNidP8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
            assertEquals(SendFailure.InvalidPsbt, (expect { tools.inspect(bad) } as SendException).failure)
        }
    }
}

class SweepAndMessageIntegrationTest {
    private val wif = "cNJFgo1driFnPcBdBX8BrJrpxchBWXwXCvNH5SoSkdcF6JXXwHMm"

    private fun deriveAddress(rpc: WalletRpc, descriptor: String): String {
        val info = rpc.call("getdescriptorinfo", listOf(descriptor), null) as Map<*, *>
        return (rpc.call("deriveaddresses", listOf(info["descriptor"].toString()), null) as List<*>).first().toString()
    }

    @Test
    fun sweepsAllKeyTypes() = withEnv(29070) { env ->
        val main = Party("A", env.rpc, env.words, 10, env.dir)
        val uncompressed = Base58.encodeChecked(byteArrayOf(0xef.toByte()) + ByteArray(32) { 0x11 })
        for (d in listOf("pkh($wif)", "wpkh($wif)", "sh(wpkh($wif))", "pkh($uncompressed)")) {
            env.rpc.call("sendtoaddress", linkedMapOf("address" to deriveAddress(env.rpc, d), "amount" to 0.5, "fee_rate" to 10), "miner")
        }
        env.mine(1)
        val engine = SweepEngine(env.rpc, testnet = true, addresses = env.addresses)
        val keys = engine.parseKeys("$wif\n$uncompressed")
        assertEquals(listOf(true, false), keys.map { it.compressed })
        assertEquals(3, keys[0].types.size)
        assertEquals(listOf(SweepType.P2pkh), keys[1].types)

        val scan = engine.scan(keys)
        assertEquals(4, scan.utxos.size)
        assertEquals(200_000_000L, scan.totalLep)
        assertEquals(setOf(SweepType.P2pkh, SweepType.P2wpkh, SweepType.P2shP2wpkh), scan.utxos.map { it.type }.toSet())

        val destination = ReceiveWallet(env.rpc, object : AddressStore { override var current: String? = null }, main.main).newAddress()
        val plan = engine.plan(scan, destination, BigDecimal("5"))
        assertEquals(scan.totalLep - plan.feeLep, plan.amountLep)
        val txid = engine.broadcast(plan)
        assertTrue(env.pool().contains(txid))
        val decoded = env.rpc.call("getrawtransaction", listOf(txid, true), null) as Map<*, *>
        val realVsize = (decoded["vsize"] as Number).toInt()
        val effective = BigDecimal(plan.feeLep).divide(BigDecimal(realVsize), 2, java.math.RoundingMode.HALF_UP)
        assertTrue("effective rate $effective", effective >= BigDecimal("4.5") && effective <= BigDecimal("6"))
        env.mine(1)
        assertEquals(plan.amountLep, WalletReader(env.rpc, main.main).balances().trustedLep)
        assertEquals(SweepFailure.NothingFound, (expect { engine.scan(keys) } as SweepException).failure)
    }

    @Test
    fun sweepRejectsBadInput() = withEnv(29080) { env ->
        val engine = SweepEngine(env.rpc, testnet = true, addresses = env.addresses)
        fun failure(text: String) = (expect { engine.parseKeys(text) } as SweepException).failure
        assertEquals(SweepFailure.NoKeys, failure("  "))
        assertEquals(SweepFailure.InvalidKey, failure(wif.dropLast(1) + "x"))
        assertEquals(SweepFailure.WrongNetwork, failure("L1aW4aubDFB7yfras2S1mN3bqg9nwySY8nkoLmJebSLD5BWv3ENZ"))
        assertEquals(SweepFailure.InvalidKey, failure("p2wpkh:" + Base58.encodeChecked(byteArrayOf(0xef.toByte()) + ByteArray(32) { 0x11 })))
        assertEquals(listOf(SweepType.P2wpkh), engine.parseKeys("p2wpkh:$wif").single().types)
        assertEquals(1, engine.parseKeys("$wif $wif").size)
        val scan = SweepScan(emptyList(), emptyList())
        assertEquals(SweepFailure.InvalidDestination, (expect { engine.plan(scan, "nonsense", BigDecimal.ONE) } as SweepException).failure)
        val key = engine.parseKey(wif)
        val tiny = SweepScan(listOf(key), listOf(SweepUtxo("a".repeat(64), 0, 400L, "0014" + "00".repeat(20), SweepType.P2wpkh, key, null)))
        val dest = Bech32.encode("bcrt", 0, ByteArray(20) { 3 })
        assertEquals(SweepFailure.TooSmall, (expect { engine.plan(tiny, dest, BigDecimal("5")) } as SweepException).failure)
        assertEquals(SweepFailure.NothingFound, (expect { engine.scan(listOf(key)) } as SweepException).failure)
    }

    @Test
    fun signsAndVerifiesMessages() = withEnv(29090) { env ->
        val party = Party("A", env.rpc, env.words, 10, env.dir)
        val tools = MessageTools(env.rpc, env.addresses, party.main)
        val address = ReceiveWallet(env.rpc, object : AddressStore { override var current: String? = null }, party.main).newAddress()
        val signature = tools.sign(address, "hello escrow", PASS)
        val header = java.util.Base64.getDecoder().decode(signature)[0].toInt() and 0xff
        assertTrue("header $header", header in 39..42)
        assertEquals(VerifyResult.Valid, tools.verify(address, signature, "hello escrow"))
        assertEquals(VerifyResult.Invalid, tools.verify(address, signature, "hello escrow!"))
        val other = ReceiveWallet(env.rpc, object : AddressStore { override var current: String? = null }, party.main).newAddress()
        assertNotEquals(address, other)
        assertEquals(VerifyResult.Invalid, tools.verify(other, signature, "hello escrow"))
        assertEquals(VerifyResult.BadFormat, tools.verify(address, "not base64!", "x"))
        assertEquals(VerifyResult.BadFormat, tools.verify(address, signature.dropLast(8), "x"))
        assertEquals(VerifyResult.UnsupportedAddress, tools.verify("nonsense", signature, "x"))
        assertEquals(VerifyResult.Valid, tools.verify(address.uppercase(), signature, "hello escrow"))

        val foreign = Bech32.encode("bcrt", 0, ByteArray(20) { 9 })
        assertEquals(MessageFailure.NotOurAddress, (expect { tools.sign(foreign, "x", PASS) } as MessageException).failure)
        assertEquals(MessageFailure.WrongPassphrase, (expect { tools.sign(address, "x", "wrong passphrase!") } as MessageException).failure)
        assertEquals(MessageFailure.InvalidAddress, (expect { tools.sign("nonsense", "x", PASS) } as MessageException).failure)
        val info = env.rpc.call("getaddressinfo", listOf(address), party.main) as Map<*, *>
        val legacy = deriveAddress(env.rpc, "pkh(${info["pubkey"]})")
        val legacySig = tools.sign(legacy, "legacy", PASS)
        assertEquals(VerifyResult.Valid, tools.verify(legacy, legacySig, "legacy"))
        assertEquals(VerifyResult.Valid, env.rpc.call("verifymessage", listOf(legacy, legacySig, "legacy"), null).let { if (it == true) VerifyResult.Valid else VerifyResult.Invalid })
        val locked = (env.rpc.call("getwalletinfo", emptyList<Any?>(), party.main) as Map<*, *>)["unlocked_until"]
        assertEquals(0L, locked)
    }
}

class EscrowPerWalletIntegrationTest {
    private fun boot(env: Env, name: String, entropyByte: Int) {
        val bip39 = Bip39(env.words)
        val phrase = bip39.fromEntropy(ByteArray(16) { (entropyByte + it).toByte() })
        check(WalletBootstrap(env.rpc, bip39, testnet = true, legacy = true, removeWalletFiles = {}, walletName = name).create(phrase, PASS) is BootstrapResult.Created)
    }

    private fun manager(env: Env, store: EscrowStore, owner: String, removeFiles: (String) -> Unit = { File(env.dir, "regtest/wallets/$it").deleteRecursively() }) =
        EscrowManager(env.rpc, store, testnet = true, mainWallet = owner, walletPrefix = "escrow", removeWalletFiles = removeFiles)

    @Test
    fun everyWalletSeesOnlyItsOwnEscrows() = withEnv(29110) { env ->
        boot(env, "w1", 10)
        boot(env, "w2", 60)
        val store = MemoryEscrowStore()
        val one = manager(env, store, "w1")
        val two = manager(env, store, "w2")
        val first = one.createKey("Deal", "Buyer", PASS)
        assertEquals(listOf(first.id), one.list().map { it.id })
        assertTrue(two.list().isEmpty())
        val second = two.createKey("Other", "Seller", PASS)
        assertNotEquals(first.walletName, second.walletName)
        assertEquals(listOf(second.id), two.list().map { it.id })
        assertEquals(listOf(first.id), one.list().map { it.id })
        assertEquals(EscrowFailure.WalletMissing, (expect { one.get(second.id) } as EscrowException).failure)
        assertEquals("w1", first.owner)
        assertEquals("w2", second.owner)
        assertEquals(0, first.account)
        assertEquals(0, second.account)
        assertNotEquals(first.myKey, second.myKey)
        assertEquals(listOf("w1"), one.walletNames())
    }

    @Test
    fun recordsFromBeforeBelongToTheFirstWallet() {
        val legacy = mapOf("id" to 1, "name" to "Old", "wallet" to "escrow-1", "account" to 0, "myLabel" to "Me", "myKey" to "k", "phase" to "KeyOnly")
        assertEquals(WalletBootstrap.DEFAULT_WALLET, EscrowRecord.fromMap(legacy).owner)
    }

    @Test
    fun anotherEscrowCanBeCreatedAfterDeletingOne() = withEnv(29120) { env ->
        boot(env, "w1", 10)
        val store = MemoryEscrowStore()
        val m = manager(env, store, "w1")
        val first = m.createKey("Deal", "Buyer", PASS)
        m.removeEscrow(first.id)
        assertTrue(m.list().isEmpty())
        val again = m.createKey("Deal 2", "Buyer", PASS)
        assertEquals(1, m.list().size)
        assertEquals(again.id, m.list().single().id)
    }

    @Test
    fun aLeftoverWalletFolderDoesNotBlockANewEscrow() = withEnv(29130) { env ->
        boot(env, "w1", 10)
        val store = MemoryEscrowStore()
        val m = manager(env, store, "w1")
        val first = m.createKey("Deal", "Buyer", PASS)
        env.rpc.call("unloadwallet", listOf(first.walletName), null)
        store.save(emptyList())
        val again = m.createKey("Deal 2", "Buyer", PASS)
        assertNotEquals(first.walletName, again.walletName)
    }

    @Test
    fun aFailedFileRemovalKeepsTheEntryForANewTry() = withEnv(29140) { env ->
        boot(env, "w1", 10)
        val store = MemoryEscrowStore()
        val broken = manager(env, store, "w1") { throw java.io.IOException("locked") }
        val created = broken.createKey("Deal", "Buyer", PASS)
        assertEquals(EscrowFailure.Unknown, (expect { broken.removeEscrow(created.id) } as EscrowException).failure)
        assertEquals(1, broken.list().size)
        manager(env, store, "w1").removeEscrow(created.id)
        assertTrue(broken.list().isEmpty())
    }
}
