package org.elektronnet.mobile.node

object PeerAddress {
    private val ONION = Regex("^[a-z2-7]{56}\\.onion(?::([0-9]{1,5}))?$")
    private val I2P = Regex("^[a-z2-7]{52}\\.b32\\.i2p(?::[0-9]{1,5})?$")

    // returns the form handed to -addnode, or null when the text is not an onion v3 or b32 I2P address (I2P always uses port 0)
    fun parse(text: String): String? {
        val t = text.trim().lowercase()
        ONION.matchEntire(t)?.let { m ->
            val port = m.groupValues[1]
            return if (port.isEmpty() || port.toInt() in 1..65535) t else null
        }
        return if (I2P.matches(t)) t.substringBefore(':') + ":0" else null
    }

    fun isOnion(arg: String): Boolean = arg.contains(".onion")

    fun isI2p(arg: String): Boolean = arg.contains(".b32.i2p")

    fun shown(arg: String): String = arg.removeSuffix(":0")
}
