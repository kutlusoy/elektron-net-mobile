package org.elektronnet.mobile.wallet

import java.text.Normalizer
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class ElectrumSeedType {
    Standard, Segwit, TwoFactor, TwoFactorSegwit
}

object ElectrumSeed {
    private const val PREFIX_STANDARD = "01"
    private const val PREFIX_SEGWIT = "100"
    private const val PREFIX_2FA = "101"
    private const val PREFIX_2FA_SEGWIT = "102"

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKD).lowercase().trim().split(Regex("\\s+")).joinToString(" ")

    fun seedType(words: List<String>): ElectrumSeedType? {
        if (words.size !in setOf(12, 13, 14, 15, 18, 24)) return null
        val normalized = normalize(words.joinToString(" "))
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec("Seed version".toByteArray(Charsets.UTF_8), "HmacSHA512"))
        val digest = mac.doFinal(normalized.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }

        return when {
            hex.startsWith(PREFIX_SEGWIT) -> ElectrumSeedType.Segwit
            hex.startsWith(PREFIX_STANDARD) -> ElectrumSeedType.Standard
            hex.startsWith(PREFIX_2FA) -> ElectrumSeedType.TwoFactor
            hex.startsWith(PREFIX_2FA_SEGWIT) -> ElectrumSeedType.TwoFactorSegwit
            else -> null
        }
    }

    fun validate(words: List<String>): Boolean = seedType(words) != null

    fun toSeed(words: List<String>, passphrase: String = ""): ByteArray {
        val password = normalize(words.joinToString(" ")).toCharArray()
        val salt = ("electrum" + normalize(passphrase)).toByteArray(Charsets.UTF_8)
        val spec = PBEKeySpec(password, salt, 2048, 512)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            password.fill('\u0000')
        }
    }
}
