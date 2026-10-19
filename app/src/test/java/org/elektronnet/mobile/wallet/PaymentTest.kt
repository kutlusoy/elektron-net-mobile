package org.elektronnet.mobile.wallet

import org.elektronnet.mobile.ui.model.ReceiveUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

class PaymentTest {
    private val main = AddressCheck(testnet = false)
    private val test = AddressCheck(testnet = true)
    private val uris = PaymentUri("elek", main)
    private val program = hex("751e76e8199196d454941c45d1b3a323f1433bd6")
    private val address = Bech32.encode("be", 0, program)

    @Test
    fun bech32MatchesBip173() {
        assertEquals("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4", Bech32.encode("bc", 0, program))
        assertEquals("bc1p0xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vqzk5jj0", Bech32.encode("bc", 1, hex("79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798")))
        assertEquals(program.toList(), Bech32.decode("BC1QW508D6QEJXTDG4Y5R3ZARVARY0C5XW7KV8F3T4")!!.program.toList())
        assertNull(Bech32.decode("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t5"))
        assertNull(Bech32.decode("Bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"))
        assertNull(Bech32.decode("bc1p0xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vqh2y7hd"))
        assertNull(Bech32.decode("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4x"))
    }

    @Test
    fun addressClassification() {
        assertEquals(AddressKind.Native, main.classify(address))
        assertEquals(AddressKind.Native, main.classify(address.uppercase()))
        assertEquals(AddressKind.ForeignNetwork("bc"), main.classify("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"))
        assertEquals(AddressKind.LegacyBase58, main.classify("1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2"))
        assertEquals(AddressKind.LegacyBase58, main.classify("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy"))
        assertEquals(AddressKind.Invalid, main.classify("1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN3"))
        assertEquals(AddressKind.Invalid, main.classify(""))
        assertEquals(AddressKind.Invalid, main.classify("hello"))
        assertEquals(AddressKind.Native, test.classify(Bech32.encode("tb", 0, program)))
        assertEquals(AddressKind.ForeignNetwork("be"), test.classify(address))
        assertEquals(AddressKind.ForeignNetwork("tb"), main.classify(Bech32.encode("tb", 0, program)))
        assertEquals(AddressKind.ForeignNetwork("bcrt"), test.classify(Bech32.encode("bcrt", 0, program)))
        assertEquals(AddressKind.Invalid, test.classify("1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2"))
    }

    @Test
    fun amounts() {
        assertEquals(150_000_000L, Amount.parse("1.5"))
        assertEquals(1L, Amount.parse("0.00000001"))
        assertEquals(50_000_000L, Amount.parse(".5"))
        assertEquals(250_000_000L, Amount.parse("2,5", allowComma = true))
        assertNull(Amount.parse("2,5"))
        for (bad in listOf("", "0", "0.0", "-1", "+1", "1e3", "0.123456789", "1.", "abc", "1.5.5", "9999999999999")) assertTrue(bad, Amount.parse(bad) == null)
        assertEquals("1.5", Amount.format(150_000_000L))
        assertEquals("1", Amount.format(100_000_000L))
        assertEquals("0.00000001", Amount.format(1L))
        assertEquals("12.3456789", Amount.format(1_234_567_890L))
    }

    @Test
    fun buildAndParseRoundTrip() {
        val request = PaymentRequest(address, 123_456_789L, "Shop & Co", "Order #5 ü")
        val text = uris.build(request)
        assertEquals("elek:$address?amount=1.23456789&label=Shop%20%26%20Co&message=Order%20%235%20%C3%BC", text)
        val parsed = uris.parse(text) as UriParse.Ok
        assertEquals(request, parsed.request)
        assertEquals(AddressKind.Native, parsed.kind)
        assertEquals("elek:$address", uris.build(PaymentRequest(address)))
    }

    @Test
    fun qrTextIsAlwaysLowercase() {
        assertEquals("elek:$address?amount=2", uris.qrText(PaymentRequest(address, 200_000_000L)))
        assertEquals("elek:" + address.lowercase(), uris.build(PaymentRequest(address.uppercase())))
        assertEquals(uris.build(PaymentRequest(address, null, null, "Thanks")), uris.qrText(PaymentRequest(address, null, null, "Thanks")))
        val parsed = uris.parse(uris.qrText(PaymentRequest(address, 200_000_000L))) as UriParse.Ok
        assertEquals(address, parsed.request.address)
        assertEquals(200_000_000L, parsed.request.amountLep)
    }

