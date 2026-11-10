package org.elektronnet.mobile.wallet

object WifKey {
    fun clean(text: String): String {
        val trimmed = text.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        val colon = trimmed.indexOf(':')
        val stripped = if (colon != -1 && colon < 15) trimmed.substring(colon + 1).trim() else trimmed
        return stripped.removeSurrounding("\"").removeSurrounding("'").trim()
    }

    fun isValid(text: String, testnet: Boolean): Boolean {
        val cleaned = clean(text)
        val bytes = Base58.decodeChecked(cleaned) ?: return false
        val expectedPrefix = if (testnet) 0xef.toByte() else 0x80.toByte()
        if (bytes.isEmpty() || bytes[0] != expectedPrefix) return false
        if (bytes.size == 33) return true
        if (bytes.size == 34 && bytes[33] == 0x01.toByte()) return true
        return false
    }

    fun isAnyWif(text: String): Boolean {
        val cleaned = clean(text)
        val bytes = Base58.decodeChecked(cleaned) ?: return false
        if (bytes.isEmpty()) return false
        val prefix = bytes[0]
        if (prefix != 0x80.toByte() && prefix != 0xef.toByte()) return false
        if (bytes.size == 33) return true
        if (bytes.size == 34 && bytes[33] == 0x01.toByte()) return true
        return false
    }
}
