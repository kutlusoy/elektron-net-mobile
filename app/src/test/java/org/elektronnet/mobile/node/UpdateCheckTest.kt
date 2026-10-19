package org.elektronnet.mobile.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    private val tags = """[{"name":"v4.0.4-m0.1.0"},{"name":"v4.0.4-m0.2.1"},{"name":"v4.0.3-m0.0.9"},{"name":"nightly"}]"""

    @Test
    fun parsesNodeAndWalletParts() {
        val v = AppVersion.parse("v4.0.4-m0.1.0")!!
        assertEquals(listOf(4, 0, 4), v.node)
        assertEquals(listOf(0, 1, 0), v.wallet)
        assertNull(AppVersion.parse("nightly"))
        assertTrue(AppVersion.parse("v4.0.4-m0.2.0")!! > AppVersion.parse("v4.0.9-m0.1.9")!!)
    }

    @Test
    fun findsTheHighestTag() {
        assertEquals(UpdateResult.Newer("v4.0.4-m0.2.1"), UpdateCheck.evaluate("4.0.4-m0.1.0", "0.1.0", tags))
        assertEquals(UpdateResult.UpToDate, UpdateCheck.evaluate("4.0.4-m0.2.1", "0.2.1", tags))
        assertEquals(UpdateResult.UpToDate, UpdateCheck.evaluate("4.0.4-m0.3.0", "0.3.0", tags))
    }

    @Test
    fun badInputIsUnknown() {
        assertEquals(UpdateResult.Unknown, UpdateCheck.evaluate("dev", "x", tags))
        assertEquals(UpdateResult.Unknown, UpdateCheck.evaluate("4.0.4-m0.1.0", "0.1.0", "not json"))
        assertEquals(UpdateResult.Unknown, UpdateCheck.evaluate("4.0.4-m0.1.0", "0.1.0", "[]"))
        assertEquals(UpdateResult.Newer("v4.0.4-m0.2.1"), UpdateCheck.evaluate("0.0.0-m0.1.0-dev", "0.1.0", tags))
    }
}
