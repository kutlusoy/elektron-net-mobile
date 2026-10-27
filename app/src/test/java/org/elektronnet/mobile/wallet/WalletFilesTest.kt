package org.elektronnet.mobile.wallet

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletFilesTest {
    @Test
    fun removesTheWholeWalletFolder() {
        val dir = File.createTempFile("wallet", "").also { it.delete(); File(it, "sub").mkdirs(); File(it, "sub/wallet.dat").writeText("x") }
        deleteWalletDir(dir)
        assertFalse(dir.exists())
    }

    private fun datadir() = File.createTempFile("datadir", "").also { it.delete(); it.mkdirs() }

    @Test
    fun findsTheWalletUnderTheExpectedNetworkFolder() {
        val root = datadir()
        File(root, "regtest/wallets/elektron_2").mkdirs().also { File(root, "regtest/wallets/elektron_2/wallet.dat").writeText("x") }
        File(root, "regtest/wallets/elektron").mkdirs()
        deleteWalletEverywhere(root, "regtest", "elektron_2")
        assertFalse(File(root, "regtest/wallets/elektron_2").exists())
        assertTrue(File(root, "regtest/wallets/elektron").exists())
    }

    @Test
    fun findsTheWalletWhenTheNetworkFolderWasGuessedWrong() {
        val root = datadir()
        File(root, "testnet4/wallets/elektron_2").mkdirs().also { File(root, "testnet4/wallets/elektron_2/wallet.dat").writeText("x") }
        deleteWalletEverywhere(root, "testnet3", "elektron_2")
        assertFalse(File(root, "testnet4/wallets/elektron_2").exists())
        val flat = datadir()
        File(flat, "wallets/elektron_3").mkdirs()
        deleteWalletEverywhere(flat, "", "elektron_3")
        assertFalse(File(flat, "wallets/elektron_3").exists())
    }

    @Test
    fun refusesNamesThatCouldLeaveTheWalletFolder() {
        val root = datadir()
        File(root, "evil").mkdirs()
        listOf("../evil", "a/b", "", "..", "x y").forEach { name ->
            val failed = try { deleteWalletEverywhere(root, "", name); false } catch (e: java.io.IOException) { true }
            assertTrue(name, failed)
        }
        assertTrue(File(root, "evil").exists())
    }
}
