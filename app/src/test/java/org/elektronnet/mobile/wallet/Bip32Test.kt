package org.elektronnet.mobile.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Bip32Test {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    @Test
    fun masterKeyMatchesBip32Vector1() {
        assertEquals(
            "xprv9s21ZrQH143K3QTDL4LXw2F7HEK3wJUD2nW2nRk4stbPy6cq3jPPqjiChkVvvNKmPGJxWUtg6LnF5kejMRNNU3TGtRBeJgk33yuGBxrMPHi",
            Bip32.masterExtendedKey(hex("000102030405060708090a0b0c0d0e0f"), testnet = false),
        )
    }

    @Test
    fun testnetUsesTprvPrefix() {
        assertTrue(Bip32.masterExtendedKey(hex("000102030405060708090a0b0c0d0e0f"), testnet = true).startsWith("tprv"))
    }

    @Test
    fun descriptorSets() {
        val mainnet = Descriptors.forWallet("KEY", testnet = false)
        assertEquals(
            listOf(
                "wpkh(KEY/84h/1370h/0h/0/*)", "wpkh(KEY/84h/1370h/0h/1/*)",
                "wpkh(KEY/84h/0h/0h/0/*)", "wpkh(KEY/84h/0h/0h/1/*)",
            ),
            mainnet.map { it.descriptor },
        )
        assertEquals(listOf(true, true, false, false), mainnet.map { it.active })
        assertEquals(listOf(false, true), mainnet.take(2).map { it.internal })
        val testnet = Descriptors.forWallet("KEY", testnet = true)
        assertEquals(listOf("wpkh(KEY/84h/1h/0h/0/*)", "wpkh(KEY/84h/1h/0h/1/*)"), testnet.map { it.descriptor })
    }
}
