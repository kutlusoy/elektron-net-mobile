package org.elektronnet.mobile.node

import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.Base64
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RpcClientTest {
    private lateinit var server: ServerSocket
    private lateinit var cookie: File
    private var lastAuth: String? = null
    private var lastPath: String? = null
    private var lastBody: String? = null
    private var status = 200
    private var reply = """{"result":{"blocks":5},"error":null,"id":1}"""

    @Before
    fun setUp() {
        cookie = Files.createTempFile("cookie", "").toFile().apply { writeText("__cookie__:secret\n") }
        server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        Thread {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (e: IOException) {
                    break
                }
                socket.use { handle(it) }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun handle(socket: Socket) {
        val input = socket.getInputStream()
        fun readLine(): String {
            val line = StringBuilder()
            while (true) {
                val c = input.read()
                if (c < 0 || c == '\n'.code) break
                if (c != '\r'.code) line.append(c.toChar())
            }
            return line.toString()
        }
        val request = readLine().split(" ")
        lastPath = java.net.URLDecoder.decode(request[1], "UTF-8").replace("+", " ")
        var length = 0
        lastAuth = null
        while (true) {
            val header = readLine()
            if (header.isEmpty()) break
            val name = header.substringBefore(':').trim().lowercase()
            val value = header.substringAfter(':').trim()
            if (name == "content-length") length = value.toInt()
            if (name == "authorization") lastAuth = value
        }
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) break
            read += n
        }
        lastBody = String(body, Charsets.UTF_8)
        val bytes = reply.toByteArray()
        val out = socket.getOutputStream()
        out.write("HTTP/1.1 $status X\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes)
        out.flush()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun client() = RpcClient(server.localPort, cookie)

    @Test
    fun sendsCookieAuthAndParams() {
        val result = client().call("getblockchaininfo", listOf("a", 1L)) as Map<*, *>
        assertEquals(5L, result["blocks"])
        assertEquals("Basic " + Base64.getEncoder().encodeToString("__cookie__:secret".toByteArray()), lastAuth)
        assertTrue(lastBody!!.contains("\"method\":\"getblockchaininfo\""))
        assertTrue(lastBody!!.contains("\"params\":[\"a\",1]"))
        assertEquals("/", lastPath)
    }

    @Test
    fun usesWalletPath() {
        client().call("getbalance", wallet = "my wallet")
        assertEquals("/wallet/my wallet", lastPath)
    }

    @Test
    fun mapsRpcErrors() {
        status = 500
        reply = """{"result":null,"error":{"code":-28,"message":"Loading block index..."},"id":1}"""
        val e = try {
            client().call("getblockchaininfo")
            null
        } catch (e: RpcException) {
            e
        }
        assertEquals(-28, e!!.code)
        assertTrue(e.inWarmup)
    }

    @Test
    fun rejectedAuthIsAnIoError() {
        status = 401
        reply = ""
        val failed = try {
            client().call("getblockchaininfo")
            false
        } catch (e: IOException) {
            true
        }
        assertTrue(failed)
    }

    @Test
    fun missingCookieIsAnIoError() {
        cookie.delete()
        val failed = try {
            client().call("getblockchaininfo")
            false
        } catch (e: IOException) {
            true
        }
        assertTrue(failed)
    }
}
