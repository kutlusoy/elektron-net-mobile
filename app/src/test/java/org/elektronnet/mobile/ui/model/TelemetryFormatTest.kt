package org.elektronnet.mobile.ui.model

import org.elektronnet.mobile.node.Telemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelemetryFormatTest {
    @Test
    fun formatsBytes() {
        assertEquals("301 B", TelemetryFormat.bytes(301))
        assertEquals("1.5 KB", TelemetryFormat.bytes(1536))
        assertEquals("2.0 GB", TelemetryFormat.bytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun formatsHashRateAndDifficulty() {
        assertEquals("-", TelemetryFormat.hashRate(0.0))
        assertEquals("1.50 MH/s", TelemetryFormat.hashRate(1_500_000.0))
        assertEquals("0.00781238", TelemetryFormat.difficulty(0.00781238))
        assertEquals("12345", TelemetryFormat.difficulty(12345.4))
    }

    @Test
    fun pruneScheduleCountsDown() {
        val early = PruneSchedule.of(0, 1_000)
        assertEquals(197_280L, early.remainingBlocks)
        assertEquals(1_000 + 197_280L * 60_000, early.estimatedMs)
        val done = PruneSchedule.of(300_000, 1_000)
        assertEquals(0L, done.remainingBlocks)
        assertNull(done.estimatedMs)
    }

    @Test
    fun parsesTelemetry() {
        val t = Telemetry.parse(
            mapOf("difficulty" to 2.5, "size_on_disk" to 301L),
            mapOf("networkhashps" to 10.0),
            mapOf("size" to 3L, "bytes" to 900L),
        )
        assertEquals(2.5, t.difficulty!!, 0.0001)
        assertEquals(3L, t.mempoolTx)
        assertEquals(301L, t.sizeOnDisk)
        assertNull(Telemetry.parse(null, null, null).difficulty)
    }
}
