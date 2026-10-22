package org.elektronnet.mobile.fiat

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object PriceFetch {
    private const val MAX_BYTES = 64 * 1024

    // null: the service answered but does not know the market; IOException: unreachable or server error
    fun get(url: String, timeoutMs: Int = 10_000): String? {
        require(url.startsWith("https://")) { "https only" }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "ElektronMobile")
            val code = connection.responseCode
            if (code in 400..499) return null
            if (code != 200) throw IOException("HTTP $code")
            return connection.inputStream.use { stream ->
                val out = ByteArrayOutputStream()
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
