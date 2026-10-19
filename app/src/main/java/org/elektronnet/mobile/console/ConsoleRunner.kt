package org.elektronnet.mobile.console

import java.io.File
import org.elektronnet.mobile.node.RpcException
import org.elektronnet.mobile.wallet.WalletRpc

enum class Refusal {
    Blocked, NoPassphrase, WatchOnly, WalletNotEncrypted, NeedsWallet, RollbackNotAllowed, DumpTypeMissing, LowStorage,
    PathEmpty, PathTraversal, PathOutside, PathSymlink, PathExists, PathMissing, PathBad, NodeNotReady,
}

sealed interface Plan {
    data class Ready(
        val method: String,
        val params: Any,
        val tier: Tier,
        val heavy: Boolean,
        val display: String,
        val wallet: String?,
        val sensitive: Boolean,
        val restoreWallet: String?,
        val autoDeleteFile: File?,
    ) : Plan

    data class Refused(val reason: Refusal) : Plan
    data class Invalid(val message: String) : Plan
}

data class ExecResult(
    val text: String,
    val truncated: Boolean,
    val sensitive: Boolean,
    val error: Boolean,
    val needsPassphraseFor: String? = null,
)

class ConsoleRunner(
    private val rpc: WalletRpc,
    private val tiers: Tiers,
    private val files: FileGuard,
    private val freeBytes: () -> Long = { Long.MAX_VALUE },
    private val storageFloorBytes: Long = 0,
) {
    fun prepare(line: String, wallet: String?): Plan {
        val command = when (val parsed = CommandParser.parse(line)) {
            is ParseResult.Ok -> parsed.command
            is ParseResult.Error -> return Plan.Invalid(parsed.message)
        }
        val tier = tiers.classify(command)
        if (tier == Tier.Blocked) {
            val dump = command.method == "dumptxoutset" && ArgNames.valueOf(command, "type")?.toString().equals("rollback", ignoreCase = true)
            return Plan.Refused(if (dump) Refusal.RollbackNotAllowed else Refusal.Blocked)
        }
        val rule = tiers.rule(command.method)
        val params = ArrayList(command.positional)
        val named = LinkedHashMap(command.named)
        fun set(name: String, value: Any?) {
            val index = ArgNames.indexOf(command.method, name)
            if (command.named.containsKey(name)) named[name] = value else if (index in params.indices) params[index] = value
        }
        var autoDelete: File? = null

        if (rule != null) {
            for (name in rule.requireNonEmpty) {
                val value = ArgNames.valueOf(command, name)
                if (value == null || value.toString().isEmpty()) return Plan.Refused(Refusal.NoPassphrase)
            }
        }
        if (command.method == "createwallet" && ArgNames.valueOf(command, "disable_private_keys")?.toString() == "true") {
            return Plan.Refused(Refusal.WatchOnly)
        }
        if (command.method == "dumptxoutset") {
            val type = ArgNames.valueOf(command, "type")?.toString()?.lowercase() ?: return Plan.Refused(Refusal.DumpTypeMissing)
            if (type != "latest") return Plan.Refused(if (type == "rollback") Refusal.RollbackNotAllowed else Refusal.DumpTypeMissing)
            if (freeBytes() < storageFloorBytes + DUMP_MARGIN) return Plan.Refused(Refusal.LowStorage)
        }
        if (rule != null && rule.requireEncryptedWallet) {
            if (wallet == null) return Plan.Refused(Refusal.NeedsWallet)
            val info = try {
                rpc.call("getwalletinfo", emptyList<Any?>(), wallet) as? Map<*, *>
            } catch (e: RpcException) {
                return Plan.Refused(if (e.inWarmup) Refusal.NodeNotReady else Refusal.NeedsWallet)
            }
            if (info == null || !info.containsKey("unlocked_until")) return Plan.Refused(Refusal.WalletNotEncrypted)
        }
        if (rule != null) {
            for (arg in rule.pathArgs) {
                val value = ArgNames.valueOf(command, arg)?.toString() ?: return Plan.Invalid("The argument $arg is needed.")
                val isDump = rule.autoDeleteDays != null
                val resolved = files.resolve(
                    value,
                    subdir = if (isDump) "dumps" else null,
                    mustBeNew = command.method == "backupwallet" || command.method == "dumptxoutset",
                    mustExist = command.method == "restorewallet",
                )
                when (resolved) {
                    is Resolved.Ok -> {
                        set(arg, resolved.file.path)
                        if (isDump) autoDelete = resolved.file
                    }
                    is Resolved.Refused -> return Plan.Refused(refusal(resolved.reason))
                }
            }
        }
        val finalParams: Any = if (named.isNotEmpty()) {
            LinkedHashMap<String, Any?>().apply {
                val names = ArgNames.of(command.method)
                params.forEachIndexed { i, v -> put(names.getOrNull(i) ?: return@forEachIndexed, v) }
                putAll(named)
            }.also { if (params.isNotEmpty() && ArgNames.of(command.method).size < params.size) return Plan.Invalid("Use name=value for all arguments of this command.") }
        } else {
            params
        }
        return Plan.Ready(
            method = command.method,
            params = finalParams,
            tier = tier,
            heavy = tiers.isHeavy(command.method),
            display = Masking.mask(command),
            wallet = wallet,
            sensitive = isSensitive(command),
            restoreWallet = if (rule?.requireEncryptedAfter == true) ArgNames.valueOf(command, "wallet_name")?.toString() else null,
            autoDeleteFile = autoDelete,
        )
    }

    fun execute(plan: Plan.Ready, timeoutMs: Int = 60_000): ExecResult {
        val result = try {
            rpc.call(plan.method, plan.params, plan.wallet)
        } catch (e: RpcException) {
            return ExecResult("error ${e.code}: ${e.message}", truncated = false, sensitive = false, error = true)
        } catch (e: java.io.IOException) {
            return ExecResult("The node did not answer: ${e.message}", truncated = false, sensitive = false, error = true)
        }
        val formatted = Pretty.format(result)
        var needs: String? = null
        plan.restoreWallet?.let { name ->
            val info = runCatching { rpc.call("getwalletinfo", emptyList<Any?>(), name) as? Map<*, *> }.getOrNull()
            if (info != null && !info.containsKey("unlocked_until")) needs = name
        }
        return ExecResult(formatted.text, formatted.truncated, plan.sensitive, error = false, needsPassphraseFor = needs)
    }

    private fun isSensitive(command: ParsedCommand): Boolean = when (command.method) {
        "listdescriptors" -> ArgNames.valueOf(command, "private")?.toString() == "true"
        "gethdkeys" -> ((ArgNames.valueOf(command, "options") as? Map<*, *>)?.get("private")?.toString() == "true")
        "signmessage", "signmessagewithprivkey", "signrawtransactionwithkey", "signrawtransactionwithwallet", "descriptorprocesspsbt" -> true
        "walletprocesspsbt" -> ArgNames.valueOf(command, "sign")?.toString() != "false"
        else -> false
    }

    private fun refusal(reason: FileRefusal): Refusal = when (reason) {
        FileRefusal.Empty -> Refusal.PathEmpty
        FileRefusal.Traversal -> Refusal.PathTraversal
        FileRefusal.Outside -> Refusal.PathOutside
        FileRefusal.Symlink -> Refusal.PathSymlink
        FileRefusal.Exists -> Refusal.PathExists
        FileRefusal.Missing -> Refusal.PathMissing
        FileRefusal.BadName -> Refusal.PathBad
    }

    private companion object {
        const val DUMP_MARGIN = 200L * 1024 * 1024
    }
}
