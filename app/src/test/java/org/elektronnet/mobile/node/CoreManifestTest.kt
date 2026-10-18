package org.elektronnet.mobile.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreManifestTest {
    private val text = """
        STAMP=abc
        ABI=arm64-v8a
        CORE_REF_NAME=v4.0.5
        CORE_TAG=v4.0.5
        CORE_SHA=5067a968f38ac08259a5628f45ff702a5e3dca8e
        LIB_SHA256=deadbeef
        # comment
    """.trimIndent()

    @Test
    fun parsesKeyValueLines() {
        val manifest = CoreManifest.parse(text)
        assertEquals("v4.0.5", manifest.tag)
        assertEquals("5067a968f38ac08259a5628f45ff702a5e3dca8e", manifest.commit)
        assertEquals("deadbeef", manifest.libSha256)
        assertEquals("v4.0.5 (5067a968)", manifest.label())
    }

    @Test
    fun nightlyHasNoTagAndFallsBackToTheRef() {
        val manifest = CoreManifest.parse("CORE_REF_NAME=main\nCORE_TAG=\nCORE_SHA=0123456789abcdef")
        assertNull(manifest.tag)
        assertEquals("main (01234567)", manifest.label())
    }

    @Test
    fun emptyInputHasNoLabel() {
        assertNull(CoreManifest.parse("").label())
    }
}
