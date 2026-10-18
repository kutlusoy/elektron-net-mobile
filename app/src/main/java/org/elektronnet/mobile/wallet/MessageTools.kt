package org.elektronnet.mobile.wallet

import java.util.Base64
import org.elektronnet.mobile.node.RpcException

enum class MessageFailure { InvalidAddress, NotOurAddress, WrongPassphrase, NodeNotReady, Unsupported, Unknown }

class MessageException(val failure: MessageFailure, detail: String = "") : Exception(detail)

enum class VerifyResult { Valid, Invalid, BadFormat, UnsupportedAddress }

class MessageTools(
    private val rpc: WalletRpc,
    private val addresses: AddressCheck,
    private val walletName: String = WalletBootstrap.DEFAULT_WALLET,
) {
    fun sign(address: String, message: String, passphrase: String): String {
        val kind = addresses.classify(address)
        val trimmed = addresses.normalized(address)
        val signer: String
        val segwit: Boolean
        when (kind) {
            AddressKind.Native -> {
                val decoded = Bech32.decode(trimmed)
                if (decoded == null || decoded.version != 0 || decoded.program.size != 20) throw MessageException(MessageFailure.Unsupported)
                val info = guard { rpc.call("getaddressinfo", listOf(trimmed), walletName) as Map<*, *> }
                if (info["ismine"] != true) throw MessageException(MessageFailure.NotOurAddress)
                val pubkey = info["pubkey"]?.toString() ?: throw MessageException(MessageFailure.Unsupported)
                signer = legacyAddress(pubkey)
                val legacyInfo = guard { rpc.call("getaddressinfo", listOf(signer), walletName) as Map<*, *> }
                if (legacyInfo["ismine"] != true) throw MessageException(MessageFailure.Unsupported)
                segwit = true
            }
            AddressKind.LegacyBase58 -> {
                signer = trimmed
                segwit = false
            }
            else -> throw MessageException(MessageFailure.InvalidAddress)
        }
        var unlocked = false
        try {
            try {
                rpc.call("walletpassphrase", listOf(passphrase, UNLOCK_SECONDS), walletName)
                unlocked = true
            } catch (e: RpcException) {
                throw MessageException(if (e.code == WRONG_PASSPHRASE) MessageFailure.WrongPassphrase else MessageFailure.Unknown, e.message ?: "")
            }
            val signature = try {
                rpc.call("signmessage", listOf(signer, message), walletName).toString()
            } catch (e: RpcException) {
                throw MessageException(MessageFailure.NotOurAddress, e.message ?: "")
            }
            return if (segwit) shiftHeader(signature, SEGWIT_SHIFT) else signature
        } finally {
            if (unlocked) runCatching { rpc.call("walletlock", emptyList<Any?>(), walletName) }
        }
    }

    fun verify(address: String, signature: String, message: String): VerifyResult {
        val raw = runCatching { Base64.getDecoder().decode(signature.trim()) }.getOrNull()
        if (raw == null || raw.size != 65) return VerifyResult.BadFormat
        val header = raw[0].toInt() and 0xff
        val trimmed = addresses.normalized(address)
        return when (addresses.classify(address)) {
            AddressKind.LegacyBase58 -> if (header in 27..34) check(trimmed, signature.trim(), message) else VerifyResult.BadFormat
            AddressKind.Native -> {
                val decoded = Bech32.decode(trimmed)
                if (decoded == null || decoded.version != 0 || decoded.program.size != 20) return VerifyResult.UnsupportedAddress
                if (header !in 39..42) return VerifyResult.BadFormat
                val legacy = Base58.encodeChecked(byteArrayOf(addresses.pubKeyHashVersion.toByte()) + decoded.program)
                check(legacy, shiftHeader(signature.trim(), -SEGWIT_SHIFT), message)
            }
            else -> VerifyResult.UnsupportedAddress
        }
    }

    private fun check(address: String, signature: String, message: String): VerifyResult = try {
        if (rpc.call("verifymessage", listOf(address, signature, message), null) == true) VerifyResult.Valid else VerifyResult.Invalid
    } catch (e: RpcException) {
        VerifyResult.Invalid
    }

    private fun legacyAddress(pubkeyHex: String): String {
        val info = guard { rpc.call("getdescriptorinfo", listOf("pkh($pubkeyHex)"), null) as Map<*, *> }
        return guard { (rpc.call("deriveaddresses", listOf(info["descriptor"].toString()), null) as List<*>).first().toString() }
    }

    private fun shiftHeader(signature: String, delta: Int): String {
        val raw = Base64.getDecoder().decode(signature)
        raw[0] = (raw[0] + delta).toByte()
        return Base64.getEncoder().encodeToString(raw)
    }

    private fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: RpcException) {
        throw MessageException(if (e.inWarmup) MessageFailure.NodeNotReady else MessageFailure.Unknown, e.message ?: "")
    }

    private companion object {
        const val UNLOCK_SECONDS = 10
        const val WRONG_PASSPHRASE = -14
        const val SEGWIT_SHIFT = 8
    }
}
