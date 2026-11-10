package org.elektronnet.mobile.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

class ElectrumSeedTest {

    @Test
    fun segwitSeedRecognition() {
        val words = Bip39.parse("unlock bitter quit enjoy pool enrich color awful taxi swarm general buffalo")
        assertEquals(ElectrumSeedType.Segwit, ElectrumSeed.seedType(words))
        assertTrue(ElectrumSeed.validate(words))

        val seedHex = ElectrumSeed.toSeed(words, "").toHex()
        assertEquals(
            "7afd86ae8f2985a0de95a9793d9e87ead3fbf08e0806585362aafe86ba0d147aceb226fd1a093bfe3bb667fcaf13423020e40f05ac1de5e03e3a4598d07a9834",
            seedHex,
        )

        val passHex = ElectrumSeed.toSeed(words, "my_password").toHex()
        assertEquals(
            "5a09118d7b0f56cc9727184a24e1f7d37f53a5ec303126b858e806df35c0cb742be10e8c254a82d0820bbd761a4b2d436b512c82c9e0740b193ace6b0bb69cdc",
            passHex,
        )
    }

    @Test
    fun standardSeedRecognition() {
        val words = Bip39.parse("clinic vital idle earth spatial history rice wood attend bulk boat baby")
        assertEquals(ElectrumSeedType.Standard, ElectrumSeed.seedType(words))
        assertTrue(ElectrumSeed.validate(words))

        val seedHex = ElectrumSeed.toSeed(words, "").toHex()
        assertEquals(
            "39097697d56eeb280d3de0c2db427427925a4325a1c4139572c639696475aeb35e9b3a1fe5467536a49389b5aa2472fea49774fec09b2098dc8a0ca2ff7dd6a7",
            seedHex,
        )
    }

    @Test
    fun invalidSeed() {
        val words = Bip39.parse("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about")
        // This is a BIP-39 seed, but NOT a valid Electrum seed
        assertNull(ElectrumSeed.seedType(words))
        assertFalse(ElectrumSeed.validate(words))
    }

    @Test
    fun descriptorGeneration() {
        val dummyMaster = "xprv9s21ZrQH143K47mo4GhKpDR3Ny5BeKFfSNPtBPp8TKkt7kju4DpQk8dRcLxamP44GpLATrYS64u7gQAhtzkdTE1WBj9nQefoH7tcDB4wN4t"
        val swSpecs = Descriptors.forElectrumSeed(dummyMaster, ElectrumSeedType.Segwit)
        assertEquals(3, swSpecs.size)
        assertEquals("wpkh($dummyMaster/0h/0/*)", swSpecs[0].descriptor)
        assertTrue(swSpecs[0].active)
        assertFalse(swSpecs[0].internal)
        assertEquals("wpkh($dummyMaster/0h/1/*)", swSpecs[1].descriptor)
        assertTrue(swSpecs[1].active)
        assertTrue(swSpecs[1].internal)

        val stdSpecs = Descriptors.forElectrumSeed(dummyMaster, ElectrumSeedType.Standard)
        assertEquals(3, stdSpecs.size)
        assertEquals("pkh($dummyMaster/0/*)", stdSpecs[0].descriptor)
        assertTrue(stdSpecs[0].active)
        assertFalse(stdSpecs[0].internal)
        assertEquals("pkh($dummyMaster/1/*)", stdSpecs[1].descriptor)
        assertTrue(stdSpecs[1].active)
        assertTrue(stdSpecs[1].internal)
    }
}
