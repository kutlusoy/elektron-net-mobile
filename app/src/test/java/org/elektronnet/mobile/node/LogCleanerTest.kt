package org.elektronnet.mobile.node

import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogCleanerTest {
    private val now = Instant.parse("2026-10-24T12:00:00Z").toEpochMilli()
    private val day = 24L * 3600 * 1000

    private fun stamp(ms: Long) = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(ms))

    private fun temp(): File = Files.createTempDirectory("log-clean").toFile()

    @Test
    fun dropsLinesOlderThanTheLimitAndKeepsContinuationLines() {
        val dir = temp()
        val file = File(dir, "debug.log")
        file.writeText(
            "${stamp(now - 10 * day)} old line\n  continuation of old\n${stamp(now - 8 * day)} also old\n" +
                "${stamp(now - 2 * day)} recent line\n  continuation of recent\n${stamp(now - 1000)} newest\n",
        )
        val result = LogCleaner.clean(listOf(file), maxAgeDays = 7, maxBytes = 1_000_000, now = now)
        val text = file.readText()
        assertFalse("old line" in text || "continuation of old" in text)
        assertTrue("recent line" in text && "continuation of recent" in text && "newest" in text)
        assertEquals(1, result.files)
        assertTrue(result.freedBytes > 0)
        dir.deleteRecursively()
    }

    @Test
    fun cutsAFileThatIsTooBigToHalfOfTheLimitAtALineStart() {
        val dir = temp()
        val file = File(dir, "debug.log")
        val lines = (1..2000).map { "${stamp(now - 1000)} line number $it with some padding to make it longer" }
        file.writeText(lines.joinToString("\n", postfix = "\n"))
        val big = file.length()
        val limit = 40_000L
        LogCleaner.clean(listOf(file), maxAgeDays = 0, maxBytes = limit, now = now)
        assertTrue(file.length() <= limit / 2)
        assertTrue(file.length() < big)
        val kept = file.readLines()
        assertTrue(kept.all { it.startsWith("2026-") })
        assertEquals("${stamp(now - 1000)} line number 2000 with some padding to make it longer", kept.last())
        dir.deleteRecursively()
    }

    @Test
    fun leavesSmallFreshFilesAndMissingFilesAlone() {
        val dir = temp()
        val file = File(dir, "debug.log")
        file.writeText("${stamp(now - 1000)} fine\n")
        val result = LogCleaner.clean(listOf(file, File(dir, "missing.log")), maxAgeDays = 7, maxBytes = 1_000_000, now = now)
        assertEquals(0L, result.freedBytes)
        assertEquals("${stamp(now - 1000)} fine\n", file.readText())
        assertFalse(File(dir, "debug.log.trim").exists())
        dir.deleteRecursively()
    }
}

class StorageScanTest {
    @Test
    fun listsTheParts() {
        val files = Files.createTempDirectory("storage").toFile()
        fun put(path: String, size: Int) = File(files, path).also { it.parentFile.mkdirs() }.writeBytes(ByteArray(size))
        put("node/blocks/blk00000.dat", 1000)
        put("node/blocks/index/000001.ldb", 300)
        put("node/chainstate/000001.ldb", 500)
        put("node/snapshots/utxo.dat", 200)
        put("node/wallets/elektron/wallet.dat", 40)
        put("node/wallets/escrow-1/wallet.dat", 30)
        put("node/debug.log", 70)
        put("node/peers.dat", 10)
        put("network/tor/state", 5)
        put("network/i2pd/netDb/a.dat", 6)
        put("geoip/db.mmdb", 90)
        val report = StorageScan.scan(files, "node", "")
        fun bytes(kind: StorageKind, name: String = "") = report.items.first { it.kind == kind && it.name == name }.bytes
        assertEquals(1000L, bytes(StorageKind.Blocks))
        assertEquals(300L, bytes(StorageKind.Index))
        assertEquals(500L, bytes(StorageKind.Utxo))
        assertEquals(200L, bytes(StorageKind.Snapshots))
        assertEquals(40L, bytes(StorageKind.Wallet, "elektron"))
        assertEquals(30L, bytes(StorageKind.Wallet, "escrow-1"))
        assertEquals(70L, bytes(StorageKind.Logs))
        assertEquals(10L, bytes(StorageKind.OtherNode))
        assertEquals(5L, bytes(StorageKind.Tor))
        assertEquals(6L, bytes(StorageKind.I2p))
        assertEquals(90L, bytes(StorageKind.App))
        assertEquals(1000L + 300 + 500 + 200 + 40 + 30 + 70 + 10 + 5 + 6 + 90, report.total)
        files.deleteRecursively()
    }

    @Test
    fun theUtxoSetAfterASnapshotBootstrapIsCounted() {
        val files = Files.createTempDirectory("storage-snapshot").toFile()
        fun put(path: String, size: Int) = File(files, path).also { it.parentFile.mkdirs() }.writeBytes(ByteArray(size))
        put("node/chainstate/CURRENT", 0)
        put("node/chainstate_snapshot/000001.ldb", 700)
        put("node/chainstate_snapshot/base_blockhash", 32)
        val report = StorageScan.scan(files, "node", "")
        assertEquals(732L, report.items.first { it.kind == StorageKind.Utxo }.bytes)
        assertEquals(0L, report.items.first { it.kind == StorageKind.OtherNode }.bytes)
        files.deleteRecursively()
    }
}
