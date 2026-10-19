package org.elektronnet.mobile.node

import java.io.File

object DatadirVersion {
    const val SCHEMA = 1
    private const val FILE = "datadir.version"

    fun ensure(datadir: File, schema: Int = SCHEMA) {
        datadir.mkdirs()
        val file = File(datadir, FILE)
        val stored = if (file.isFile) file.readText().trim().toIntOrNull() else null
        check(stored == null || stored <= schema) { "this data folder was written by a newer app version (schema $stored)" }
        if (stored != schema) file.writeText("$schema\n")
    }
}
