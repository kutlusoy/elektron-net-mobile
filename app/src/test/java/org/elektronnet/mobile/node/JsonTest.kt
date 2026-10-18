package org.elektronnet.mobile.node

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {
    @Test
    fun parsesNodeStyleReply() {
        val text = """{"result":{"chain":"main","blocks":269354,"verificationprogress":0.99,"pruned":true,"x":null},"error":null,"id":1}"""
        val reply = Json.parse(text) as Map<*, *>
        val result = reply["result"] as Map<*, *>
        assertEquals("main", result["chain"])
        assertEquals(269354L, result["blocks"])
        assertEquals(BigDecimal("0.99"), result["verificationprogress"])
        assertEquals(true, result["pruned"])
        assertNull(result["x"])
        assertNull(reply["error"])
    }

    @Test
    fun keepsAmountPrecision() {
        val value = Json.parse("[0.12345678, 21000000.00000001]") as List<*>
        assertEquals(BigDecimal("0.12345678"), value[0])
        assertEquals(BigDecimal("21000000.00000001"), value[1])
    }

    @Test
    fun roundTripsEscapes() {
        val original = mapOf("a" to "line\nbreak \"quoted\" \\ tab\t \u0001", "b" to listOf(1L, true, null))
        assertEquals(original, Json.parse(Json.stringify(original)))
    }

    @Test
    fun parsesUnicodeEscape() {
        assertEquals("\u00e9", Json.parse("\"\\u00e9\""))
    }

    @Test
    fun rejectsMalformed() {
        for (text in listOf("{", "[1,", "{\"a\" 1}", "tru", "\"abc", "[1] x", "")) {
            val failed = try {
                Json.parse(text)
                false
            } catch (e: JsonException) {
                true
            }
            assertTrue("should reject: $text", failed)
        }
    }
}
