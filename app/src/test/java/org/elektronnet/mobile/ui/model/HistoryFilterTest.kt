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
}
