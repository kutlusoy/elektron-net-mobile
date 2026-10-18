package org.elektronnet.mobile.wallet

import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Bip32 {
    private val curveOrder = BigInteger("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", 16)
    private val mainnetPrivate = byteArrayOf(0x04, 0x88.toByte(), 0xAD.toByte(), 0xE4.toByte())
    private val testnetPrivate = byteArrayOf(0x04, 0x35, 0x83.toByte(), 0x94.toByte())

    fun masterExtendedKey(seed: ByteArray, testnet: Boolean): String {
        require(seed.size in 16..64) { "seed must be 16 to 64 bytes" }
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec("Bitcoin seed".toByteArray(), "HmacSHA512"))
        val digest = mac.doFinal(seed)
        val key = digest.copyOfRange(0, 32)
        val chainCode = digest.copyOfRange(32, 64)
        val k = BigInteger(1, key)
        check(k.signum() != 0 && k < curveOrder) { "invalid master key, use another seed" }
        val payload = (if (testnet) testnetPrivate else mainnetPrivate) +
            byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0) + chainCode + byteArrayOf(0) + key
        digest.fill(0)
        key.fill(0)
        return Base58.encodeChecked(payload)
    }
}

object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    fun encodeChecked(payload: ByteArray): String {
        val sha = MessageDigest.getInstance("SHA-256")
        val checksum = sha.digest(sha.digest(payload)).copyOfRange(0, 4)
        val data = payload + checksum
        var value = BigInteger(1, data)
        val base = BigInteger.valueOf(58)
        val out = StringBuilder()
        while (value.signum() > 0) {
            val (q, r) = value.divideAndRemainder(base)
            out.append(ALPHABET[r.toInt()])
            value = q
        }
        for (b in data) {
            if (b.toInt() == 0) out.append(ALPHABET[0]) else break
        }
        return out.reverse().toString()
    }

    fun decodeChecked(text: String): ByteArray? {
        var value = BigInteger.ZERO
        val base = BigInteger.valueOf(58)
        for (c in text) {
            val digit = ALPHABET.indexOf(c)
            if (digit < 0) return null
            value = value.multiply(base).add(BigInteger.valueOf(digit.toLong()))
        }
        val raw = value.toByteArray().dropWhile { it.toInt() == 0 }.toByteArray()
        val zeros = text.takeWhile { it == ALPHABET[0] }.length
        val data = ByteArray(zeros) + raw
        if (data.size < 5) return null
        val payload = data.copyOfRange(0, data.size - 4)
        val sha = MessageDigest.getInstance("SHA-256")
        val expected = sha.digest(sha.digest(payload)).copyOfRange(0, 4)
        return if (expected.contentEquals(data.copyOfRange(data.size - 4, data.size))) payload else null
    }
}
