package org.elektronnet.mobile.console

object Masking {
    private val secretNames = setOf("passphrase", "oldpassphrase", "newpassphrase", "privkey", "privkeys")
    private val secretPositions = mapOf(
        "walletpassphrase" to setOf(0),
        "walletpassphrasechange" to setOf(0, 1),
        "encryptwallet" to setOf(0),
        "createwallet" to setOf(3),
        "signmessagewithprivkey" to setOf(0),
        "signrawtransactionwithkey" to setOf(1),
    )
    const val MASK = "***"

    fun mask(command: ParsedCommand): String {
        val secretIndexes = secretPositions[command.method].orEmpty()
        val out = ArrayList<String>()
        out.add(command.method)
        var positionalIndex = 0
        for (token in command.tokens.drop(1)) {
            val eq = token.indexOf('=')
            val name = if (eq > 0) token.substring(0, eq) else null
            if (name != null && Regex("[a-z][a-z0-9_]{0,30}").matches(name) && eq < token.length - 1) {
                out.add(if (name in secretNames) "$name=$MASK" else token)
            } else {
                out.add(if (positionalIndex in secretIndexes) MASK else token)
                positionalIndex++
            }
        }
        return out.joinToString(" ")
    }
}
