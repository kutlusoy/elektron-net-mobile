package org.elektronnet.mobile.ur

import java.security.MessageDigest

// xoshiro256** seeded with the SHA-256 of the seed, the generator of the fountain code in BCR-2020-005
class Xoshiro(seed: ByteArray) {
    private val s = ULongArray(4)

    init {
        val digest = MessageDigest.getInstance("SHA-256").digest(seed)
        for (i in 0 until 4) {
            var v = 0uL
            for (n in 0 until 8) v = (v shl 8) or digest[i * 8 + n].toUByte().toULong()
            s[i] = v
        }
    }

    private fun rotl(x: ULong, k: Int): ULong = (x shl k) or (x shr (64 - k))

    fun next(): ULong {
        val result = rotl(s[1] * 5uL, 7) * 9uL
        val t = s[1] shl 17
        s[2] = s[2] xor s[0]
        s[3] = s[3] xor s[1]
        s[1] = s[1] xor s[2]
        s[0] = s[0] xor s[3]
        s[2] = s[2] xor t
        s[3] = rotl(s[3], 45)
        return result
    }

    fun nextDouble(): Double = next().toDouble() / TWO_POW_64

    fun nextInt(low: Int, high: Int): Int = Math.floor(nextDouble() * (high - low + 1) + low).toInt()

    private companion object {
        const val TWO_POW_64 = 18446744073709551616.0
    }
}
