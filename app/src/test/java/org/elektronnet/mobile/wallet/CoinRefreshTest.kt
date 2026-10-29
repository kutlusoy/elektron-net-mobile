package org.elektronnet.mobile.wallet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoinRefreshTest {
    @Test
    fun theNodeScansItselfFromVersion406() {
        assertTrue(CoinRefresh.nodeScansItself("/Elektron Net:4.0.6/"))
        assertTrue(CoinRefresh.nodeScansItself("/Elektron Net:4.1.0/"))
        assertTrue(CoinRefresh.nodeScansItself("/Elektron Net:5.0.0/"))
        assertFalse(CoinRefresh.nodeScansItself("/Elektron Net:4.0.5/"))
        assertFalse(CoinRefresh.nodeScansItself("/Elektron Net:3.9.9/"))
        assertFalse(CoinRefresh.nodeScansItself("/unknown/"))
        assertFalse(CoinRefresh.nodeScansItself(null))
    }
}
