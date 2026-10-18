package org.elektronnet.mobile.node

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong

class RpcException(val code: Int, message: String) : Exception(message) {
    val inWarmup: Boolean get() = code == WARMUP

    companion object {
        const val WARMUP = -28
    }
}

class RpcClient(
    private val port: Int,
    private val cookieFile: File,
    private val host: String = "127.0.0.1",
    private val connectTimeoutMs: Int = 2_000,
) {
    private val ids = AtomicLong()

    fun call(method: String, params: Any = emptyList<Any?>(), wallet: String? = null, timeoutMs: Int = 30_000): Any? {
        require(params is List<*> || params is Map<*, *>) { "params must be a list or a map" }
        val path = if (wallet == null) "/" else "/wallet/" + URLEncoder.encode(wallet, "UTF-8").replace("+", "%20")
        val body = Json.stringify(
            linkedMapOf("jsonrpc" to "1.0", "id" to ids.incrementAndGet(), "method" to method, "params" to params),
        )
        val connection = URL("http://$host:$port$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = timeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString(readCookie().toByteArray()))
            connection.outputStream.use { it.write(body.toByteArray()) }
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED) throw IOException("RPC authentication rejected")
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            val reply = try {
                Json.parse(text) as? Map<*, *>
            } catch (e: JsonException) {
                null
            } ?: throw IOException("RPC returned HTTP $status without a JSON reply")
            val error = reply["error"] as? Map<*, *>
            if (error != null) {
                throw RpcException((error["code"] as? Long)?.toInt() ?: 0, error["message"]?.toString() ?: "")
            }
            return reply["result"]
        } finally {
            connection.disconnect()
        }
    }

    private fun readCookie(): String {
        if (!cookieFile.isFile) throw IOException("RPC cookie not available")
        return cookieFile.readText().trim()
    }
}
