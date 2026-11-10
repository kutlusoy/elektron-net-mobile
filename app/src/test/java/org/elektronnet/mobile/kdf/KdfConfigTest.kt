package org.elektronnet.mobile.kdf

import java.io.File
import org.elektronnet.mobile.node.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KdfConfigTest {

    @Test
    fun testDeriveKdfPassphrase() {
        assertEquals("", KdfConfig.deriveKdfPassphrase(null))
        assertEquals("", KdfConfig.deriveKdfPassphrase("   "))

        val seed = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val derived = KdfConfig.deriveKdfPassphrase(seed)
        assertTrue(derived.startsWith("elkkdf1"))
        assertEquals(7 + 64, derived.length) // "elkkdf1" (7) + 64 hex chars of sha256

        // Deterministic check
        assertEquals(derived, KdfConfig.deriveKdfPassphrase("  $seed  "))
    }

    @Test
    fun testPasswordPolicy() {
        assertFalse(KdfConfig.isPasswordPolicyOk("short1!")) // < 8
        assertFalse(KdfConfig.isPasswordPolicyOk("Password123!")) // contains password
        assertFalse(KdfConfig.isPasswordPolicyOk("ALLUPPERCASE1!")) // no lowercase
        assertFalse(KdfConfig.isPasswordPolicyOk("alllowercase1!")) // no uppercase
        assertFalse(KdfConfig.isPasswordPolicyOk("NoSpecial12345")) // all alphanumeric
        assertFalse(KdfConfig.isPasswordPolicyOk("K_aaa12345678")) // 3 identical characters

        assertTrue(KdfConfig.isPasswordPolicyOk("K_0123456789abcdef"))
        assertTrue(KdfConfig.isPasswordPolicyOk("K_a1b2c3d4e5f6g7h8"))
    }

    @Test
    fun testGenerateRpcPassword() {
        for (i in 0 until 50) {
            val pw = KdfConfig.generateRpcPassword()
            assertTrue("Generated password $pw should pass policy", KdfConfig.isPasswordPolicyOk(pw))
            assertTrue(pw.startsWith("K_"))
        }
    }

    @Test
    fun testBuildConfigTestnet() {
        val tempDir = File.createTempFile("kdf-test", "").apply { delete(); mkdirs() }
        try {
            val conf = KdfConfig.buildConfig(
                kdfDir = tempDir,
                isTestnet = true,
                passphrase = "test-passphrase",
                rpcPassword = "K_a1b2c3d4e5f6g7h8",
                rpcPort = 7821,
            )
            assertEquals("elektron-net", conf["gui"])
            assertEquals(8888, conf["netid"])
            assertEquals("127.0.0.1", conf["rpcip"])
            assertEquals(7821, conf["rpcport"])
            assertEquals("test-passphrase", conf["passphrase"])
            assertEquals("K_a1b2c3d4e5f6g7h8", conf["rpc_password"])

            val coins = conf["coins"] as List<*>
            assertEquals(3, coins.size)
            val tickers = coins.map { (it as Map<*, *>)["coin"] }
            assertEquals(listOf("tELEK", "tBTC", "SEPOLIAETH"), tickers)

            // Test JSON serialization
            val jsonStr = Json.stringify(conf)
            assertNotNull(jsonStr)
            val reparsed = Json.parse(jsonStr) as Map<*, *>
            assertEquals(8888L, reparsed["netid"])
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testBuildConfigMainnet() {
        val tempDir = File.createTempFile("kdf-test-main", "").apply { delete(); mkdirs() }
        try {
            val conf = KdfConfig.buildConfig(
                kdfDir = tempDir,
                isTestnet = false,
                passphrase = "main-passphrase",
                rpcPassword = "K_a1b2c3d4e5f6g7h8",
            )
            assertEquals(2, conf["netid"])
            val coins = conf["coins"] as List<*>
            val tickers = coins.map { (it as Map<*, *>)["coin"] }
            assertEquals(listOf("ELEK", "BTC", "ETH"), tickers)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
