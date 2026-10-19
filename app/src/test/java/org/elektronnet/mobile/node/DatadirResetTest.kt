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
}
