package org.elektronnet.mobile.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {
    @Test
    fun hidesPeerAddresses() {
        assertEquals("peer=7 addr=<ip> version 70016", LogSanitizer.clean("peer=7 addr=203.0.113.9:9333 version 70016"))
        assertEquals("connected to <ip>", LogSanitizer.clean("connected to [2001:db8::1]:9333"))
        assertEquals("via <onion>", LogSanitizer.clean("via abcdefghijklmnopqrstuvwxyz234567abcdefghijklmnopqrstuvwx.onion:9333"))
        assertEquals("LeaseSet for <i2p> not found", LogSanitizer.clean("LeaseSet for aomzpobs3m5n3g64j3tvpqegn7o5qeaazgok74uruczxbqep3hxq.b32.i2p not found"))
    }

    @Test
    fun hidesPathsAndAddresses() {
        assertEquals("Using datadir <path>", LogSanitizer.clean("Using datadir /data/user/0/org.elektronnet.mobile/files/elektron-datadir"))
        assertEquals("pay <address> done", LogSanitizer.clean("pay be1qw508d6qejxtdg4y5r3zarvary0c5xw7k5s9qdd done"))
        assertEquals("pay <address> done", LogSanitizer.clean("pay mipcBbFg9gMiCh81Kj8tqqdgoZub1ZJRfn done"))
    }

    @Test
    fun keepsUsefulFacts() {
        val line = "UpdateTip: new best=00000000000000000001a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f7 height=197280 progress=0.999800"
        assertEquals(line, LogSanitizer.clean(line))
        val times = "2026-10-19T10:15:30Z Pruning blockstore"
        assertEquals(times, LogSanitizer.clean(times))
        assertFalse(LogSanitizer.clean(listOf("a 10.0.0.1", "b")).contains("10.0.0.1"))
        assertTrue(LogSanitizer.clean(listOf("a", "b")).contains("\n"))
    }
}
