package org.elektronnet.mobile.node

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatadirVersionTest {
    private fun dir(): File = Files.createTempDirectory("dv").toFile()

    @Test
    fun writesTheMarkerOnFirstUse() {
        val d = dir()
        DatadirVersion.ensure(d)
        assertEquals("1", File(d, "datadir.version").readText().trim())
    }

    @Test
    fun refusesDataFromANewerApp() {
        val d = dir()
        File(d, "datadir.version").writeText("7\n")
        try {
            DatadirVersion.ensure(d)
            throw AssertionError("expected a failure")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("newer"))
        }
    }

    @Test
    fun upgradesAnOlderMarker() {
        val d = dir()
        File(d, "datadir.version").writeText("1\n")
        DatadirVersion.ensure(d, schema = 2)
        assertEquals("2", File(d, "datadir.version").readText().trim())
    }
}
