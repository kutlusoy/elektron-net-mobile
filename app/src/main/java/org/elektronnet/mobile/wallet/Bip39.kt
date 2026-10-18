package org.elektronnet.mobile.wallet

import java.security.MessageDigest
import java.text.Normalizer
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class Bip39(private val words: List<String>) {
    init {
        require(words.size == 2048) { "BIP-39 word list must have 2048 entries" }
    }

    private val index = words.withIndex().associate { it.value to it.index }

    fun fromEntropy(entropy: ByteArray): List<String> {
        require(entropy.size in setOf(16, 20, 24, 28, 32)) { "entropy must be 128 to 256 bits in 32 bit steps" }
        val checksumBits = entropy.size * 8 / 32
        val hash = MessageDigest.getInstance("SHA-256").digest(entropy)
        val total = entropy.size * 8 + checksumBits
        return (0 until total / 11).map { i ->
            var value = 0
            for (b in 0 until 11) {
                val pos = i * 11 + b
                val bit = if (pos < entropy.size * 8) bitOf(entropy, pos) else bitOf(hash, pos - entropy.size * 8)
                value = (value shl 1) or bit
            }
            words[value]
        }
    }

    fun validate(mnemonic: List<String>): Boolean {
        if (mnemonic.size !in setOf(12, 15, 18, 21, 24)) return false
        val indices = mnemonic.map { index[it] ?: return false }
        val total = indices.size * 11
        val entropyBits = total * 32 / 33
        val entropy = ByteArray(entropyBits / 8)
        for (pos in 0 until entropyBits) {
            val bit = (indices[pos / 11] shr (10 - pos % 11)) and 1
            if (bit == 1) entropy[pos / 8] = (entropy[pos / 8].toInt() or (0x80 shr (pos % 8))).toByte()
        }
        val expected = fromEntropy(entropy)
        return expected == mnemonic
    }

    fun isWord(candidate: String): Boolean = candidate in index

    fun suggestions(prefix: String, limit: Int = 5): List<String> =
        if (prefix.isEmpty()) emptyList() else words.filter { it.startsWith(prefix) }.take(limit)

    companion object {
        fun parse(text: String): List<String> =
            Normalizer.normalize(text, Normalizer.Form.NFKD).lowercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

        fun toSeed(mnemonic: List<String>, passphrase: String = ""): ByteArray {
            val password = Normalizer.normalize(mnemonic.joinToString(" "), Normalizer.Form.NFKD).toCharArray()
            val salt = Normalizer.normalize("mnemonic$passphrase", Normalizer.Form.NFKD).toByteArray(Charsets.UTF_8)
            val spec = PBEKeySpec(password, salt, 2048, 512)
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
                password.fill('\u0000')
            }
        }

        private fun bitOf(bytes: ByteArray, pos: Int): Int = (bytes[pos / 8].toInt() shr (7 - pos % 8)) and 1
    }
}
