package org.elektronnet.mobile.wallet

import org.elektronnet.mobile.ur.Ur
import org.elektronnet.mobile.ur.UrDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// the UR strings were made with @keystonehq/bc-ur-registry 0.6.4 from the BIP-84 test key (account 0 of the "abandon ... about" seed)
class WatchOnlyTest {
    private val xpub = "xpub6CatWdiZiodmUeTDp8LT5or8nmbKNcuyvz7WyksVFkKB4RHwCD3XyuvPEbvqAQY3rAPshWcMLoP2fMFMKHPJ4ZeZXYVUhLv1VMrjPC7PW6V"
    private val zpub = "zpub6rFR7y4Q2AijBEqTUquhVz398htDFrtymD9xYYfG1m4wAcvPhXNfE3EfH1r1ADqtfSdVCToUG868RvUUkgDKf31mGDtKsAYz2oz2AGutZYs"
    private val tpub = "tpubDCxX2sYFS5bDkSe5GKKYHjBW7tgyN1R3UchpLJvdbf54ohxeGRtd8MbDUe1cguVHe4vnK68DsuD5MXjxi9EXx16rb9EnNsaF5KT99CinaJz"
    private val origin = "[73c5da0a/84h/0h/0h]"
    private val main = WatchOnlyInput(testnet = false)
    private val test = WatchOnlyInput(testnet = true)
    private val address = Bech32.encode("be", 0, ByteArray(20) { (it * 7 + 1).toByte() })

    private fun ur(text: String): Ur {
        val decoder = UrDecoder()
        assertTrue(decoder.receive(text))
        return decoder.result!!
    }

    private fun items(parse: WatchParse) = ((parse as WatchParse.Ok).source as WatchSource.Descriptors).items

    @Test
    fun anAddressBecomesAnAddressWallet() {
        val parse = main.parse("  " + address.uppercase() + "\n") as WatchParse.Ok
        assertEquals(WatchSource.Address(address), parse.source)
        assertEquals(WatchParse.Invalid(WatchProblem.WrongNetwork), test.parse(address))
    }

    @Test
    fun aBareKeyGetsReceiveAndChangeDescriptors() {
        val list = items(main.parse(xpub))
        assertEquals(listOf("wpkh($xpub/0/*)", "wpkh($xpub/1/*)"), list.map { it.descriptor })
        assertEquals(listOf(false, true), list.map { it.internal })
    }

    @Test
    fun aSlip132KeyIsConvertedAndKeepsItsScriptType() {
        assertEquals("wpkh($xpub/0/*)", items(main.parse(zpub)).first().descriptor)
        assertEquals(WatchParse.Invalid(WatchProblem.WrongNetwork), test.parse(zpub))
        assertEquals(WatchParse.Invalid(WatchProblem.WrongNetwork), main.parse(tpub))
        assertEquals("wpkh($tpub/0/*)", items(test.parse(tpub)).first().descriptor)
    }

    @Test
    fun theOriginDecidesTheScriptType() {
        assertEquals("wpkh($origin$xpub/0/*)", items(main.parse("[73C5DA0A/84'/0'/0']$xpub")).first().descriptor)
        assertEquals("sh(wpkh([73c5da0a/49h/0h/0h]$xpub/0/*))", items(main.parse("[73c5da0a/49h/0h/0h]$xpub")).first().descriptor)
        assertEquals("pkh([73c5da0a/44h/0h/0h]$xpub/0/*)", items(main.parse("[73c5da0a/44h/0h/0h]$xpub")).first().descriptor)
        assertEquals(WatchParse.Invalid(WatchProblem.Unsupported), main.parse("[73c5da0a/48h/0h/0h/2h]$xpub"))
    }

    @Test
    fun aGivenChainIsKept() {
        val list = items(main.parse("$origin$xpub/0/*"))
        assertEquals(listOf("wpkh($origin$xpub/0/*)", "wpkh($origin$xpub/1/*)"), list.map { it.descriptor })
        assertEquals(listOf("wpkh($origin$xpub/1/*)"), items(main.parse("$origin$xpub/1/*")).map { it.descriptor })
    }

    @Test
    fun aDescriptorIsTakenAsItIsWithoutItsChecksum() {
        val d = "wpkh($origin$xpub/0/*)"
        assertEquals(listOf(d), items(main.parse("$d#abcd1234")).map { it.descriptor })
        val both = items(main.parse("wpkh($origin$xpub/<0;1>/*)"))
        assertEquals(listOf("wpkh($origin$xpub/0/*)", "wpkh($origin$xpub/1/*)"), both.map { it.descriptor })
        assertEquals(listOf(false, true), both.map { it.internal })
        val json = items(main.parse("{\"label\":\"x\",\"descriptor\":\"$d#abcd1234\"}"))
        assertEquals(listOf(d), json.map { it.descriptor })
    }

    @Test
    fun privateKeysAreRefused() {
        val xprv = "xprv9s21ZrQH143K3QTDL4LXw2F7HEK3wJUD2nW2nRk4stbPy6cq3jPPqjiChkVvvNKmPGJxWUtg6LnF5kejMRNNU3TGtRBeJgk33yuGBxrMPHi"
        assertEquals(WatchParse.Invalid(WatchProblem.PrivateKey), main.parse(xprv))
        assertEquals(WatchParse.Invalid(WatchProblem.PrivateKey), main.parse("wpkh($xprv/0/*)"))
        assertEquals(WatchParse.Invalid(WatchProblem.PrivateKey), main.parse("KwDiBf89QgGbjEhKnhXJuH7LrciVrZi3qYjgd9M7rFU73sVHnoWn"))
    }

