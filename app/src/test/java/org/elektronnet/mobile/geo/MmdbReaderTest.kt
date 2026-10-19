package org.elektronnet.mobile.geo

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MmdbReaderTest {
    private fun str(s: String): ByteArray {
        val b = s.toByteArray()
        require(b.size < 29)
        return byteArrayOf((0x40 or b.size).toByte()) + b
    }

    private fun map(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0xE0 or entries.size)
        for ((k, v) in entries) { out.write(str(k)); out.write(v) }
        return out.toByteArray()
    }

    private fun uint(v: Int): ByteArray = byteArrayOf(0xA1.toByte(), v.toByte())

    // Binary tree over the first `bits` bits of the prefix, a data record at the end, 24 bit records
    private fun build(ipVersion: Int, prefix: ByteArray, bits: Int, data: ByteArray, recordOffset: Int = 0): ByteArray {
        val nodes = bits
        val nodeCount = nodes
        val tree = ByteArray(nodeCount * 6)
        fun put(node: Int, side: Int, value: Int) {
            val o = node * 6 + side * 3
            tree[o] = (value shr 16).toByte(); tree[o + 1] = (value shr 8).toByte(); tree[o + 2] = value.toByte()
        }
        for (n in 0 until nodes) {
            val bit = (prefix[n shr 3].toInt() shr (7 - (n and 7))) and 1
            val next = if (n == nodes - 1) nodeCount + 16 + recordOffset else n + 1
            put(n, bit, next)
            put(n, 1 - bit, nodeCount)
        }
        val meta = map(
            "node_count" to byteArrayOf(0xC2.toByte()) + byteArrayOf((nodeCount shr 8).toByte(), nodeCount.toByte()),
            "record_size" to uint(24),
            "ip_version" to uint(ipVersion),
        )
        val out = ByteArrayOutputStream()
        out.write(tree); out.write(ByteArray(16)); out.write(data)
        out.write("«ÍïMaxMind.com".toByteArray(Charsets.ISO_8859_1)); out.write(meta)
        return out.toByteArray()
    }

    private val country = map("country" to map("iso_code" to str("ES"), "names" to map("en" to str("Spain"))))

    @Test
    fun findsAnIpv4PrefixInAnIpv4Tree() {
        val db = MmdbReader(ByteBuffer.wrap(build(4, byteArrayOf(8, 8, 8, 0), 24, country)))
        val hit = db.lookup("8.8.8.8")!!
        assertEquals("ES", ((hit["country"] as Map<*, *>)["iso_code"]))
        assertNull(db.lookup("9.9.9.9"))
    }

    @Test
    fun findsAnIpv4PrefixInAnIpv6Tree() {
        val tree = ByteArray(16).also { it[12] = 8; it[13] = 8; it[14] = 8 }
        val db = MmdbReader(ByteBuffer.wrap(build(6, tree, 96 + 24, country)))
        assertEquals("ES", ((db.lookup("8.8.8.1")!!["country"] as Map<*, *>)["iso_code"]))
        assertNull(db.lookup("1.2.3.4"))
    }

    @Test
    fun followsPointersIntoTheDataSection() {
        val data = str("ES") + map("country" to map("iso_code" to byteArrayOf(0x20, 0x00)))
        val db = MmdbReader(ByteBuffer.wrap(build(4, byteArrayOf(8, 8, 8, 0), 24, data, recordOffset = 3)))
        assertEquals("ES", ((db.lookup("8.8.8.8")!!["country"] as Map<*, *>)["iso_code"]))
    }
}
