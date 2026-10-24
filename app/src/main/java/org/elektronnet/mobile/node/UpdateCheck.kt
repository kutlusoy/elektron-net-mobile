package org.elektronnet.mobile.node

data class AppVersion(val node: List<Int>, val wallet: List<Int>, val candidate: Int? = null) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        val w = compareLists(wallet, other.wallet)
        if (w != 0) return w
        val n = compareLists(node, other.node)
        if (n != 0) return n
        // a release candidate is older than the release with the same numbers
        return (candidate ?: Int.MAX_VALUE).compareTo(other.candidate ?: Int.MAX_VALUE)
    }

    private fun compareLists(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val c = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    companion object {
        private val TAG = Regex("""^v?(\d+(?:\.\d+){0,3})(?:-m(\d+(?:\.\d+){0,3}))?(?:-rc(\d+))?$""")

        fun parse(tag: String): AppVersion? {
            val m = TAG.matchEntire(tag.trim()) ?: return null
            fun nums(text: String?) = text?.split('.')?.map { it.toInt() } ?: emptyList()
            return AppVersion(nums(m.groupValues[1]), nums(m.groups[2]?.value), m.groups[3]?.value?.toInt())
        }
    }
}

// Test builds are signed with the public test key, release candidates and releases with the release key
enum class ReleaseKind { Test, Candidate, Final }

sealed interface UpdateResult {
    data class Newer(val tag: String, val kind: ReleaseKind) : UpdateResult
    data object UpToDate : UpdateResult
    data object Unknown : UpdateResult
}

object UpdateCheck {
    const val RELEASES_URL = "https://api.github.com/repos/kutlusoy/elektron-net-mobile/releases?per_page=30"
    const val RELEASE_URL = "https://github.com/kutlusoy/elektron-net-mobile/releases/tag/"
    const val INTERVAL_MS = 24L * 60 * 60 * 1000
    const val RETRY_MS = 60L * 60 * 1000

    // the prerelease workflow titles its releases "(test build, ...)"
    private const val TEST_TITLE = "test build"

    fun current(versionName: String, mobileVersion: String): AppVersion? =
        AppVersion.parse(versionName) ?: AppVersion.parse(mobileVersion)?.let { AppVersion(listOf(0), it.node) }

    fun kindOf(tag: String, prerelease: Boolean, title: String): ReleaseKind? {
        val version = AppVersion.parse(tag) ?: return null
        return when {
            prerelease && title.contains(TEST_TITLE, ignoreCase = true) -> ReleaseKind.Test
            prerelease && version.candidate != null -> ReleaseKind.Candidate
            !prerelease && version.candidate == null -> ReleaseKind.Final
            else -> null
        }
    }

    // a test build only follows test builds, the release line follows release candidates and releases
    fun offered(testBuild: Boolean): Set<ReleaseKind> =
        if (testBuild) setOf(ReleaseKind.Test) else setOf(ReleaseKind.Candidate, ReleaseKind.Final)

    // only the newest matching release is offered, not a list
    fun evaluate(versionName: String, mobileVersion: String, releasesJson: String, testBuild: Boolean): UpdateResult {
        val current = current(versionName, mobileVersion) ?: return UpdateResult.Unknown
        val releases = (runCatching { Json.parse(releasesJson) as? List<*> }.getOrNull() ?: return UpdateResult.Unknown).mapNotNull { it as? Map<*, *> }
        val wanted = offered(testBuild)
        val best = releases
            .filter { it["draft"] != true }
            .mapNotNull { release ->
                val tag = release["tag_name"] as? String ?: return@mapNotNull null
                val kind = kindOf(tag, release["prerelease"] == true, release["name"] as? String ?: "") ?: return@mapNotNull null
                if (kind !in wanted) return@mapNotNull null
                Triple(tag, kind, AppVersion.parse(tag) ?: return@mapNotNull null)
            }
            .maxByOrNull { it.third } ?: return UpdateResult.Unknown
        return if (best.third > current) UpdateResult.Newer(best.first, best.second) else UpdateResult.UpToDate
    }

    // one check a day, after a failure the next try comes after an hour
    fun due(lastOkMs: Long, lastAttemptMs: Long, nowMs: Long): Boolean =
        nowMs - lastOkMs >= INTERVAL_MS && nowMs - lastAttemptMs >= RETRY_MS
}

object UpdateFetch {
    private const val MAX_BYTES = 1024 * 1024

    fun releases(timeoutMs: Int = 10_000): String {
        val connection = java.net.URL(UpdateCheck.RELEASES_URL).openConnection() as java.net.HttpURLConnection
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
