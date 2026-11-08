package org.elektronnet.mobile.wallet

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ElectrumWalletParserTest {

    private val BIE1_DETERMINISTIC =
        "QklFMQPbAG7nwrPewWNUvxJ9c9vIP1/vXovdMaHxtBlDcLfuW0dPHpbOaFOM3cgheTQr7L6CfpdUVoUtdz4GUpeoDt+J7J0mUE/Tdll/J9EQaiGZdnnZe4csZfmGk8IdiGxdKzt6YQ7AvGdnXzwrSaI+6q56nHoO4ZRwT5x4x27F6lMKe6jnSDq5yirxkJHB1ojOxrOw9yamx2P5SYKzaKoJ8vbo9nmIr9LW7cEpWWFa+dnt/g=="

    private val BIE1_IMPORTED =
        "QklFMQNGccdN6E8tRD1iKkDqynNnRnsO0a38YdaLve5ryq6/NRdTS14qBXLSpGIqIt+WvsOhG8e3mPuDyBRaqnVdnwMkUiTKYZO70d4lojsm9vMBpnLRdP5YsJsWUVpAkhlmNze2X7KxbBrTL/n3ssDXmg+tuskHypAyMm1i/JHkBGTAzis32wFRtCiceJHw+GZ7Kx2GwThZKMbPecYJ5cDnatsIfQPLBYRYRslz3HbZgFiqwOQ79itG3RoixReC2tBTRhqq4hd/GEkWVAs4KMhfU4DKIwrN7c2DAS8JXHiCaa/tiDwS8bNro+bXEvC/LdayrEsaE1nnKjoau1jZQ7ge2Rt0P3EUWdPHEHm8O2I2wcaNwg=="

    @Test
    fun detectionWorks() {
        assertTrue(ElectrumWalletParser.isElectrumWallet(BIE1_DETERMINISTIC))
        assertTrue(ElectrumWalletParser.isElectrumEncrypted(BIE1_DETERMINISTIC))
        assertFalse(ElectrumWalletParser.isElectrumPlaintext(BIE1_DETERMINISTIC))

        val plainJson = "{\"wallet_type\": \"standard\", \"keystore\": {}}"
        assertTrue(ElectrumWalletParser.isElectrumWallet(plainJson))
        assertFalse(ElectrumWalletParser.isElectrumEncrypted(plainJson))
        assertTrue(ElectrumWalletParser.isElectrumPlaintext(plainJson))

        assertFalse(ElectrumWalletParser.isElectrumWallet("not a wallet"))
    }

    @Test
    fun decryptsAndParsesDeterministicWallet() {
        val content = ElectrumWalletParser.parse(BIE1_DETERMINISTIC, "testpassword123")
        assertTrue(content is ElectrumWalletContent.Seed)
        val seed = content as ElectrumWalletContent.Seed
        assertEquals(12, seed.words.size)
        assertEquals("abandon", seed.words[0])
        assertEquals("about", seed.words.last())
        assertEquals(ElectrumSeedType.Segwit, seed.type)
    }

    @Test
    fun decryptsAndParsesImportedWalletWithEncryptedKeys() {
        val content = ElectrumWalletParser.parse(BIE1_IMPORTED, "testpassword123")
        assertTrue(content is ElectrumWalletContent.ImportedKeys)
        val imported = content as ElectrumWalletContent.ImportedKeys
        assertEquals(1, imported.wifKeys.size)
        assertEquals("cT6mE9x7jS4gZc4h4zZ9X7qM2nK1v8Y5w3b8e7d6a5f4", imported.wifKeys[0])
    }

    @Test
    fun rejectsWrongPassword() {
        try {
            ElectrumWalletParser.parse(BIE1_DETERMINISTIC, "wrong_password")
            fail("Expected InvalidElectrumPasswordException")
        } catch (e: InvalidElectrumPasswordException) {
            // expected
        }
    }

    @Test
    fun secp256k1MultiplicationMatchesGenerator() {
        // G = 0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798
        val gBytes = byteArrayOf(
            0x02,
            0x79.toByte(), 0xbe.toByte(), 0x66, 0x7e, 0xf9.toByte(), 0xdc.toByte(), 0xbb.toByte(), 0xac.toByte(),
            0x55, 0xa0.toByte(), 0x62, 0x95.toByte(), 0xce.toByte(), 0x87.toByte(), 0x0b, 0x07,
            0x02, 0x9b.toByte(), 0xfc.toByte(), 0xdb.toByte(), 0x2d, 0xce.toByte(), 0x28, 0xd9.toByte(),
            0x59, 0xf2.toByte(), 0x81.toByte(), 0x5b, 0x16, 0xf8.toByte(), 0x17, 0x98.toByte()
        )
        val g = Secp256k1.decompress(gBytes)
        // 1 * G = G
        val oneG = Secp256k1.multiply(BigInteger.ONE, g)
        assertEquals(g.x, oneG.x)
        assertEquals(g.y, oneG.y)
        assertTrue(Secp256k1.compress(oneG).contentEquals(gBytes))

        // 2 * G: known point 02c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5
        val twoG = Secp256k1.multiply(BigInteger.valueOf(2), g)
        val twoGCompressed = Secp256k1.compress(twoG)
        assertEquals(0x02.toByte(), twoGCompressed[0])
        val expectedX = BigInteger("c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5", 16)
        assertEquals(expectedX, twoG.x)
    }
}
