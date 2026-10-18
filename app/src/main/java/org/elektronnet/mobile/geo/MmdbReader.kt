package org.elektronnet.mobile.geo

import java.net.Inet4Address
import java.net.InetAddress
import java.nio.ByteBuffer

class MmdbReader(private val buffer: ByteBuffer) {
    private val nodeCount: Int
    private val recordSize: Int
    private val ipVersion: Int
    private val nodeBytes: Int
    private val searchTreeSize: Int
    private val dataStart: Int
    private val ipv4Start: Int

    init {
        val metaStart = findMetadata()
        val meta = Decoder(metaStart).decode(metaStart).first as? Map<*, *> ?: throw IllegalArgumentException("bad metadata")
        nodeCount = (meta["node_count"] as Number).toInt()
        recordSize = (meta["record_size"] as Number).toInt()
        ipVersion = (meta["ip_version"] as Number).toInt()
        require(recordSize == 24 || recordSize == 28 || recordSize == 32) { "unsupported record size $recordSize" }
        nodeBytes = recordSize / 4
        searchTreeSize = nodeCount * nodeBytes
        dataStart = searchTreeSize + DATA_SEPARATOR
        ipv4Start = if (ipVersion == 6) walkZeros(96) else 0
    }

    fun lookup(address: InetAddress): Map<*, *>? {
        val bytes = address.address
        if (ipVersion == 4 && bytes.size == 16) return null
        var node = if (bytes.size == 4 && ipVersion == 6) ipv4Start else 0
        if (node >= nodeCount) return resolve(node)
        val bits = bytes.size * 8
        for (i in 0 until bits) {
            val bit = (bytes[i shr 3].toInt() shr (7 - (i and 7))) and 1
            node = readRecord(node, bit)
            if (node >= nodeCount) return resolve(node)
        }
        return null
    }

    fun lookup(host: String): Map<*, *>? = lookup(InetAddress.getByName(host))

    private fun walkZeros(count: Int): Int {
        var node = 0
        for (i in 0 until count) {
            if (node >= nodeCount) break
            node = readRecord(node, 0)
        }
        return node
    }

    private fun resolve(record: Int): Map<*, *>? {
        if (record == nodeCount) return null
        val offset = dataStart + (record - nodeCount - DATA_SEPARATOR)
        return Decoder(dataStart).decode(offset).first as? Map<*, *>
    }

    private fun readRecord(node: Int, bit: Int): Int {
        val base = node * nodeBytes
        return when (recordSize) {
            24 -> u24(base + bit * 3)
            28 -> if (bit == 0) ((u8(base + 3) and 0xF0) shl 20) or u24(base) else ((u8(base + 3) and 0x0F) shl 24) or u24(base + 4)
            else -> u32(base + bit * 4)
        }
    }

    private fun u8(i: Int) = buffer.get(i).toInt() and 0xFF
    private fun u24(i: Int) = (u8(i) shl 16) or (u8(i + 1) shl 8) or u8(i + 2)
    private fun u32(i: Int) = (u8(i) shl 24) or (u8(i + 1) shl 16) or (u8(i + 2) shl 8) or u8(i + 3)

    private fun findMetadata(): Int {
        val marker = "«ÍïMaxMind.com".toByteArray(Charsets.ISO_8859_1)
        var i = buffer.limit() - marker.size
        while (i >= 0) {
            var match = true
            for (j in marker.indices) if (buffer.get(i + j) != marker[j]) { match = false; break }
            if (match) return i + marker.size
            i--
        }
        throw IllegalArgumentException("not an MMDB file")
    }

    private inner class Decoder(private val base: Int) {
        fun decode(offset: Int): Pair<Any?, Int> {
            var pos = offset
            val control = u8(pos++)
            var type = control shr 5
            if (type == 1) {
                val size = (control shr 3) and 3
                val low = control and 7
                val value: Int
                when (size) {
                    0 -> { value = (low shl 8) or u8(pos); pos += 1 }
                    1 -> { value = ((low shl 16) or (u8(pos) shl 8) or u8(pos + 1)) + 2048; pos += 2 }
                    2 -> { value = ((low shl 24) or (u8(pos) shl 16) or (u8(pos + 1) shl 8) or u8(pos + 2)) + 526336; pos += 3 }
                    else -> { value = u32(pos); pos += 4 }
                }
                return decode(base + value).first to pos
            }
            if (type == 0) type = 7 + u8(pos++)
            var size = control and 0x1F
            if (size >= 29) {
                when (size) {
                    29 -> { size = 29 + u8(pos); pos += 1 }
                    30 -> { size = 285 + ((u8(pos) shl 8) or u8(pos + 1)); pos += 2 }
                    else -> { size = 65821 + ((u8(pos) shl 16) or (u8(pos + 1) shl 8) or u8(pos + 2)); pos += 3 }
                }
            }
            return when (type) {
                2 -> String(ByteArray(size) { buffer.get(pos + it) }, Charsets.UTF_8) to pos + size
                3 -> buffer.getDouble(pos) to pos + 8
                15 -> buffer.getFloat(pos) to pos + 4
                4 -> ByteArray(size) { buffer.get(pos + it) } to pos + size
                5, 6, 8, 9, 10 -> {
                    var v = 0L
                    for (k in 0 until size) v = (v shl 8) or u8(pos + k).toLong()
                    v to pos + size
                }
                7 -> {
                    val map = LinkedHashMap<String, Any?>()
                    repeat(size) {
                        val key = decode(pos); pos = key.second
                        val value = decode(pos); pos = value.second
                        map[key.first as String] = value.first
                    }
                    map to pos
                }
                11 -> {
                    val list = ArrayList<Any?>()
                    repeat(size) {
                        val item = decode(pos); pos = item.second
                        list.add(item.first)
                    }
                    list to pos
                }
                14 -> (size != 0) to pos
                else -> throw IllegalArgumentException("unsupported type $type")
            }
        }
    }

    private companion object {
        const val DATA_SEPARATOR = 16
    }
}
