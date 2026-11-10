package org.elektronnet.mobile.wallet

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.elektronnet.mobile.node.Json

sealed interface ElectrumWalletContent {
    data class Seed(val words: List<String>, val type: ElectrumSeedType, val passphrase: String = "") : ElectrumWalletContent
    data class MasterKey(val xprv: String, val type: ElectrumSeedType) : ElectrumWalletContent
    data class ImportedKeys(val wifKeys: List<String>) : ElectrumWalletContent
}

class InvalidElectrumPasswordException(message: String = "Invalid password") : Exception(message)
class CorruptedElectrumWalletException(message: String) : Exception(message)

object ElectrumWalletParser {
    private val BIE1_MAGIC = byteArrayOf(0x42, 0x49, 0x45, 0x31)

    fun isElectrumWallet(raw: String): Boolean {
        val trimmed = raw.trim()
        return isElectrumEncrypted(trimmed) || isElectrumPlaintext(trimmed)
    }

    fun isElectrumEncrypted(trimmed: String): Boolean = trimmed.startsWith("QklFMQ")

    fun isElectrumPlaintext(trimmed: String): Boolean =
        trimmed.startsWith("{") && (trimmed.contains("\"wallet_type\"") || trimmed.contains("\"keystore\""))

    fun parse(raw: String, password: String = ""): ElectrumWalletContent {
        val trimmed = raw.trim()
        val jsonStr = when {
            isElectrumEncrypted(trimmed) -> decryptStorage(trimmed, password)
            isElectrumPlaintext(trimmed) -> trimmed
            else -> throw CorruptedElectrumWalletException("Unrecognized wallet file format")
        }
        return parseJson(jsonStr, password)
    }

    fun decryptStorage(base64Ciphertext: String, password: String): String {
        val data = try {
            Base64.getDecoder().decode(base64Ciphertext.trim())
        } catch (e: IllegalArgumentException) {
            throw CorruptedElectrumWalletException("Not valid Base64: ${e.message}")
        }
        if (data.size < 85) throw CorruptedElectrumWalletException("Ciphertext too short: ${data.size} bytes")
        if (!data.copyOfRange(0, 4).contentEquals(BIE1_MAGIC)) {
            throw CorruptedElectrumWalletException("Invalid magic bytes, expected BIE1")
        }

        val ephemeralPubkeyBytes = data.copyOfRange(4, 37)
        val ciphertext = data.copyOfRange(37, data.size - 32)
        val mac = data.copyOfRange(data.size - 32, data.size)

        // 1. Derive secret scalar from password via PBKDF2-HMAC-SHA512 (iterations=1024, salt=empty)
        val secretBytes = pbkdf2HmacSha512EmptySalt(password.toByteArray(Charsets.UTF_8), 1024)
        val scalar = BigInteger(1, secretBytes).mod(Secp256k1.ORDER)
        if (scalar.signum() == 0) throw InvalidElectrumPasswordException("Derived zero scalar")

        // 2. Compute ECDH shared point: scalar * ephemeralPubkey
        val ephPoint = Secp256k1.decompress(ephemeralPubkeyBytes)
        val sharedPoint = Secp256k1.multiply(scalar, ephPoint)
        val ecdhKey = Secp256k1.compress(sharedPoint)

        // 3. Hash ECDH key with SHA-512 to split into iv, key_e, key_m
        val key = MessageDigest.getInstance("SHA-512").digest(ecdhKey)
        val iv = key.copyOfRange(0, 16)
        val keyE = key.copyOfRange(16, 32)
        val keyM = key.copyOfRange(32, 64)

        // 4. Verify HMAC-SHA256
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(keyM, "HmacSHA256"))
        val computedMac = hmac.doFinal(data.copyOfRange(0, data.size - 32))
        if (!MessageDigest.isEqual(computedMac, mac)) {
            throw InvalidElectrumPasswordException()
        }

