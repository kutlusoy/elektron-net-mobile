package org.elektronnet.mobile.explorer

import java.math.BigDecimal
import java.net.InetAddress
import java.net.ServerSocket
import org.elektronnet.mobile.wallet.FeeLevel
import org.elektronnet.mobile.wallet.FeePresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerListTest {
    @Test
    fun normalizesAndValidates() {
        assertEquals("https://mempool.elektron-net.org", ExplorerList.normalize(" HTTPS://Mempool.Elektron-Net.org/ "))
        assertEquals("https://example.org/prefix", ExplorerList.normalize("https://example.org/prefix/"))
        assertNull(ExplorerList.normalize("ftp://example.org"))
        assertNull(ExplorerList.normalize("https://"))
        assertNull(ExplorerList.normalize("https://user@example.org"))
        assertNull(ExplorerList.normalize("https://example.org/a b"))
        assertNull(ExplorerList.normalize("https://example.org/?q=1"))
    }

    @Test
    fun checksInsecureDuplicateAndFull() {
        val existing = listOf("https://a.org", "https://b.org")
        assertTrue(ExplorerList.check("http://c.org", existing) is ExplorerList.Check.Insecure)
        assertTrue(ExplorerList.check("http://localhost:3000", existing) is ExplorerList.Check.Ok)
        assertTrue(ExplorerList.check("https://A.org/", existing) is ExplorerList.Check.Duplicate)
        assertTrue(ExplorerList.check("https://a.org", existing, replacing = "https://a.org") is ExplorerList.Check.Ok)
        assertTrue(ExplorerList.check("nonsense", existing) is ExplorerList.Check.Invalid)
        val full = (1..ExplorerList.MAX_ENTRIES).map { "https://h$it.org" }
        assertTrue(ExplorerList.check("https://new.org", full) is ExplorerList.Check.Full)
    }

    @Test
    fun storageRoundTripAndDefault() {
        assertEquals(listOf(ExplorerList.DEFAULT_URL), ExplorerList.parse(null))
        val urls = listOf("https://a.org", "https://b.org/x")
        assertEquals(urls, ExplorerList.parse(ExplorerList.serialize(urls)))
        assertEquals(emptyList<String>(), ExplorerList.parse("[]"))
        assertEquals("https://a.org/tx/ab", ExplorerList.txUrl("https://a.org", "ab"))
    }
}

class ExplorerClientTest {
    @Test
    fun parsesPreciseAndWholeNumberFees() {
        val precise = ExplorerClient.parse("""{"fastestFee":5.2,"halfHourFee":2.1,"hourFee":1.5,"economyFee":0.5,"minimumFee":0.1}""")!!
        assertEquals(BigDecimal("5.2"), precise.fastest)
        assertEquals(BigDecimal("0.1"), precise.minimum)
        val whole = ExplorerClient.parse("""{"fastestFee":5,"halfHourFee":2}""")!!
        assertEquals(BigDecimal(2), whole.hour)
        assertNull(ExplorerClient.parse("""{"fastestFee":0}"""))
        assertNull(ExplorerClient.parse("""{"fastestFee":99999}"""))
        assertNull(ExplorerClient.parse("not json"))
    }

    private fun server(replies: Map<String, Pair<Int, String>>): Pair<ServerSocket, Thread> {
        val socket = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val thread = Thread {
            while (!socket.isClosed) {
                try {
                    val client = socket.accept()
                    val request = client.getInputStream().bufferedReader().readLine().orEmpty()
                    val path = request.split(" ").getOrNull(1).orEmpty()
                    val (code, body) = replies[path] ?: (404 to "")
                    val out = client.getOutputStream()
                    out.write("HTTP/1.1 $code X\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body".toByteArray())
                    out.flush()
                    client.close()
                } catch (e: Exception) {
                }
            }
        }.also { it.isDaemon = true; it.start() }
        return socket to thread
    }

    @Test
    fun usesTheNextPathAndTheNextServer() {
        val (bad, _) = server(emptyMap())
        val (good, _) = server(mapOf("/api/v1/fees/recommended" to (200 to """{"fastestFee":4,"halfHourFee":3,"hourFee":2,"economyFee":1,"minimumFee":1}""")))
        try {
            val client = ExplorerClient(timeoutMs = 2_000)
            val fees = client.fees(listOf("http://127.0.0.1:${bad.localPort}", "http://127.0.0.1:${good.localPort}"))!!
            assertEquals(BigDecimal(4), fees.fastest)
            assertNull(client.fees(listOf("http://127.0.0.1:${bad.localPort}")))
        } finally {
            bad.close()
            good.close()
        }
    }

    @Test
    fun explorerFeesFillOnlyTheMissingLevels() {
        val merged = mapOf(FeeLevel.Slow to null, FeeLevel.Normal to BigDecimal("3"), FeeLevel.Fast to BigDecimal("6"))
        val options = FeePresets.derive(mapOf(FeeLevel.Slow to BigDecimal("2"), FeeLevel.Normal to BigDecimal("3"), FeeLevel.Fast to BigDecimal("6")), BigDecimal("0.1"), setOf(FeeLevel.Slow))
        assertTrue(options[0].fromExplorer)
        assertTrue(!options[1].fromExplorer)
        assertEquals(3, merged.size)
    }
}
