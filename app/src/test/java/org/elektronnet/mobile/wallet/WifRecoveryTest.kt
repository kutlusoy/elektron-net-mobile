package org.elektronnet.mobile.wallet

import org.elektronnet.mobile.node.Json
import org.elektronnet.mobile.node.RpcException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifRecoveryTest {
    private val bip39 = Bip39(loadWordlist())
    private val pass = "test-passphrase-123"

    private val mainnetUncompressed = "5HpjE2Hs7vjU4SN3YyPQCdhzCu92WoEeuE6PWNuiPyTu3ESGnzn"
    private val mainnetCompressed = "KwFfNUhSDaASSAwtG7ssQM1uVX8RgX5GHWnnLfhfiQDigjioWXHH"
    private val testnetUncompressed = "91bMom7Qi9oc2VsLBKHK5EFwrZVjfxmrFAxLb1GDjiCwpGS6u85"
    private val testnetCompressed = "cMceqPhHedrhbcR9eXgzmfWy7kRqLyAxMYwFT6ABDWsiwUp9Nsq9"

    @Test
    fun wifCleaningStripsWhitespaceAndPrefixes() {
        assertEquals(mainnetCompressed, WifKey.clean("  $mainnetCompressed  "))
        assertEquals(mainnetCompressed, WifKey.clean("p2wpkh:$mainnetCompressed"))
        assertEquals(mainnetCompressed, WifKey.clean("p2pkh:$mainnetCompressed"))
        assertEquals(mainnetCompressed, WifKey.clean("\"$mainnetCompressed\""))
        assertEquals(mainnetCompressed, WifKey.clean("'$mainnetCompressed'"))
    }

    @Test
    fun wifValidationMainnet() {
        assertTrue(WifKey.isValid(mainnetUncompressed, testnet = false))
        assertTrue(WifKey.isValid(mainnetCompressed, testnet = false))

        // Should fail on testnet
        assertFalse(WifKey.isValid(mainnetCompressed, testnet = true))
        assertFalse(WifKey.isValid(mainnetUncompressed, testnet = true))

        // Corrupted characters / checksum
        assertFalse(WifKey.isValid(mainnetCompressed.dropLast(1) + "X", testnet = false))
        assertFalse(WifKey.isValid("invalid-key", testnet = false))
        assertFalse(WifKey.isValid("", testnet = false))
    }

    @Test
    fun wifValidationTestnet() {
        assertTrue(WifKey.isValid(testnetUncompressed, testnet = true))
        assertTrue(WifKey.isValid(testnetCompressed, testnet = true))

        // Should fail on mainnet
        assertFalse(WifKey.isValid(testnetCompressed, testnet = false))
        assertFalse(WifKey.isValid(testnetUncompressed, testnet = false))
    }

    @Test
    fun recoverPrivateKeyBootstrapFlow() {
        val calls = mutableListOf<String>()
        val importedDescriptors = mutableListOf<String>()

        val rpc = WalletRpc { method, params, _ ->
            calls += method
            when (method) {
                "createwallet" -> Json.parse("""{"name":"elektron"}""")
                "walletpassphrase" -> null
                "getdescriptorinfo" -> {
                    val pMap = params as? Map<*, *>
                    val desc = (pMap?.get("descriptor") as? String) ?: ""
                    Json.parse("""{"checksum":"abcd1234","descriptor":"$desc#abcd1234"}""")
                }
                "importdescriptors" -> {
                    val pList = params as? List<*>
                    val requests = (pList?.firstOrNull() as? List<*>) ?: (params as? Map<*, *>)?.get("requests") as? List<*>
                    requests?.forEach { req ->
                        val reqMap = req as? Map<*, *>
                        importedDescriptors += reqMap?.get("desc") as? String ?: ""
                    }
                    Json.parse("""[{"success":true},{"success":true}]""")
                }
                "walletlock" -> null
                "getwalletinfo" -> Json.parse("""{"unlocked_until":0}""")
                else -> null
            }
        }

        val bootstrap = WalletBootstrap(rpc, bip39, testnet = false, removeWalletFiles = {})
        val result = bootstrap.recoverPrivateKey(mainnetCompressed, pass)

        assertTrue(result is BootstrapResult.Created)
        assertEquals(listOf("createwallet", "walletpassphrase", "getdescriptorinfo", "getdescriptorinfo", "importdescriptors", "walletlock", "getwalletinfo"), calls)

        // Check descriptors contain both wpkh and pkh with checksum
        assertTrue(importedDescriptors.any { it.startsWith("wpkh(") })
        assertTrue(importedDescriptors.any { it.startsWith("pkh(") })
    }

    @Test
    fun walletSetupWithPrivateKeyTriggersNeedsRescan() {
        val calls = mutableListOf<String>()
        val rpc = WalletRpc { method, _, _ ->
            calls += method
            when (method) {
                "createwallet" -> Json.parse("""{"name":"elektron"}""")
                "walletpassphrase" -> null
                "getdescriptorinfo" -> Json.parse("""{"checksum":"abcd1234"}""")
                "importdescriptors" -> Json.parse("""[{"success":true},{"success":true}]""")
                "walletlock" -> null
                else -> null
            }
        }

        val setup = WalletSetup { WalletBootstrap(rpc, bip39, testnet = false, removeWalletFiles = {}) }
        setup.begin(SetupRequest(phrase = emptyList(), passphrase = pass, restore = true, privateKey = mainnetCompressed))

        val status = setup.step(nodeSynced = false)
        assertEquals(SetupStatus.NeedsRescan, status)
        assertTrue(setup.hasRescanPassphrase)
        assertTrue(calls.contains("createwallet"))
    }
}