        // 5. Decrypt AES-128-CBC
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyE, "AES"), IvParameterSpec(iv))
        val decompressedBytes = try {
            val decrypted = cipher.doFinal(ciphertext)
            decompressZlib(decrypted)
        } catch (e: Exception) {
            throw InvalidElectrumPasswordException("Decryption failed: ${e.message}")
        }
        return decompressedBytes.toString(Charsets.UTF_8)
    }

    private fun decompressZlib(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        try {
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && inflater.needsInput()) break
                out.write(buffer, 0, count)
            }
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }

    @Suppress("UNCHECKED_CAST")
    fun parseJson(jsonStr: String, password: String): ElectrumWalletContent {
        val root = (Json.parse(jsonStr) as? Map<*, *>)
            ?: throw CorruptedElectrumWalletException("Invalid JSON root object")
        val useEncryption = root["use_encryption"] == true
        val walletType = root["wallet_type"]?.toString() ?: "standard"

        if (walletType == "imported") {
            val keypairsMap = (root["keypairs"] as? Map<*, *>)
                ?: ((root["keystore"] as? Map<*, *>)?.get("keypairs") as? Map<*, *>)
                ?: emptyMap<Any?, Any?>()
            val wifList = mutableListOf<String>()
            for ((_, privVal) in keypairsMap) {
                if (privVal == null) continue
                val privStr = privVal.toString()
                val wif = if (useEncryption) {
                    try {
                        pwDecode(privStr, password)
                    } catch (e: Exception) {
                        pwDecodeWithMac(privStr, password)
                    }
                } else {
                    privStr
                }
                val cleanWif = if (wif.contains(':')) wif.substringAfter(':') else wif
                wifList.add(cleanWif)
            }
            if (wifList.isEmpty()) {
                throw CorruptedElectrumWalletException("Imported wallet contains no keypairs")
            }
            return ElectrumWalletContent.ImportedKeys(wifList)
        }

        // Deterministic or standard wallet
        val keystore = (root["keystore"] as? Map<*, *>)
            ?: throw CorruptedElectrumWalletException("Missing keystore object")

        if (keystore.containsKey("seed")) {
            var seedStr = keystore["seed"]?.toString() ?: ""
            if (useEncryption) {
                seedStr = pwDecode(seedStr, password)
            }
            val seedTypeStr = keystore["seed_type"]?.toString() ?: "segwit"
            val type = when (seedTypeStr.lowercase()) {
                "standard" -> ElectrumSeedType.Standard
                "2fa" -> ElectrumSeedType.TwoFactor
                "2fa_segwit" -> ElectrumSeedType.TwoFactorSegwit
                else -> ElectrumSeedType.Segwit
            }
            val words = seedStr.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val passphrase = keystore["passphrase"]?.toString() ?: ""
            val decodedPassphrase = if (useEncryption && passphrase.isNotEmpty()) pwDecode(passphrase, password) else passphrase
            return ElectrumWalletContent.Seed(words, type, decodedPassphrase)
        }

        if (keystore.containsKey("xprv")) {
            var xprv = keystore["xprv"]?.toString() ?: ""
            if (useEncryption) {
                xprv = pwDecode(xprv, password)
            }
            return ElectrumWalletContent.MasterKey(xprv, ElectrumSeedType.Segwit)
        }

        throw CorruptedElectrumWalletException("Keystore has neither seed nor xprv")
    }

    fun pwDecode(dataBase64: String, password: String): String {
        if (password.isEmpty()) return dataBase64
        val data = Base64.getDecoder().decode(dataBase64)
        if (data.size < 16) throw InvalidElectrumPasswordException("Encrypted data too short")
        val iv = data.copyOfRange(0, 16)
        val ciphertext = data.copyOfRange(16, data.size)
        val secret = sha256d(password.toByteArray(Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(secret, "AES"), IvParameterSpec(iv))
        val decrypted = try {
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw InvalidElectrumPasswordException("Keystore decode failed: ${e.message}")
        }
        return decrypted.toString(Charsets.UTF_8)
    }

    fun pwDecodeWithMac(dataBase64: String, password: String): String {
        if (password.isEmpty()) return dataBase64
        val data = Base64.getDecoder().decode(dataBase64)
        if (data.size < 21) throw InvalidElectrumPasswordException("Encrypted data too short for MAC format")
        val version = data[0].toInt()
        if (version != 1) throw InvalidElectrumPasswordException("Unsupported pw hash version: $version")
        val ciphertext = data.copyOfRange(1, data.size - 4)
        val mac = data.copyOfRange(data.size - 4, data.size)
        if (ciphertext.size < 16) throw InvalidElectrumPasswordException("Ciphertext too short")
        val iv = ciphertext.copyOfRange(0, 16)
        val aesData = ciphertext.copyOfRange(16, ciphertext.size)
        val secret = sha256d(password.toByteArray(Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(secret, "AES"), IvParameterSpec(iv))
        val decrypted = try {
            cipher.doFinal(aesData)
        } catch (e: Exception) {
            throw InvalidElectrumPasswordException("Keystore key decode failed: ${e.message}")
        }
        val sha = MessageDigest.getInstance("SHA-256")
        val expectedMac = sha.digest(decrypted).copyOfRange(0, 4)
        if (!MessageDigest.isEqual(expectedMac, mac)) {
            throw InvalidElectrumPasswordException("Key MAC mismatch")
        }
        return decrypted.toString(Charsets.UTF_8)
    }

    private fun sha256d(data: ByteArray): ByteArray {
        val sha = MessageDigest.getInstance("SHA-256")
        return sha.digest(sha.digest(data))
    }

    private fun pbkdf2HmacSha512EmptySalt(password: ByteArray, iterations: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(password, "HmacSHA512"))
        var u = mac.doFinal(byteArrayOf(0, 0, 0, 1))
        val result = u.clone()
        for (i in 1 until iterations) {
            u = mac.doFinal(u)
            for (j in u.indices) {
                result[j] = (result[j].toInt() xor u[j].toInt()).toByte()
            }
        }
        return result
    }
}