    @Test
    fun parserRejectsBadInput() {
        fun problem(text: String) = (uris.parse(text) as UriParse.Invalid).reason
        assertEquals(UriProblem.Scheme, problem("bitcoin:$address"))
        assertEquals(UriProblem.Scheme, problem(address))
        assertEquals(UriProblem.Address, problem("elek:"))
        assertEquals(UriProblem.Address, problem("elek:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"))
        assertEquals(UriProblem.Amount, problem("elek:$address?amount=1e3"))
        assertEquals(UriProblem.Amount, problem("elek:$address?amount=-1"))
        assertEquals(UriProblem.Amount, problem("elek:$address?amount=0.123456789"))
        assertEquals(UriProblem.Duplicate, problem("elek:$address?amount=1&amount=2"))
        assertEquals(UriProblem.RequiredParameter, problem("elek:$address?req-x=1"))
        assertEquals(UriProblem.Encoding, problem("elek:$address?label=%ZZ"))
        assertEquals(UriProblem.Encoding, problem("elek:$address?label=%C3"))
    }

    @Test
    fun parserCleansUntrustedText() {
        val ok = uris.parse("ELEK:$address?lightning=lnbc1&unknown=1&label=a%E2%80%AEb%00c&message=" + "x".repeat(400)) as UriParse.Ok
        assertEquals("abc", ok.request.label)
        assertEquals(256, ok.request.message!!.length)
        assertNull(ok.request.amountLep)
        val legacy = uris.parse("elek:1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2") as UriParse.Ok
        assertEquals(AddressKind.LegacyBase58, legacy.kind)
    }

    @Test
    fun fuzzNeverThrows() {
        val random = java.util.Random(11)
        val alphabet = "elek:?&=%0123456789abcdefABCDEF.-_ \u202e\n"
        repeat(20000) {
            val text = (if (it % 3 == 0) "elek:$address?" else "") + (1..random.nextInt(60)).joinToString("") { alphabet[random.nextInt(alphabet.length)].toString() }
            uris.parse(text)
        }
    }

    @Test
    fun addressRuns() {
        val runs = AddressFormat.runs(address)
        assertEquals(AddressFormat.grouped(address), runs.joinToString("") { it.text })
        assertTrue(runs.first().emphasized)
        assertTrue(runs.last().emphasized)
        assertEquals(AddressFormat.EMPHASIS * 2, runs.filter { it.emphasized }.sumOf { r -> r.text.count { it != ' ' } })
        assertFalse(runs.filter { it.emphasized }.any { it.text.startsWith(" ") && it.text.length == 1 && false })
    }

    @Test
    fun receiveModel() {
        val model = ReceiveUiModel.build(address, uris, "1,5", " Lunch ", synced = false)
        assertEquals("elek:$address?amount=1.5&message=Lunch", model.uri)
        assertFalse(model.amountInvalid)
        assertTrue(model.showSyncNote)
        val bad = ReceiveUiModel.build(address, uris, "abc", "", synced = true)
        assertTrue(bad.amountInvalid)
        assertEquals("elek:$address", bad.uri)
        assertEquals("elek:$address", bad.qrText)
        assertEquals(120, ReceiveUiModel.build(address, uris, "", "n".repeat(500), true).uri.substringAfter("message=").length)
        assertEquals(address, ReceiveUiModel.build(address, uris, "", "", true).copyText)
        assertEquals("elek:$address?amount=0.137", ReceiveUiModel.build(address, uris, "0.137", "", true).copyText)
        assertEquals("elek:$address?message=Hi", ReceiveUiModel.build(address, uris, "", "Hi", true).copyText)
    }

    @Test
    fun parsesTypicalFaucetAndShareLinks() {
        val faucet = uris.parse("elek:$address?amount=0.13700000&label=Faucet%20Donation") as UriParse.Ok
        assertEquals(13_700_000L, faucet.request.amountLep)
        assertEquals("Faucet Donation", faucet.request.label)
        val shared = uris.parse("elek:$address?amount=0.137&message=Meine%20Nachricht") as UriParse.Ok
        assertEquals("Meine Nachricht", shared.request.message)
        assertEquals("elek:$address?amount=0.137&message=Meine%20Nachricht", uris.build(shared.request))
        val upper = uris.parse("ELEK:" + address.uppercase() + "?AMOUNT=1") as UriParse.Ok
        assertEquals(address, upper.request.address)
        assertEquals(100_000_000L, upper.request.amountLep)
    }
}
