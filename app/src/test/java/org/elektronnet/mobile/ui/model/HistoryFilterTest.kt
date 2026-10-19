package org.elektronnet.mobile.ui.model

import org.elektronnet.mobile.wallet.TxItem
import org.elektronnet.mobile.wallet.TxKind
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFilterTest {
    private fun tx(id: String, net: Long, address: String? = null, label: String? = null) =
        TxItem(id, if (net > 0) TxKind.Received else TxKind.Sent, net, null, 6, 1_000, address, label, abandoned = false, immature = false)

    private val items = listOf(tx("aa11", 5, "be1qalice", "Alice"), tx("bb22", -3, "be1qbob"), tx("cc33", 7, "be1qcarol", "Rent"))

    @Test
    fun filtersByDirection() {
        assertEquals(3, HistoryModel.filter(items, HistoryFilter.All, "").size)
        assertEquals(listOf("aa11", "cc33"), HistoryModel.filter(items, HistoryFilter.Received, "").map { it.txid })
        assertEquals(listOf("bb22"), HistoryModel.filter(items, HistoryFilter.Sent, "").map { it.txid })
    }

    @Test
    fun searchesIdAddressAndLabelIgnoringCase() {
        assertEquals(listOf("aa11"), HistoryModel.filter(items, HistoryFilter.All, " ALICE ").map { it.txid })
        assertEquals(listOf("bb22"), HistoryModel.filter(items, HistoryFilter.All, "be1qbob").map { it.txid })
        assertEquals(listOf("cc33"), HistoryModel.filter(items, HistoryFilter.All, "cc3").map { it.txid })
        assertEquals(emptyList<TxItem>(), HistoryModel.filter(items, HistoryFilter.Sent, "alice"))
    }

    @Test
    fun pagesHoldTenNewestFirstAndSearchSpansEverything() {
        val many = (1..25).map { n -> TxItem("tx$n", TxKind.Received, 5, null, 6, n.toLong() * 100, null, if (n == 3) "Rent" else null, abandoned = false, immature = false) }
        assertEquals(3, HistoryModel.pageCount(many.size))
        assertEquals((25 downTo 16).map { "tx$it" }, HistoryModel.page(many, 0).map { it.txid })
        assertEquals((5 downTo 1).map { "tx$it" }, HistoryModel.page(many, 2).map { it.txid })
        assertEquals(listOf("tx5", "tx4", "tx3", "tx2", "tx1").size, HistoryModel.page(many, 99).size)
        assertEquals(1, HistoryModel.pageCount(0))
        val hits = HistoryModel.filter(many, HistoryFilter.All, "rent")
        assertEquals(listOf("tx3"), HistoryModel.page(hits, 0).map { it.txid })
    }
}
