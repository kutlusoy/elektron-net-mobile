package org.elektronnet.mobile.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryExportTest {
    private fun item(txid: String, net: Long, time: Long, label: String? = null, kind: TxKind = TxKind.Received) =
        TxItem(txid, kind, net, null, 3, time, "be1qexample", label, abandoned = false, immature = false)

    @Test
    fun writesSortedRowsWithHeader() {
        val csv = HistoryExport.csv(listOf(item("b", 200_000_000, 2_000), item("a", -150_000_000, 1_000, kind = TxKind.Sent)))
        val lines = csv.trim().split("\n")
        assertEquals(HistoryExport.HEADER, lines[0])
        assertTrue(lines[1].startsWith("a,1970-01-01T00:16:40Z,sent,-1.5"))
        assertTrue(lines[2].startsWith("b,"))
    }

    @Test
    fun escapesQuotesCommasAndFormulaStarts() {
        val csv = HistoryExport.csv(listOf(item("x", 1, 1, label = "=SUM(A1), \"hi\"")))
        assertTrue(csv, csv.contains("\"'=SUM(A1), \"\"hi\"\"\""))
    }
}
