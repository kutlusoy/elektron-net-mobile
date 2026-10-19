package org.elektronnet.mobile.wallet

import java.io.File
import org.elektronnet.mobile.node.RpcException

enum class ExportFailure { NotEncrypted, TargetExists, NodeNotReady, Unknown }

class ExportException(val failure: ExportFailure, detail: String = "") : Exception(detail)

data class DescriptorLine(val descriptor: String, val active: Boolean, val internal: Boolean)

class WalletExport(private val rpc: WalletRpc, private val walletName: String = WalletBootstrap.DEFAULT_WALLET) {
    fun publicDescriptors(): List<DescriptorLine> {
        val result = guard { rpc.call("listdescriptors", listOf(false), walletName) as Map<*, *> }
        val rows = (result["descriptors"] as? List<*>)?.map { it as Map<*, *> } ?: return emptyList()
        return rows.map { DescriptorLine(it["desc"].toString(), it["active"] == true, it["internal"] == true) }
            .onEach { check(!it.descriptor.contains("prv")) { "private key material in public export" } }
    }

    fun descriptorText(): String = buildString {
        append("# Elektron Net public descriptors for wallet ").append(walletName).append('\n')
        append("# No private keys. Usable for watch-only import only.\n")
        publicDescriptors().forEach {
            append(if (it.internal) "change   " else "receive  ").append(if (it.active) "active    " else "inactive  ").append(it.descriptor).append('\n')
        }
    }

    fun backup(target: File): File {
        if (target.exists()) throw ExportException(ExportFailure.TargetExists)
        val info = guard { rpc.call("getwalletinfo", emptyList<Any?>(), walletName) as Map<*, *> }
        if (!info.containsKey("unlocked_until")) throw ExportException(ExportFailure.NotEncrypted)
        target.parentFile?.mkdirs()
        guard { rpc.call("backupwallet", listOf(target.path), walletName) }
        if (!target.isFile || target.length() == 0L) throw ExportException(ExportFailure.Unknown, "no file written")
        return target
    }

    private fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: RpcException) {
        throw ExportException(if (e.inWarmup) ExportFailure.NodeNotReady else ExportFailure.Unknown, e.message ?: "")
    }

    companion object {
        fun backupName(walletName: String, nowMs: Long): String = "${walletName.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "wallet" }}-$nowMs.dat"
    }
}
