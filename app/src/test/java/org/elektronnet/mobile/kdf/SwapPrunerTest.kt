package org.elektronnet.mobile.kdf

import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwapPrunerTest {

    @Test
    fun testPruneSwapAndFileCleanup() {
        val tempDir = Files.createTempDirectory("kdf_prune_test").toFile()
        try {
            val dbDir = File(tempDir, "db/test_hash/SWAPS/MY").apply { mkdirs() }
            val uuid = "106c4177-9eed-4835-8039-f709b5d7c229"
            val rawSwapFile = File(dbDir, "$uuid.json").apply {
                writeText("""{"raw_sensitive":"tb1qsensitiveaddress","taker":"pubkey123"}""")
            }
            assertTrue("Raw file should exist before pruning", rawSwapFile.exists())

            val pruner = SwapPruner(tempDir)
            val swap = SwapStatus(
                uuid = uuid,
                state = "Finished",
                error = null,
                events = listOf(SwapEvent("Finished", 1794260532000L)),
                myCoin = "tELEK",
                otherCoin = "tBTC",
                myAmount = BigDecimal("0.5"),
                otherAmount = BigDecimal("0.5"),
                isPruned = false,
                secretHash = "d126f50f749336f31636fdf36f1b09c51d7b1490",
            )

            val receipt = pruner.pruneSwap(swap)
            assertTrue(receipt.isPruned)
            assertEquals("1.00000000", receipt.executionPrice.toPlainString())
            assertEquals("d126f50f749336f31636fdf36f1b09c51d7b1490", receipt.secretHash)
            assertFalse("Raw swap file with addresses must be deleted", rawSwapFile.exists())

            // Reload from disk to verify persistence
            val prunerReloaded = SwapPruner(tempDir)
            val receipts = prunerReloaded.getPrunedReceipts()
            assertEquals(1, receipts.size)
            assertEquals(uuid, receipts[0].uuid)
            assertEquals("tELEK", receipts[0].myCoin)
            assertEquals("tBTC", receipts[0].otherCoin)

            val converted = receipts[0].toSwapStatus()
            assertTrue(converted.isPruned)
            assertEquals("Finished", converted.state)
            assertEquals("d126f50f749336f31636fdf36f1b09c51d7b1490", converted.secretHash)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testPruneAllFinished() {
        val tempDir = Files.createTempDirectory("kdf_prune_all_test").toFile()
        try {
            val pruner = SwapPruner(tempDir)
            val swapFinished1 = SwapStatus("uuid-1", "Finished", null, emptyList(), "tELEK", "tBTC", BigDecimal("1.0"), BigDecimal("2.0"), isPruned = false)
            val swapFinished2 = SwapStatus("uuid-2", "Finished", null, emptyList(), "tELEK", "tBTC", BigDecimal("0.5"), BigDecimal("0.5"), isPruned = false)
            val swapRunning = SwapStatus("uuid-3", "Running", null, emptyList(), "tELEK", "tBTC", BigDecimal("0.1"), BigDecimal("0.1"), isPruned = false)

            val count = pruner.pruneAllFinished(listOf(swapFinished1, swapFinished2, swapRunning))
            assertEquals(2, count)

            val saved = pruner.getPrunedReceipts()
            assertEquals(2, saved.size)
            assertTrue(saved.any { it.uuid == "uuid-1" && it.executionPrice.compareTo(BigDecimal("2.0")) == 0 })
            assertTrue(saved.any { it.uuid == "uuid-2" && it.executionPrice.compareTo(BigDecimal("1.0")) == 0 })
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
