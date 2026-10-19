package org.elektronnet.mobile.node

object LogSanitizer {
    private val ipv4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?\b""")
    private val ipv6 = Regex("""\[?[0-9a-fA-F]*:[0-9a-fA-F:]{2,}\]?(?::\d{1,5})?""")
    private val overlay = Regex("""\b[a-z2-7]{16,56}\.(?:onion|b32\.i2p)(?::\d{1,5})?\b""")
    private val bech32 = Regex("""\b(?:be|tb|bcrt)1[02-9ac-hj-np-z]{11,87}\b""", RegexOption.IGNORE_CASE)
    private val base58 = Regex("""\b[13mn2][1-9A-HJ-NP-Za-km-z]{25,34}\b""")
    private val path = Regex("""/(?:data|storage|sdcard|mnt)/[^\s'"]*""")

    fun clean(line: String): String {
        var out = line
        out = path.replace(out, "<path>")
        out = overlay.replace(out, "<onion>")
        out = ipv6.replace(out) { if (it.value.contains("::") || it.value.count { c -> c == ':' } >= 5) "<ip>" else it.value }
        out = ipv4.replace(out, "<ip>")
        out = bech32.replace(out, "<address>")
        out = base58.replace(out, "<address>")
        return out
    }

    fun clean(lines: List<String>): String = lines.joinToString("\n") { clean(it) }
}
