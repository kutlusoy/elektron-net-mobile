package org.elektronnet.mobile.wallet

import java.io.File
import java.nio.file.Files
import org.elektronnet.mobile.node.Chain
import org.elektronnet.mobile.node.NodeConfig
import org.elektronnet.mobile.node.NodeState
import org.elektronnet.mobile.node.NodeSupervisor
import org.elektronnet.mobile.ur.Ur
import org.elektronnet.mobile.ur.UrDecoder
import org.elektronnet.mobile.ur.UrEncoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class WatchOnlyIntegrationTest {
    private val pass = "correct horse battery"
    private val phrase = Bip39.parse("legal winner thank year wave sausage worth useful legal winner thank yellow")

    private fun withNode(port: Int, block: (WalletRpc, File) -> Unit) {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dir = Files.createTempDirectory("watch-it").toFile()
        val supervisor = NodeSupervisor(NodeConfig(File(binary!!), dir, Chain.Regtest, rpcPort = port, p2pPort = port + 1), pollIntervalMs = 100)
        supervisor.start()
        try {
            val end = System.currentTimeMillis() + 60_000
            while (supervisor.state != NodeState.Running) {
                check(System.currentTimeMillis() < end) { "node did not start" }
                Thread.sleep(100)
            }
            block(supervisor.rpc.asWalletRpc(), dir)
        } finally {
            supervisor.stop()
            dir.deleteRecursively()
        }
    }

    private fun bootstrap(rpc: WalletRpc, dir: File, name: String) = WalletBootstrap(
        rpc, Bip39(loadWordlist()), testnet = true, legacy = true,
        removeWalletFiles = { File(dir, "regtest/wallets/$it").deleteRecursively() }, walletName = name,
    )

    // the public descriptors of the signing wallet are what a signing device would hand over as its key
    private fun publicDescriptors(rpc: WalletRpc, wallet: String): String =
        ((rpc.call("listdescriptors", listOf(false), wallet) as Map<*, *>)["descriptors"] as List<*>)
            .map { it as Map<*, *> }.filter { it["active"] == true }.joinToString("\n") { it["desc"].toString() }

    private fun ur(psbt: String): String {
        val encoder = UrEncoder(Ur.ofBytes(Ur.PSBT, java.util.Base64.getDecoder().decode(psbt)), 60)
        val decoder = UrDecoder()
        var guard = 0
        while (!decoder.isComplete && guard++ < 5000) decoder.receive(encoder.nextPart())
        assertTrue(decoder.isComplete)
        return java.util.Base64.getEncoder().encodeToString(decoder.result!!.bytes())
    }

    @Test
    fun aWatchOnlyWalletSharesTheAddressesOfTheSignerAndNeedsItsSignature() = withNode(29340) { rpc, dir ->
        assertEquals(BootstrapResult.Created("signer"), bootstrap(rpc, dir, "signer").create(phrase, pass))
        val parsed = WatchOnlyInput(testnet = true).parse(publicDescriptors(rpc, "signer")) as WatchParse.Ok
        val result = bootstrap(rpc, dir, "watch").createWatchOnly(parsed.source)
        assertEquals(BootstrapResult.Created("watch"), result)
        val info = rpc.call("getwalletinfo", emptyList<Any?>(), "watch") as Map<*, *>
        assertEquals(false, info["private_keys_enabled"])
        val first = rpc.call("getnewaddress", emptyList<Any?>(), "watch").toString()
        assertEquals(rpc.call("getnewaddress", emptyList<Any?>(), "signer").toString(), first)

        rpc.call("createwallet", linkedMapOf("wallet_name" to "miner"), null)
        val miner = rpc.call("getnewaddress", emptyList<Any?>(), "miner").toString()
        rpc.call("generatetoaddress", listOf(110, miner), "miner")
        rpc.call("sendtoaddress", linkedMapOf("address" to first, "amount" to 2.0, "fee_rate" to 10), "miner")
        rpc.call("generatetoaddress", listOf(1, miner), "miner")
        assertEquals(200_000_000L, WalletReader(rpc, "watch").balances().trustedLep)

        val check = AddressCheck(testnet = true, hrp = "bcrt")
        val prepared = SendEngine(rpc, check, "watch").prepare(SendRequest(miner, 100_000_000L, false, FeeSource(rpc).options()[1].lepPerVb))
        val tools = PsbtTools(rpc) { listOf("watch", "signer") }
        val unsigned = ur(prepared.psbt)
        assertEquals(prepared.psbt, unsigned)
        assertEquals(PsbtStatus.NeedsSignatures, tools.inspect(unsigned).status)
        val signed = ur(tools.sign(unsigned, "signer", pass))
        assertTrue(tools.inspect(signed).let { it.status == PsbtStatus.ReadyToFinalize || it.status == PsbtStatus.Final })
        assertEquals(64, tools.finalizeAndBroadcast(signed).length)
        rpc.call("generatetoaddress", listOf(1, miner), "miner")
        assertTrue(WalletReader(rpc, "watch").balances().trustedLep < 200_000_000L)
    }

    @Test
    fun theKeyShownAsQrCreatesTheSameWatchOnlyWallet() = withNode(29370) { rpc, dir ->
        assertEquals(BootstrapResult.Created("signer"), bootstrap(rpc, dir, "signer").create(phrase, pass))
        val key = WalletExport(rpc, "signer").watchKey()!!
        assertTrue(key.startsWith("[") && key.contains("pub") && !key.contains("/*") && !key.contains("prv"))
        val parsed = WatchOnlyInput(testnet = true).parse(key) as WatchParse.Ok
        assertEquals(BootstrapResult.Created("watch"), bootstrap(rpc, dir, "watch").createWatchOnly(parsed.source))
        assertEquals(rpc.call("getnewaddress", emptyList<Any?>(), "signer").toString(), rpc.call("getnewaddress", emptyList<Any?>(), "watch").toString())
        val change = { w: String -> rpc.call("getrawchangeaddress", emptyList<Any?>(), w).toString() }
        assertEquals(change("signer"), change("watch"))
    }

    @Test
    fun aSingleAddressCanBeWatchedAndIsScannedWithoutAPassphrase() = withNode(29350) { rpc, dir ->
        rpc.call("createwallet", linkedMapOf("wallet_name" to "miner"), null)
        val miner = rpc.call("getnewaddress", emptyList<Any?>(), "miner").toString()
        rpc.call("generatetoaddress", listOf(110, miner), "miner")
        rpc.call("sendtoaddress", linkedMapOf("address" to miner, "amount" to 1.5, "fee_rate" to 10), "miner")
        val target = rpc.call("getnewaddress", linkedMapOf("address_type" to "bech32"), "miner").toString()
        rpc.call("sendtoaddress", linkedMapOf("address" to target, "amount" to 3.0, "fee_rate" to 10), "miner")
        rpc.call("generatetoaddress", listOf(1, miner), "miner")
        val b = bootstrap(rpc, dir, "single")
        assertEquals(BootstrapResult.Created("single"), b.createWatchOnly(WatchSource.Address(target)))
        assertTrue(b.rescan("").let { it == RescanOutcome.Full || it == RescanOutcome.Partial })
        val store = object : AddressStore { override var current: String? = null }
        assertEquals(target, ReceiveWallet(rpc, store, "single").currentAddress())
        assertEquals(target, ReceiveWallet(rpc, store, "single").newAddress())
        assertEquals(300_000_000L, WalletReader(rpc, "single").balances().trustedLep)
    }

    @Test
    fun privateKeysAreNeverImportedIntoAWatchOnlyWallet() = withNode(29360) { rpc, dir ->
        val b = bootstrap(rpc, dir, "evil")
        val xprv = "tprv8ZgxMBicQKsPd1QwsGgzfu2pcPYBBnPLm8WLgwCW6gnBe6FUyPSvUM9bUF8Fg8cNQaYRHFqCUKMkU4xWtBbV3PdpBCZkQTzUhdUqwUh3LPW"
        val outcome = b.createWatchOnly(WatchSource.Descriptors(listOf(WatchDescriptor("wpkh($xprv/84h/1h/0h/0/*)", false))))
        assertTrue(outcome is BootstrapResult.Failed)
        assertFalse((rpc.call("listwallets", emptyList<Any?>(), null) as List<*>).contains("evil"))
        assertFalse(File(dir, "regtest/wallets/evil").exists())
    }
}
