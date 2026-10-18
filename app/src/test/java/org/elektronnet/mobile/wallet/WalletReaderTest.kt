package org.elektronnet.mobile.wallet

import java.time.ZoneId
import org.elektronnet.mobile.node.Json
import org.elektronnet.mobile.ui.model.HistoryModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletReaderTest {
    private fun entries(json: String): List<TxItem> =
        WalletReader.group((Json.parse(json) as List<*>).map { it as Map<*, *> })

    private fun item(id: String, json: String) = entries(json).first { it.txid == id }

    @Test
    fun decimalConversionIsExact() {
        assertEquals(12_345_678L, WalletReader.lep(java.math.BigDecimal("0.12345678")))
        assertEquals(100_000_000L, WalletReader.lep(1L))
        assertEquals(-1L, WalletReader.lep(java.math.BigDecimal("-0.00000001")))
        assertEquals(0L, WalletReader.lep(null))
    }

    @Test
    fun receivedPaymentIsGroupedAndSigned() {
        val tx = item("a", """[{"txid":"a","category":"receive","amount":1.5,"address":"be1qaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","label":"","confirmations":0,"time":1000}]""")
        assertEquals(TxKind.Received, tx.kind)
        assertEquals(150_000_000L, tx.netLep)
        assertEquals(TxStatus.Pending, tx.status)
        assertNull(tx.label)
    }

    @Test
    fun sendIncludesFeeOnceEvenWithTwoOutputs() {
        val tx = item(
            "b",
            """[{"txid":"b","category":"send","amount":-1.0,"fee":-0.0001,"address":"x1","confirmations":3,"time":2000},
               {"txid":"b","category":"send","amount":-0.5,"fee":-0.0001,"address":"x2","confirmations":3,"time":2000}]""",
        )
        assertEquals(TxKind.Sent, tx.kind)
        assertEquals(-150_010_000L, tx.netLep)
        assertEquals(10_000L, tx.feeLep)
        assertEquals(TxStatus.Confirming, tx.status)
        assertEquals("x1", tx.address)
    }

    @Test
    fun sendToSelfIsMoved() {
        val tx = item(
            "c",
            """[{"txid":"c","category":"send","amount":-1.0,"fee":-0.00002,"address":"own","confirmations":10,"time":3000},
               {"txid":"c","category":"receive","amount":1.0,"address":"own","confirmations":10,"time":3000}]""",
        )
        assertEquals(TxKind.Moved, tx.kind)
        assertEquals(-2_000L, tx.netLep)
        assertEquals(TxStatus.Confirmed, tx.status)
    }

    @Test
    fun minedAndStatuses() {
        val mined = item("d", """[{"txid":"d","category":"immature","amount":50.0,"confirmations":5,"time":10}]""")
        assertEquals(TxKind.Mined, mined.kind)
        assertTrue(mined.immature)
        assertEquals(TxStatus.Conflicted, item("e", """[{"txid":"e","category":"receive","amount":1,"confirmations":-1,"time":1}]""").status)
        assertEquals(TxStatus.Abandoned, item("f", """[{"txid":"f","category":"send","amount":-1,"fee":-0.0001,"abandoned":true,"confirmations":0,"time":1}]""").status)
    }

    @Test
    fun readerCallsTheWalletRpc() {
        val calls = mutableListOf<String>()
        val rpc = WalletRpc { method, _, wallet ->
            calls += "$method@$wallet"
            when (method) {
                "getbalances" -> Json.parse("""{"mine":{"trusted":2.5,"untrusted_pending":0.1,"immature":0}}""")
                else -> Json.parse("""[{"txid":"a","category":"receive","amount":1,"confirmations":1,"time":5},{"txid":"b","category":"receive","amount":2,"confirmations":1,"time":9}]""")
            }
        }
        val reader = WalletReader(rpc)
        assertEquals(Balances(250_000_000L, 10_000_000L, 0L), reader.balances())
        assertEquals(listOf("b", "a"), reader.transactions().map { it.txid })
        assertEquals(listOf("getbalances@elektron", "listtransactions@elektron"), calls)
    }

    @Test
    fun equalTimestampsKeepNewestFirst() {
        val rpc = WalletRpc { _, _, _ ->
            Json.parse((1..150).joinToString(",", "[", "]") { "{\"txid\":\"t$it\",\"category\":\"receive\",\"amount\":1,\"confirmations\":1,\"time\":500}" })
        }
        val list = WalletReader(rpc).transactions()
        assertEquals(100, list.size)
        assertEquals("t150", list.first().txid)
    }

    @Test
    fun historyGroupsByDay() {
        val zone = ZoneId.of("UTC")
        val items = listOf(
            TxItem("a", TxKind.Received, 100_000_000L, null, 1, 86_400L * 10 + 100, "be1qxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", null, false, false),
            TxItem("b", TxKind.Sent, -50_000_001L, 1L, 0, 86_400L * 10 + 5000, "be1qyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy", "Rent", false, false),
            TxItem("c", TxKind.Received, 1L, null, 7, 86_400L * 9, null, null, false, false),
        )
        val days = HistoryModel.days(items, zone)
        assertEquals(2, days.size)
        assertEquals(listOf("b", "a"), days[0].rows.map { it.txid })
        assertEquals("-0.50000001", days[0].rows[0].amountText)
        assertEquals("Rent", days[0].rows[0].subtitle)
        assertEquals("+1", days[0].rows[1].amountText)
        assertEquals("be1qxx…xxxxxx", days[0].rows[1].subtitle)
        assertTrue(days[0].rows[1].incoming)
        assertFalse(days[0].rows[0].incoming)
        assertNull(days[1].rows[0].subtitle)
    }

    @Test
    fun balanceNeverShowsMisleadingZeroWhileSyncing() {
        val zero = Balances(0, 0, 0)
        assertTrue(HistoryModel.balance(zero, synced = false, hasHistory = false).waitingForSync)
        assertEquals("0", HistoryModel.balance(zero, synced = true, hasHistory = false).amountText)
        assertEquals("0", HistoryModel.balance(zero, synced = false, hasHistory = true).amountText)
        val pending = HistoryModel.balance(Balances(100_000_000L, 20_000_000L, 5_000_000L), true, true)
        assertEquals("1", pending.amountText)
        assertEquals("0.25", pending.pendingText)
        assertNull(HistoryModel.balance(null, true, false).amountText)
        assertNull(HistoryModel.balance(Balances(5, 0, 0), true, true).pendingText)
    }
}
