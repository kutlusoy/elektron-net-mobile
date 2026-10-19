package org.elektronnet.mobile.console

object ArgNames {
    private val names = mapOf(
        "listdescriptors" to listOf("private"),
        "gethdkeys" to listOf("options"),
        "dumptxoutset" to listOf("path", "type", "options"),
        "walletprocesspsbt" to listOf("psbt", "sign", "sighashtype", "bip32derivs", "finalize"),
        "backupwallet" to listOf("destination"),
        "restorewallet" to listOf("wallet_name", "backup_file", "load_on_startup"),
        "createwallet" to listOf("wallet_name", "disable_private_keys", "blank", "passphrase", "avoid_reuse", "descriptors", "load_on_startup", "external_signer"),
        "walletpassphrase" to listOf("passphrase", "timeout"),
        "walletpassphrasechange" to listOf("oldpassphrase", "newpassphrase"),
        "encryptwallet" to listOf("passphrase"),
        "signmessagewithprivkey" to listOf("privkey", "message"),
        "signrawtransactionwithkey" to listOf("hexstring", "privkeys", "prevtxs", "sighashtype"),
    )

    fun of(method: String): List<String> = names[method].orEmpty()

    fun indexOf(method: String, name: String): Int = of(method).indexOf(name)

    fun valueOf(command: ParsedCommand, name: String): Any? {
        if (command.named.containsKey(name)) return command.named[name]
        val index = indexOf(command.method, name)
        return if (index >= 0 && index < command.positional.size) command.positional[index] else null
    }

    fun isSet(command: ParsedCommand, name: String): Boolean =
        command.named.containsKey(name) || indexOf(command.method, name).let { it >= 0 && it < command.positional.size }
}
