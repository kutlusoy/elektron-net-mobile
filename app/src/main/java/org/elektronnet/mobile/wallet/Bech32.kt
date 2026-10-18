package org.elektronnet.mobile.wallet

data class Bech32Decoded(val hrp: String, val version: Int, val program: ByteArray)

object Bech32 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val BECH32_CONST = 1
    private const val BECH32M_CONST = 0x2bc830a3
    private val GENERATOR = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    fun encode(hrp: String, version: Int, program: ByteArray): String {
        val data = intArrayOf(version) + convertBits(program, 8, 5, true)!!
        val constant = if (version == 0) BECH32_CONST else BECH32M_CONST
        val values = expandHrp(hrp) + data + IntArray(6)
        val mod = polymod(values) xor constant
        val checksum = IntArray(6) { (mod shr (5 * (5 - it))) and 31 }
        return hrp + "1" + (data + checksum).map { CHARSET[it] }.joinToString("")
    }

    fun decode(text: String): Bech32Decoded? {
        if (text.length < 8 || text.length > 90) return null
        if (text.any { it.code < 33 || it.code > 126 }) return null
        if (text != text.lowercase() && text != text.uppercase()) return null
        val lower = text.lowercase()
        val sep = lower.lastIndexOf('1')
        if (sep < 1 || sep + 7 > lower.length) return null
        val hrp = lower.substring(0, sep)
        val values = lower.substring(sep + 1).map { CHARSET.indexOf(it).also { i -> if (i < 0) return null } }.toIntArray()
        val check = polymod(expandHrp(hrp) + values)
        val version = values[0]
        val expected = if (version == 0) BECH32_CONST else BECH32M_CONST
        if (check != expected || version > 16) return null
        val program = convertBits(values.copyOfRange(1, values.size - 6).map { it.toByte() }.toByteArray(), 5, 8, false)?.map { it.toByte() }?.toByteArray() ?: return null
        if (program.size < 2 || program.size > 40) return null
        if (version == 0 && program.size != 20 && program.size != 32) return null
        return Bech32Decoded(hrp, version, program)
    }

    private fun polymod(values: IntArray): Int {
        var chk = 1
        for (v in values) {
            val top = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v
            for (i in 0 until 5) if ((top shr i) and 1 == 1) chk = chk xor GENERATOR[i]
        }
        return chk
    }

    private fun expandHrp(hrp: String): IntArray =
        IntArray(hrp.length) { hrp[it].code shr 5 } + intArrayOf(0) + IntArray(hrp.length) { hrp[it].code and 31 }

    private fun convertBits(data: ByteArray, from: Int, to: Int, pad: Boolean): IntArray? {
        var acc = 0
        var bits = 0
        val out = mutableListOf<Int>()
        val maxv = (1 shl to) - 1
        for (b in data) {
            val value = b.toInt() and 0xff
            if (value shr from != 0) return null
            acc = (acc shl from) or value
            bits += from
            while (bits >= to) {
                bits -= to
                out.add((acc shr bits) and maxv)
            }
            acc = acc and ((1 shl bits) - 1)
        }
        if (pad) {
            if (bits > 0) out.add((acc shl (to - bits)) and maxv)
        } else if (bits >= from || ((acc shl (to - bits)) and maxv) != 0) {
            return null
        }
        return out.toIntArray()
    }
}
