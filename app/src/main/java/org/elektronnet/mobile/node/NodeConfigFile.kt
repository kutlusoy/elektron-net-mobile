package org.elektronnet.mobile.node

import java.io.File

object NodeConfigFile {
    private const val HEADER = "# managed by the app, do not add peers here\n"

    fun ensure(config: NodeConfig) {
        config.datadir.mkdirs()
        val section = config.chain.section ?: return
        val file = config.confFile
        if (!file.exists()) {
            file.writeText(HEADER)
            return
        }
        if (hasForeignSettings(file.readLines(), section)) {
            file.copyTo(File(config.datadir, "bitcoin.conf.bak"), overwrite = true)
            file.writeText(HEADER)
        }
    }

    fun hasForeignSettings(lines: List<String>, chainSection: String): Boolean {
        var current: String? = null
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                current = line.substring(1, line.length - 1)
                continue
            }
            if (current != chainSection) return true
        }
        return false
    }
}
