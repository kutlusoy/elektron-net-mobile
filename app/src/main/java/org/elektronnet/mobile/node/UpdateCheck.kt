package org.elektronnet.mobile.node

data class AppVersion(val node: List<Int>, val wallet: List<Int>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        val w = compareLists(wallet, other.wallet)
        return if (w != 0) w else compareLists(node, other.node)
    }

    private fun compareLists(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val c = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    companion object {
        private val TAG = Regex("""^v?(\d+(?:\.\d+){0,3})(?:-m(\d+(?:\.\d+){0,3}))?$""")

        fun parse(tag: String): AppVersion? {
            val m = TAG.matchEntire(tag.trim()) ?: return null
            fun nums(text: String?) = text?.split('.')?.map { it.toInt() } ?: emptyList()
            return AppVersion(nums(m.groupValues[1]), nums(m.groups[2]?.value))
        }
    }
}

sealed interface UpdateResult {
    data class Newer(val tag: String) : UpdateResult
    data object UpToDate : UpdateResult
    data object Unknown : UpdateResult
}

object UpdateCheck {
    const val TAGS_URL = "https://api.github.com/repos/kutlusoy/elektron-net-mobile/tags?per_page=50"
    const val RELEASE_URL = "https://github.com/kutlusoy/elektron-net-mobile/releases/tag/"

    fun current(versionName: String, mobileVersion: String): AppVersion? =
        AppVersion.parse(versionName) ?: AppVersion.parse(mobileVersion)?.let { AppVersion(listOf(0), it.node) }

    fun evaluate(versionName: String, mobileVersion: String, tagsJson: String): UpdateResult {
        val current = current(versionName, mobileVersion) ?: return UpdateResult.Unknown
        val tags = (runCatching { Json.parse(tagsJson) as? List<*> }.getOrNull() ?: return UpdateResult.Unknown)
            .mapNotNull { (it as? Map<*, *>)?.get("name") as? String }
        val best = tags.mapNotNull { tag -> AppVersion.parse(tag)?.let { tag to it } }.maxByOrNull { it.second } ?: return UpdateResult.Unknown
        return if (best.second > current) UpdateResult.Newer(best.first) else UpdateResult.UpToDate
    }
}

object UpdateFetch {
    private const val MAX_BYTES = 64 * 1024

    fun tags(timeoutMs: Int = 10_000): String {
        val connection = java.net.URL(UpdateCheck.TAGS_URL).openConnection() as java.net.HttpURLConnection
        try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "ElektronMobile")
            if (connection.responseCode != 200) throw java.io.IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var left = MAX_BYTES
                while (left > 0) {
                    val n = stream.read(chunk, 0, minOf(chunk.size, left))
                    if (n < 0) break
                    out.write(chunk, 0, n)
                    left -= n
                }
                out.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }
}
