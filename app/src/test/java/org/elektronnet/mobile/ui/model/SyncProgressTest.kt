package org.elektronnet.mobile.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncProgressTest {
    @Test
    fun readsThePhaseFromTheLatestMatchingLine() {
        val lines = listOf("2026 Loading block index...", "2026 Verifying blocks...", "2026 something else")
        assertEquals(NodePhase.VerifyingBlocks, NodeLogPhase.of(lines))
        assertEquals(NodePhase.Other, NodeLogPhase.of(listOf("nothing known")))
        assertEquals(NodePhase.LoadingIndex, NodeLogPhase.of(listOf("Loading block index")))
    }

    @Test
    fun detectsRescanningPhaseAndProgress() {
        val lines = listOf(
            "2026-11-08 Rescan started from block 0...",
            "2026-11-08 Still rescanning. At block 12345. Progress=0.570000",
        )
        assertEquals(NodePhase.ScanningWallet, NodeLogPhase.of(lines))
        assertEquals(0.57, NodeLogPhase.parseRescanProgress(lines)!!, 0.001)

        val percentageLines = listOf("Rescanning… 57%")
        assertEquals(NodePhase.ScanningWallet, NodeLogPhase.of(percentageLines))
        assertEquals(0.57, NodeLogPhase.parseRescanProgress(percentageLines)!!, 0.001)
    }

    @Test
    fun estimatesFromTheObservedRate() {
        val eta = SyncEta()
        eta.add(0, 0)
        assertNull(eta.remainingMs(100))
        eta.add(60_000, 600)
        assertEquals(600_000L, eta.remainingMs(6_000))
        assertNull(eta.remainingMs(0))
    }

    @Test
    fun noProgressMeansNoEstimate() {
        val eta = SyncEta()
        eta.add(0, 10)
        eta.add(60_000, 10)
        assertNull(eta.remainingMs(500))
    }
}
