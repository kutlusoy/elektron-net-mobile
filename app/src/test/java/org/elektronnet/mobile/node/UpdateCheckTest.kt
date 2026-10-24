package org.elektronnet.mobile.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    private fun release(tag: String, prerelease: Boolean, name: String = "Elektron Mobile $tag", draft: Boolean = false) =
        """{"tag_name":"$tag","prerelease":$prerelease,"draft":$draft,"name":"$name"}"""

    private val test = "(test build, no updates to or from releases)"
    private val releases = "[" + listOf(
        release("v4.0.5-m0.1.4", true, "Elektron Mobile v4.0.5-m0.1.4 $test"),
        release("v4.0.5-m0.1.5", true, "Elektron Mobile v4.0.5-m0.1.5 $test"),
        release("v4.0.5-m0.1.3-rc1", true),
        release("v4.0.5-m0.1.3-rc2", true),
        release("v4.0.5-m0.1.2", false),
        release("v4.0.5-m0.1.9", false, draft = true),
    ).joinToString(",") + ",{\"tag_name\":\"nightly\",\"prerelease\":true,\"name\":\"x\"}]"

    @Test
    fun parsesNodeWalletAndCandidatePart() {
        val v = AppVersion.parse("v4.0.4-m0.1.0")!!
        assertEquals(listOf(4, 0, 4), v.node)
        assertEquals(listOf(0, 1, 0), v.wallet)
        assertNull(v.candidate)
        assertEquals(2, AppVersion.parse("v4.0.4-m0.1.0-rc2")!!.candidate)
        assertNull(AppVersion.parse("nightly"))
        assertNull(AppVersion.parse("v4.0.4-m0.1.0-beta"))
        assertTrue(AppVersion.parse("v4.0.4-m0.2.0")!! > AppVersion.parse("v4.0.9-m0.1.9")!!)
    }

    @Test
    fun aCandidateIsOlderThanItsReleaseAndNewerThanTheLastOne() {
        assertTrue(AppVersion.parse("4.0.5-m0.1.3-rc1")!! < AppVersion.parse("4.0.5-m0.1.3-rc2")!!)
        assertTrue(AppVersion.parse("4.0.5-m0.1.3-rc9")!! < AppVersion.parse("4.0.5-m0.1.3")!!)
        assertTrue(AppVersion.parse("4.0.5-m0.1.3-rc1")!! > AppVersion.parse("4.0.5-m0.1.2")!!)
    }

    @Test
    fun releasesAreToldApart() {
        assertEquals(ReleaseKind.Test, UpdateCheck.kindOf("v4.0.5-m0.1.4", true, "Elektron Mobile v4.0.5-m0.1.4 $test"))
        assertEquals(ReleaseKind.Candidate, UpdateCheck.kindOf("v4.0.5-m0.1.3-rc1", true, "Elektron Mobile"))
        assertEquals(ReleaseKind.Final, UpdateCheck.kindOf("v4.0.5-m0.1.2", false, "Elektron Mobile"))
        assertNull(UpdateCheck.kindOf("v4.0.5-m0.1.2", true, "Elektron Mobile"))
        assertNull(UpdateCheck.kindOf("nightly", true, "x"))
    }

    @Test
    fun aTestBuildOnlySeesTheNewestTestBuild() {
        assertEquals(UpdateResult.Newer("v4.0.5-m0.1.5", ReleaseKind.Test), UpdateCheck.evaluate("4.0.5-m0.1.4", "0.1.4", releases, testBuild = true))
        assertEquals(UpdateResult.UpToDate, UpdateCheck.evaluate("4.0.5-m0.1.5", "0.1.5", releases, testBuild = true))
        assertEquals(UpdateResult.Newer("v4.0.5-m0.1.5", ReleaseKind.Test), UpdateCheck.evaluate("0.0.0-m0.1.0-dev", "0.1.0", releases, testBuild = true))
    }

    @Test
    fun theReleaseLineSeesCandidatesAndReleasesButNeverTestBuilds() {
        assertEquals(UpdateResult.Newer("v4.0.5-m0.1.3-rc2", ReleaseKind.Candidate), UpdateCheck.evaluate("4.0.5-m0.1.2", "0.1.2", releases, testBuild = false))
        assertEquals(UpdateResult.Newer("v4.0.5-m0.1.3-rc2", ReleaseKind.Candidate), UpdateCheck.evaluate("4.0.5-m0.1.3-rc1", "0.1.3", releases, testBuild = false))
        assertEquals(UpdateResult.UpToDate, UpdateCheck.evaluate("4.0.5-m0.1.3-rc2", "0.1.3", releases, testBuild = false))
        val withFinal = releases.replaceFirst("[", "[" + release("v4.0.5-m0.1.3", false) + ",")
        assertEquals(UpdateResult.Newer("v4.0.5-m0.1.3", ReleaseKind.Final), UpdateCheck.evaluate("4.0.5-m0.1.3-rc2", "0.1.3", withFinal, testBuild = false))
        assertEquals(UpdateResult.UpToDate, UpdateCheck.evaluate("4.0.5-m0.1.3", "0.1.3", withFinal, testBuild = false))
    }

    @Test
    fun badInputIsUnknown() {
        assertEquals(UpdateResult.Unknown, UpdateCheck.evaluate("dev", "x", releases, true))
        assertEquals(UpdateResult.Unknown, UpdateCheck.evaluate("4.0.4-m0.1.0", "0.1.0", "not json", true))
        assertEquals(UpdateResult.Unknown, UpdateCheck.evaluate("4.0.4-m0.1.0", "0.1.0", "[]", true))
        assertEquals(UpdateResult.Unknown, UpdateCheck.evaluate("4.0.4-m0.1.0", "0.1.0", "[" + release("v9.0.0-m0.1.0", false, draft = true) + "]", false))
    }

    @Test
    fun checksHappenOnceADayAndRetryAfterAnHour() {
        val h = 60L * 60 * 1000
        assertTrue(UpdateCheck.due(0, 0, 100 * h))
        assertFalse(UpdateCheck.due(100 * h, 100 * h, 110 * h))
        assertTrue(UpdateCheck.due(100 * h, 100 * h, 124 * h))
        assertFalse(UpdateCheck.due(100 * h, 123 * h + h / 2, 124 * h))
        assertTrue(UpdateCheck.due(100 * h, 123 * h, 124 * h))
    }
}
