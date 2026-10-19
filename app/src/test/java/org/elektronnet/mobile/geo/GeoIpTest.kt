package org.elektronnet.mobile.geo

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoIpTest {
    @Test
    fun extractsNumericHosts() {
        assertEquals("188.215.8.165", GeoIp.numericHost("188.215.8.165:8333"))
        assertEquals("188.215.8.165", GeoIp.numericHost("188.215.8.165"))
        assertEquals("2001:db8::1", GeoIp.numericHost("[2001:db8::1]:8333"))
        assertNull(GeoIp.numericHost("kutlusoy.duckdns.org:8333"))
        assertNull(GeoIp.numericHost("abcdefghijklmnop.onion:8333"))
        assertNull(GeoIp.numericHost(""))
    }

    @Test
    fun rejectsPrivateAndSharedRanges() {
        for (ip in listOf("10.0.0.1", "192.168.1.5", "127.0.0.1", "100.64.3.3", "198.18.0.1", "169.254.1.1")) {
            assertFalse(GeoIp.isPublic(InetAddress.getByName(ip)))
        }
        assertTrue(GeoIp.isPublic(InetAddress.getByName("188.215.8.165")))
    }

    @Test
    fun noDatabaseMeansNoCountry() {
        val geo = GeoIp(null)
        assertFalse(geo.available)
        assertNull(geo.lookup("188.215.8.165:8333"))
    }

    @Test
    fun buildsFlagEmoji() {
        assertEquals("🇪🇸", Country("ES", "Spain").flag)
    }
}