/**
 * Self-contained secp256k1 point decompression and scalar multiplication
 * using standard Jacobian projective coordinates.
 */
object Secp256k1 {
    val P = BigInteger("fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f", 16)
    val ORDER = BigInteger("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", 16)

    data class Point(val x: BigInteger, val y: BigInteger)
    private data class JacobianPoint(val x: BigInteger, val y: BigInteger, val z: BigInteger)

    fun decompress(pub: ByteArray): Point {
        require(pub.size == 33) { "Compressed public key must be 33 bytes" }
        val prefix = pub[0].toInt() and 0xff
        require(prefix == 2 || prefix == 3) { "Invalid compressed prefix: $prefix" }
        val x = BigInteger(1, pub.copyOfRange(1, 33))
        // y^2 = x^3 + 7 (mod p)
        val ySq = x.modPow(BigInteger.valueOf(3), P).add(BigInteger.valueOf(7)).mod(P)
        // y = (y^2)^((p+1)/4) (mod p) since p == 3 (mod 4)
        val exp = P.add(BigInteger.ONE).shiftRight(2)
        var y = ySq.modPow(exp, P)
        if (y.testBit(0) != (prefix == 3)) {
            y = P.subtract(y).mod(P)
        }
        return Point(x, y)
    }

    fun compress(p: Point): ByteArray {
        val out = ByteArray(33)
        out[0] = if (p.y.testBit(0)) 3 else 2
        val xBytes = p.x.toByteArray()
        val xRaw = if (xBytes.size > 32) xBytes.copyOfRange(xBytes.size - 32, xBytes.size) else xBytes
        System.arraycopy(xRaw, 0, out, 33 - xRaw.size, xRaw.size)
        return out
    }

    fun multiply(k: BigInteger, p: Point): Point {
        var r = JacobianPoint(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO)
        val bits = k.toString(2)
        for (bit in bits) {
            r = jacobianDouble(r)
            if (bit == '1') {
                r = jacobianAddAffine(r, p)
            }
        }
        val zInv = r.z.modInverse(P)
        val zInvSq = zInv.multiply(zInv).mod(P)
        val zInvCb = zInvSq.multiply(zInv).mod(P)
        val rx = r.x.multiply(zInvSq).mod(P)
        val ry = r.y.multiply(zInvCb).mod(P)
        return Point(rx, ry)
    }

    private fun jacobianDouble(p: JacobianPoint): JacobianPoint {
        if (p.y.signum() == 0 || p.z.signum() == 0) return p
        val ySq = p.y.multiply(p.y).mod(P)
        val s = BigInteger.valueOf(4).multiply(p.x).multiply(ySq).mod(P)
        val m = BigInteger.valueOf(3).multiply(p.x.multiply(p.x)).mod(P)
        val xPrime = m.multiply(m).subtract(BigInteger.valueOf(2).multiply(s)).mod(P)
        val yPrime = m.multiply(s.subtract(xPrime)).subtract(BigInteger.valueOf(8).multiply(ySq.multiply(ySq))).mod(P)
        val zPrime = BigInteger.valueOf(2).multiply(p.y).multiply(p.z).mod(P)
        return JacobianPoint(xPrime, yPrime, zPrime)
    }

    private fun jacobianAddAffine(p1: JacobianPoint, p2: Point): JacobianPoint {
        if (p1.z.signum() == 0) return JacobianPoint(p2.x, p2.y, BigInteger.ONE)
        val z1Sq = p1.z.multiply(p1.z).mod(P)
        val z1Cb = p1.z.multiply(z1Sq).mod(P)
        val u2 = p2.x.multiply(z1Sq).mod(P)
        val s2 = p2.y.multiply(z1Cb).mod(P)
        val h = u2.subtract(p1.x).mod(P)
        val r = s2.subtract(p1.y).mod(P)
        if (h.signum() == 0) {
            return if (r.signum() == 0) jacobianDouble(p1) else JacobianPoint(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO)
        }
        val hSq = h.multiply(h).mod(P)
        val hCb = h.multiply(hSq).mod(P)
        val x3 = r.multiply(r).subtract(hCb).subtract(BigInteger.valueOf(2).multiply(p1.x).multiply(hSq)).mod(P)
        val y3 = r.multiply(p1.x.multiply(hSq).subtract(x3)).subtract(p1.y.multiply(hCb)).mod(P)
        val z3 = p1.z.multiply(h).mod(P)
        return JacobianPoint(x3, y3, z3)
    }
}
