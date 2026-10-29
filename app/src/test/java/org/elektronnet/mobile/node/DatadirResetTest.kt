package org.elektronnet.mobile.node

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatadirResetTest {
    @Test
    fun removesChainDataAndKeepsWallets() {
        val dir = Files.createTempDirectory("reset").toFile()
        File(dir, "blocks/index").mkdirs()
        File(dir, "blocks/index/000001.ldb").writeText("x")
        File(dir, "chainstate").mkdirs()
        File(dir, "peers.dat").writeText("p")
        File(dir, "wallets/elektron").mkdirs()
        File(dir, "wallets/elektron/wallet.dat").writeText("secret")
        File(dir, "datadir.version").writeText("1\n")
        val removed = DatadirReset.perform(dir)
        assertEquals(setOf("blocks", "chainstate", "peers.dat"), removed.toSet())
        assertFalse(File(dir, "blocks").exists())
        assertTrue(File(dir, "wallets/elektron/wallet.dat").isFile)
        assertEquals(listOf("datadir.version", "wallets"), DatadirReset.keeps(dir))
        assertTrue(DatadirReset.perform(dir).isEmpty())
    }

    @Test
    fun removesTheSnapshotChainstateAndTheRestartMarker() {
        val dir = Files.createTempDirectory("reset-snapshot").toFile()
        File(dir, "blocks").mkdirs()
        File(dir, "chainstate").mkdirs()
        File(dir, "chainstate_snapshot").mkdirs()
        File(dir, "chainstate_snapshot/base_blockhash").writeText("x")
        File(dir, "snapshots").mkdirs()
        File(dir, "snapshots/utxo-1000.dat").writeText("x")
        File(dir, ".elektron_auto_reindex_attempted").writeText("2026-10-27T00:00:00Z")
        File(dir, "wallets/elektron").mkdirs()
        File(dir, "wallets/elektron/wallet.dat").writeText("secret")
        val removed = DatadirReset.perform(dir)
        assertEquals(setOf("blocks", "chainstate", "chainstate_snapshot", "snapshots", ".elektron_auto_reindex_attempted"), removed.toSet())
        assertEquals(listOf("wallets"), DatadirReset.keeps(dir))
        assertTrue(File(dir, "wallets/elektron/wallet.dat").isFile)
    }

    @Test
    fun removesTheChainDataOfASubdirectoryChain() {
        val dir = Files.createTempDirectory("reset-subdir").toFile()
        File(dir, "testnet3/blocks").mkdirs()
        File(dir, "testnet3/chainstate_snapshot").mkdirs()
        File(dir, "testnet3/.elektron_auto_reindex_attempted").writeText("x")
        File(dir, "testnet3/wallets/elektron").mkdirs()
        File(dir, "testnet3/wallets/elektron/wallet.dat").writeText("secret")
        File(dir, "peers.dat").writeText("p")
        val removed = DatadirReset.perform(dir, "testnet3")
        assertEquals(setOf("peers.dat", "testnet3/blocks", "testnet3/chainstate_snapshot", "testnet3/.elektron_auto_reindex_attempted"), removed.toSet())
        assertFalse(File(dir, "testnet3/blocks").exists())
        assertTrue(File(dir, "testnet3/wallets/elektron/wallet.dat").isFile)
        assertTrue(DatadirReset.perform(dir, "testnet3").isEmpty())
    }
}
