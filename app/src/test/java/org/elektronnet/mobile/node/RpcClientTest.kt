package org.elektronnet.mobile.node

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Base64
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RpcClientTest {
    private lateinit var server: HttpServer
    private lateinit var cookie: File
    private var lastAuth: String? = null
    private var lastPath: String? = null
    private var lastBody: String? = null
    private var status = 200
    private var reply = """{"result":{"blocks":5},"error":null,"id":1}"""

    @Before
    fun setUp() {
        cookie = Files.createTempFile("cookie", "").toFile().apply { writeText("__cookie__:secret\n") }
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            lastAuth = exchange.requestHeaders.getFirst("Authorization")
            lastPath = exchange.requestURI.path
            lastBody = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val bytes = reply.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun client() = RpcClient(server.address.port, cookie)

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
