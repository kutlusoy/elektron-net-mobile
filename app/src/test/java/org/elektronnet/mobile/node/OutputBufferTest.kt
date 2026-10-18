package org.elektronnet.mobile.node

import org.junit.Assert.assertEquals
import org.junit.Test

class OutputBufferTest {
    @Test
    fun keepsOnlyTheNewestLines() {
        val buffer = OutputBuffer(3)
        (1..5).forEach { buffer.add("l$it") }
        assertEquals(listOf("l3", "l4", "l5"), buffer.snapshot())
        assertEquals(listOf("l4", "l5"), buffer.tail(2))
    }

    @Test
    fun notifiesListeners() {
        val buffer = OutputBuffer()
        val seen = mutableListOf<String>()
        buffer.addListener { seen.add(it) }
        buffer.add("a")
        assertEquals(listOf("a"), seen)
    }
}
