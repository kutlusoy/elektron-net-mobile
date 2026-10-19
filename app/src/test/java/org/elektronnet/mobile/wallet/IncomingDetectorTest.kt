package org.elektronnet.mobile.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingDetectorTest {
    private class Memory : SeenStore {
        override var ids: List<String>? = null
    }

    private fun tx(id: String, kind: TxKind = TxKind.Received, confirmations: Int = 0) =
        TxItem(id, kind, 1000, null, confirmations, 0, null, null, false, false)

    @Test
    fun firstPollOnlySeeds() {
        val store = Memory()
        val detector = IncomingDetector(store)
        assertTrue(detector.poll(listOf(tx("a"), tx("b"))).isEmpty())
        assertEquals(listOf("a", "b"), store.ids)
    }

    @Test
    fun reportsOnlyNewIncomingOnce() {
        val store = Memory()
        val detector = IncomingDetector(store)
        detector.poll(listOf(tx("a")))
        val fresh = detector.poll(listOf(tx("a"), tx("b"), tx("c", TxKind.Sent)))
        assertEquals(listOf("b"), fresh.map { it.txid })
        assertTrue(detector.poll(listOf(tx("a"), tx("b"))).isEmpty())
    }

    @Test
    fun trimsTheSeenListAndSkipsConflicts() {
        val store = Memory()
        val detector = IncomingDetector(store, keep = 3)
        detector.poll(listOf(tx("a")))
        detector.poll((1..5).map { tx("t$it") })
        assertEquals(listOf("t3", "t4", "t5"), store.ids)
        assertTrue(detector.poll(listOf(tx("x", confirmations = -1))).isEmpty())
    }
}
