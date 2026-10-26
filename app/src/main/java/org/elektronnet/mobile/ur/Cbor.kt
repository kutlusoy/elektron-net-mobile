package org.elektronnet.mobile.ur

import java.io.ByteArrayOutputStream

sealed interface Cbor {
    data class Uint(val value: Long) : Cbor
    data class Neg(val value: Long) : Cbor
    data class Bytes(val value: ByteArray) : Cbor {
        override fun equals(other: Any?) = other is Bytes && value.contentEquals(other.value)
        override fun hashCode() = value.contentHashCode()
    }
    data class Text(val value: String) : Cbor
    data class Array(val items: List<Cbor>) : Cbor
    data class Map(val entries: List<Pair<Cbor, Cbor>>) : Cbor
    data class Tag(val tag: Long, val item: Cbor) : Cbor
    data class Simple(val value: Int) : Cbor

    companion object {
        fun encode(item: Cbor): ByteArray = ByteArrayOutputStream().also { write(item, it) }.toByteArray()

        fun bytes(data: ByteArray): ByteArray = encode(Bytes(data))

        private fun head(major: Int, value: Long, out: ByteArrayOutputStream) {
            val m = major shl 5
            when {
                value < 24 -> out.write(m or value.toInt())
                value < 0x100 -> { out.write(m or 24); out.write(value.toInt()) }
                value < 0x10000 -> { out.write(m or 25); out.write((value shr 8).toInt()); out.write(value.toInt()) }
                value < 0x100000000L -> { out.write(m or 26); for (s in 24 downTo 0 step 8) out.write((value shr s).toInt()) }
                else -> { out.write(m or 27); for (s in 56 downTo 0 step 8) out.write((value shr s).toInt()) }
            }
        }

        private fun write(item: Cbor, out: ByteArrayOutputStream) {
            when (item) {
                is Uint -> head(0, item.value, out)
                is Neg -> head(1, -1 - item.value, out)
                is Bytes -> { head(2, item.value.size.toLong(), out); out.write(item.value) }
                is Text -> { val b = item.value.toByteArray(Charsets.UTF_8); head(3, b.size.toLong(), out); out.write(b) }
                is Array -> { head(4, item.items.size.toLong(), out); item.items.forEach { write(it, out) } }
                is Map -> { head(5, item.entries.size.toLong(), out); item.entries.forEach { write(it.first, out); write(it.second, out) } }
                is Tag -> { head(6, item.tag, out); write(item.item, out) }
                is Simple -> out.write((7 shl 5) or item.value)
            }
        }

        // definite lengths only, which is what the UR types use
        fun decode(data: ByteArray): Cbor {
            val reader = Reader(data)
            val item = reader.read(0)
            require(reader.pos == data.size) { "Trailing CBOR data" }
            return item
        }
    }

    private class Reader(val data: ByteArray) {
        var pos = 0

        fun byte(): Int {
            require(pos < data.size) { "Truncated CBOR" }
            return data[pos++].toInt() and 0xFF
        }

        fun value(info: Int): Long = when {
            info < 24 -> info.toLong()
            info in 24..27 -> {
                var v = 0L
                repeat(1 shl (info - 24)) { v = (v shl 8) or byte().toLong() }
                v
            }
            else -> throw IllegalArgumentException("Unsupported CBOR length")
        }

        fun take(n: Long): ByteArray {
            require(n >= 0 && pos + n <= data.size) { "Truncated CBOR" }
            return data.copyOfRange(pos, pos + n.toInt()).also { pos += n.toInt() }
        }

        fun read(depth: Int): Cbor {
            require(depth < 16) { "CBOR too deep" }
            val first = byte()
            val major = first shr 5
            val info = first and 0x1F
            return when (major) {
                0 -> Uint(value(info))
                1 -> Neg(-1 - value(info))
                2 -> Bytes(take(value(info)))
                3 -> Text(String(take(value(info)), Charsets.UTF_8))
                4 -> { val n = value(info); require(n <= data.size) { "Truncated CBOR" }; Array(List(n.toInt()) { read(depth + 1) }) }
                5 -> { val n = value(info); require(n <= data.size) { "Truncated CBOR" }; Map(List(n.toInt()) { read(depth + 1) to read(depth + 1) }) }
                6 -> Tag(value(info), read(depth + 1))
                else -> Simple(first and 0x1F)
            }
        }
    }
}
