package org.elektronnet.mobile.console

import org.elektronnet.mobile.node.Json

enum class Tier { Open, Confirm, Danger, Blocked;

    companion object {
        fun parse(name: String): Tier = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Confirm
    }
}

data class TierRule(val arg: String, val equals: Any?, val tier: Tier)

data class CommandRule(
    val tier: Tier,
    val heavy: Boolean = false,
    val rules: List<TierRule> = emptyList(),
    val pathArgs: List<String> = emptyList(),
    val requireEncryptedWallet: Boolean = false,
    val requireEncryptedAfter: Boolean = false,
    val requireNonEmpty: List<String> = emptyList(),
    val autoDeleteDays: Int? = null,
)

class Tiers(private val commands: Map<String, CommandRule>, private val defaultTier: Tier) {
    val names: List<String> get() = commands.keys.sorted()

    fun rule(method: String): CommandRule? = commands[method]

    fun classify(command: ParsedCommand): Tier {
        val rule = commands[command.method] ?: return defaultTier
        var tier = rule.tier
        for (r in rule.rules) {
            if (matches(command, r)) tier = r.tier
        }
        return tier
    }

    fun isHeavy(method: String): Boolean = commands[method]?.heavy == true

    private fun matches(command: ParsedCommand, rule: TierRule): Boolean {
        val path = rule.arg.split('.')
        var value: Any? = ArgNames.valueOf(command, path.first()) ?: if (ArgNames.isSet(command, path.first())) null else return false
        for (part in path.drop(1)) {
            value = (value as? Map<*, *>)?.get(part)
        }
        return value?.toString()?.equals(rule.equals.toString(), ignoreCase = true) == true
    }

    companion object {
        fun parse(json: String): Tiers {
            val root = Json.parse(json) as Map<*, *>
            val all = LinkedHashMap<String, CommandRule>()
            for (group in listOf("commands", "hidden_commands")) {
                (root[group] as? Map<*, *>).orEmpty().forEach { (name, raw) -> all[name.toString()] = rule(raw as Map<*, *>) }
            }
            return Tiers(all, Tier.parse(root["default_tier"]?.toString() ?: "confirm"))
        }

        private fun rule(raw: Map<*, *>): CommandRule = CommandRule(
            tier = Tier.parse(raw["tier"].toString()),
            heavy = raw["heavy"] == true,
            rules = (raw["rules"] as? List<*>).orEmpty().map {
                val r = it as Map<*, *>
                val `when` = r["when"] as Map<*, *>
                TierRule(`when`["arg"].toString(), `when`["equals"], Tier.parse(r["tier"].toString()))
            },
            pathArgs = (raw["path_args"] as? List<*>).orEmpty().map { it.toString() },
            requireEncryptedWallet = raw["require_encrypted_wallet"] == true,
            requireEncryptedAfter = raw["require_encrypted_wallet_after"] == true,
            requireNonEmpty = (raw["require_nonempty_args"] as? List<*>).orEmpty().map { it.toString() },
            autoDeleteDays = (raw["auto_delete_days"] as? Number)?.toInt(),
        )
    }
}
