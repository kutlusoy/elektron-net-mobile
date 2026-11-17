package org.elektronnet.mobile.wallet

import java.io.File
import org.elektronnet.mobile.node.Json
import org.elektronnet.mobile.node.RpcException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletRestoreTest {

    private val BIE1_VECTOR =
        "QklFMQPbAG7nwrPewWNUvxJ9c9vIP1/vXovdMaHxtBlDcLfuW0dPHpbOaFOM3cgheTQr7L6CfpdUVoUtdz4GUpeoDt+J7J0mUE/Tdll/J9EQaiGZdnnZe4csZfmGk8IdiGxdKzt6YQ7AvGdnXzwrSaI+6q56nHoO4ZRwT5x4x27F6lMKe6jnSDq5yirxkJHB1ojOxrOw9yamx2P5SYKzaKoJ8vbo9nmIr9LW7cEpWWFa+dnt/g=="

    private class FakeRpc(val existingWallets: List<String> = emptyList()) {
        val calls = mutableListOf<String>()
        val createParams = mutableListOf<Map<*, *>>()
        val importParams = mutableListOf<Any?>()

        val rpc = WalletRpc { method, params, _ ->
            calls += method
            when (method) {
                "listwalletdir" -> Json.parse("""{"wallets": [${existingWallets.joinToString(",") { """{"name":"$it"}""" }}]}""")
                "restorewallet" -> null
                "createwallet" -> {
                    (params as? Map<*, *>)?.let { createParams += it }
                    null
                }
                "walletpassphrase" -> null
                "getdescriptorinfo" -> {
                    val desc = (params as? List<*>)?.get(0).toString()
                    Json.parse("""{"descriptor":"$desc#chk1234","isrange":true}""")
                }
                "importdescriptors" -> {
                    importParams += params
                    Json.parse("""[{"success":true},{"success":true},{"success":true},{"success":true}]""")
                }
                "getwalletinfo" -> Json.parse("""{"unlocked_until":0}""")
                "getblockchaininfo" -> Json.parse("""{"pruneheight":0}""")
                "rescanblockchain" -> null
                "rescanutxoset" -> null
                else -> throw RpcException(-1, "unknown $method")
            }
        }
    }

    @Test
    fun restoresCoreFileDirectly() {
        val temp = File.createTempFile("core_test", ".dat").apply {
            writeBytes(byteArrayOf(0x00, 0x05, 0x31, 0x02)) // SQLite or binary junk
            deleteOnExit()
        }
        val fake = FakeRpc()
        val restore = WalletRestore(fake.rpc) {}
        val result = restore.restore(temp, "core_wallet")
        assertTrue(result is RestoreResult.Restored)
        assertTrue(fake.calls.contains("restorewallet"))
        temp.delete()
    }

    @Test
    fun detectsElectrumAndRequestsPassphrase() {
        val temp = File.createTempFile("electrum_test", ".dat").apply {
            writeText(BIE1_VECTOR)
            deleteOnExit()
        }
        val fake = FakeRpc()
        val restore = WalletRestore(fake.rpc) {}
        val result = restore.restore(temp, "electrum_wallet", null)
        assertTrue(result is RestoreResult.NeedsPassphrase)
        assertEquals("electrum_wallet", (result as RestoreResult.NeedsPassphrase).walletName)
        temp.delete()
    }

    @Test
    fun rejectsIncorrectPassphrase() {
        val temp = File.createTempFile("electrum_test", ".dat").apply {
            writeText(BIE1_VECTOR)
            deleteOnExit()
        }
        val fake = FakeRpc()
        val restore = WalletRestore(fake.rpc) {}
        val result = restore.restore(temp, "electrum_wallet", "wrong_pass")
        assertTrue(result is RestoreResult.Failed)
        assertEquals(RestoreFailure.IncorrectPassphrase, (result as RestoreResult.Failed).reason)
        temp.delete()
    }

    @Test
    fun restoresElectrumWalletWithCorrectPassphrase() {
        val temp = File.createTempFile("electrum_test", ".dat").apply {
            writeText(BIE1_VECTOR)
            deleteOnExit()
        }
        val fake = FakeRpc()
        val restore = WalletRestore(fake.rpc, testnet = false) {}
        val result = restore.restore(temp, "elektron_restored", "testpassword123")
        assertTrue(result is RestoreResult.Restored)
        val restored = result as RestoreResult.Restored
        assertEquals("elektron_restored", restored.walletName)
        assertTrue(restored.encrypted)
        assertTrue(fake.calls.contains("createwallet"))
        assertTrue(fake.calls.contains("walletpassphrase"))
        assertTrue(fake.calls.contains("getdescriptorinfo"))
        assertTrue(fake.calls.contains("importdescriptors"))
        temp.delete()
    }

    @Test
    fun failsIfWalletNameTaken() {
        val temp = File.createTempFile("temp", ".dat").apply {
            writeText("dummy")
            deleteOnExit()
        }
        val fake = FakeRpc(existingWallets = listOf("existing_wallet"))
        val restore = WalletRestore(fake.rpc) {}
        val result = restore.restore(temp, "existing_wallet")
        assertTrue(result is RestoreResult.Failed)
        assertEquals(RestoreFailure.NameTaken, (result as RestoreResult.Failed).reason)
        temp.delete()
    }
}
