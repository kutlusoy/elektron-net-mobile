package org.elektronnet.mobile.explorer

import org.elektronnet.mobile.node.Json

object ExplorerList {
    const val DEFAULT_URL = "https://mempool.elektron-net.org"
    const val MAX_ENTRIES = 5

    sealed interface Check {
        data class Ok(val url: String) : Check
        data object Invalid : Check
        data object Insecure : Check
        data object Duplicate : Check
        data object Full : Check
    }

    fun normalize(input: String): String? {
        val text = input.trim().trimEnd('/')
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val lower = text.lowercase()
        val rest = when {
            lower.startsWith("https://") -> text.substring(8)
            lower.startsWith("http://") -> text.substring(7)
            else -> return null
        }
        val host = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        if (host.isEmpty() || host.startsWith(".") || host.startsWith("@") || '@' in host) return null
        if (rest.contains('?') || rest.contains('#')) return null
        val scheme = text.substring(0, text.length - rest.length).lowercase()
        val path = rest.substring(host.length)
        return scheme + host.lowercase() + path
    }

    fun check(input: String, existing: List<String>, replacing: String? = null): Check {
        val url = normalize(input) ?: return Check.Invalid
        if (url.startsWith("http://") && !isLocalHost(url)) return Check.Insecure
        if (existing.any { it == url && it != replacing }) return Check.Duplicate
        if (replacing == null && existing.size >= MAX_ENTRIES) return Check.Full
        return Check.Ok(url)
    }

    private fun isLocalHost(url: String): Boolean {
        val host = url.substringAfter("://").substringBefore('/').substringBefore(':')
        return host == "localhost" || host == "127.0.0.1" || host.endsWith(".onion")
    }

    fun parse(text: String?): List<String> {
        if (text == null) return listOf(DEFAULT_URL)
        return runCatching { (Json.parse(text) as List<*>).mapNotNull { normalize(it.toString()) } }.getOrDefault(listOf(DEFAULT_URL)).distinct()
    }

    fun serialize(urls: List<String>): String = Json.stringify(urls)

    fun txUrl(base: String, txid: String): String = "$base/tx/$txid"
}
