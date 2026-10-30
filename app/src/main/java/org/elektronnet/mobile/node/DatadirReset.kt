package org.elektronnet.mobile.node

import java.io.File

object DatadirReset {
    // chainstate_snapshot and chainstate go together: a snapshot chainstate without its block index cannot be loaded
    private val remove = listOf(
        "blocks", "chainstate", "chainstate_snapshot", "snapshots", "indexes",
        "peers.dat", "anchors.dat", "banlist.json", "banlist.dat", "mempool.dat", "fee_estimates.dat", "debug.log",
        ".elektron_auto_reindex_attempted",
    )

    fun perform(datadir: File, chainSubdir: String = ""): List<String> {
        val removed = mutableListOf<String>()
        for (base in bases(datadir, chainSubdir)) {
            for (name in remove) {
                val target = File(base, name)
                if (target.exists() && (if (target.isDirectory) target.deleteRecursively() else target.delete())) {
                    removed.add(if (base == datadir) name else "${base.name}/$name")
                }
            }
        }
        return removed
    }

    fun keeps(datadir: File): List<String> = datadir.list()?.filter { it !in remove }?.sorted().orEmpty()

    private fun bases(datadir: File, chainSubdir: String): List<File> =
        if (chainSubdir.isEmpty()) listOf(datadir) else listOf(datadir, File(datadir, chainSubdir))
}