    @Test
    fun nonsenseIsRefused() {
        listOf("", "   ", "hello", "xpub123", "wpkh(", "ur:crypto-psbt/abc").forEach {
            assertTrue(main.parse(it) is WatchParse.Invalid)
        }
    }

    @Test
    fun urKeysMatchTheReference() {
        val expectedAccount = listOf("wpkh($origin$xpub/0/*)", "sh(wpkh($origin$xpub/0/*))")
        assertEquals(expectedAccount, UrKeys.descriptors(ur("ur:crypto-account/oeadcyjksktnbkaolftaadmwtaaddlosaowkaxhdclaojoknidzcpssajtptrpfrcecfkkamykjtvtcsbtbdtkcfiyvyoetneeykwfnbnyndaahdcxgegunbpyclrhuomdlnnsglmooyhscfglaxrtwsfhykadgeswmowkeosskoghmhztahtaadehnbamtaaddyotadlncsghykaeykaeykaocyjksktnbkaxaxattaaddyoyadlraewklawkaycykbwfdnuytaadmhtaadmwtaaddlosaowkaxhdclaojoknidzcpssajtptrpfrcecfkkamykjtvtcsbtbdtkcfiyvyoetneeykwfnbnyndaahdcxgegunbpyclrhuomdlnnsglmooyhscfglaxrtwsfhykadgeswmowkeosskoghmhztahtaadehnbamtaaddyotadlncsghykaeykaeykaocyjksktnbkaxaxattaaddyoyadlraewklawkaycykbwfdnuycsehktdy"), testnet = false))
        assertEquals(listOf("wpkh($origin$xpub/0/*)"), UrKeys.descriptors(ur("ur:crypto-output/taadmwtaaddlosaowkaxhdclaojoknidzcpssajtptrpfrcecfkkamykjtvtcsbtbdtkcfiyvyoetneeykwfnbnyndaahdcxgegunbpyclrhuomdlnnsglmooyhscfglaxrtwsfhykadgeswmowkeosskoghmhztahtaadehnbamtaaddyotadlncsghykaeykaeykaocyjksktnbkaxaxattaaddyoyadlraewklawkaycykbwfdnuybekifpbb"), testnet = false))
        assertEquals(listOf("$origin$xpub"), UrKeys.descriptors(ur("ur:crypto-hdkey/olaowkaxhdclaojoknidzcpssajtptrpfrcecfkkamykjtvtcsbtbdtkcfiyvyoetneeykwfnbnyndaahdcxgegunbpyclrhuomdlnnsglmooyhscfglaxrtwsfhykadgeswmowkeosskoghmhztahtaadehnbamtaaddyotadlncsghykaeykaeykaocyjksktnbkaxaxaycykbwfdnuysnlnfdst"), testnet = false))
    }

    @Test
    fun urKeysOfTheOtherNetworkOrTypeAreRefused() {
        assertNull(UrKeys.descriptors(ur("ur:crypto-account/oeadcyjksktnbkaolftaadmwtaaddlosaowkaxhdclaojoknidzcpssajtptrpfrcecfkkamykjtvtcsbtbdtkcfiyvyoetneeykwfnbnyndaahdcxgegunbpyclrhuomdlnnsglmooyhscfglaxrtwsfhykadgeswmowkeosskoghmhztahtaadehnbamtaaddyotadlncsghykaeykaeykaocyjksktnbkaxaxattaaddyoyadlraewklawkaycykbwfdnuytaadmhtaadmwtaaddlosaowkaxhdclaojoknidzcpssajtptrpfrcecfkkamykjtvtcsbtbdtkcfiyvyoetneeykwfnbnyndaahdcxgegunbpyclrhuomdlnnsglmooyhscfglaxrtwsfhykadgeswmowkeosskoghmhztahtaadehnbamtaaddyotadlncsghykaeykaeykaocyjksktnbkaxaxattaaddyoyadlraewklawkaycykbwfdnuycsehktdy"), testnet = true))
        assertNull(UrKeys.descriptors(Ur.ofBytes("crypto-psbt", ByteArray(5)), testnet = false))
    }

    @Test
    fun theKeysFromAUrGoThroughTheParser() {
        val descriptors = UrKeys.descriptors(ur("ur:crypto-account/oeadcyjksktnbkaolftaadmwtaaddlosaowkaxhdclaojoknidzcpssajtptrpfrcecfkkamykjtvtcsbtbdtkcfiyvyoetneeykwfnbnyndaahdcxgegunbpyclrhuomdlnnsglmooyhscfglaxrtwsfhykadgeswmowkeosskoghmhztahtaadehnbamtaaddyotadlncsghykaeykaeykaocyjksktnbkaxaxattaaddyoyadlraewklawkaycykbwfdnuytaadmhtaadmwtaaddlosaowkaxhdclaojoknidzcpssajtptrpfrcecfkkamykjtvtcsbtbdtkcfiyvyoetneeykwfnbnyndaahdcxgegunbpyclrhuomdlnnsglmooyhscfglaxrtwsfhykadgeswmowkeosskoghmhztahtaadehnbamtaaddyotadlncsghykaeykaeykaocyjksktnbkaxaxattaaddyoyadlraewklawkaycykbwfdnuycsehktdy"), testnet = false)!!
        val list = items(main.parse(descriptors.joinToString("\n")))
        assertEquals(2, list.size)
        assertFalse(list.none { it.descriptor.startsWith("sh(wpkh(") })
    }
}
