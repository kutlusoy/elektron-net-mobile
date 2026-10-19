package org.elektronnet.mobile.node

import java.io.File

object DatadirReset {
    private val remove = listOf("blocks", "chainstate", "indexes", "peers.dat", "anchors.dat", "banlist.json", "banlist.dat", "mempool.dat", "fee_estimates.dat", "debug.log")

    fun perform(datadir: File): List<String> {
        val removed = mutableListOf<String>()
        for (name in remove) {
            val target = File(datadir, name)
            if (target.exists() && (if (target.isDirectory) target.deleteRecursively() else target.delete())) removed.add(name)
        }
        return removed
    }

    fun keeps(datadir: File): List<String> = datadir.list()?.filter { it !in remove }?.sorted().orEmpty()
}
