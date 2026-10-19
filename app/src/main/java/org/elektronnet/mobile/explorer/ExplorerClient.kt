package org.elektronnet.mobile.explorer

import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import org.elektronnet.mobile.node.Json

data class RecommendedFees(
    val fastest: BigDecimal,
    val halfHour: BigDecimal,
    val hour: BigDecimal,
    val economy: BigDecimal,
    val minimum: BigDecimal,
)

class ExplorerClient(private val timeoutMs: Int = 5_000) {
    fun fees(urls: List<String>): RecommendedFees? {
        for (base in urls) {
            for (path in PATHS) {
                val parsed = runCatching { parse(get(base + path)) }.getOrNull()
                if (parsed != null) return parsed
            }
        }
        return null
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "ElektronMobile")
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.use { it.readNBytes(MAX_BYTES).toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private val PATHS = listOf("/api/v1/fees/precise", "/api/v1/fees/recommended")
        private const val MAX_BYTES = 16 * 1024
        private val CEILING = BigDecimal(10_000)

        fun parse(text: String): RecommendedFees? {
            val map = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *> ?: return null
            fun rate(key: String): BigDecimal? {
                val value = when (val raw = map[key]) {
                    is Long -> BigDecimal(raw)
                    is Double -> BigDecimal(raw.toString())
                    is BigDecimal -> raw
                    else -> null
                } ?: return null
                return value.takeIf { it > BigDecimal.ZERO && it <= CEILING }
            }
            val fastest = rate("fastestFee") ?: return null
            val halfHour = rate("halfHourFee") ?: fastest
            val hour = rate("hourFee") ?: halfHour
            val economy = rate("economyFee") ?: hour
            val minimum = rate("minimumFee") ?: economy
            return RecommendedFees(fastest, halfHour, hour, economy, minimum)
        }
    }
}
