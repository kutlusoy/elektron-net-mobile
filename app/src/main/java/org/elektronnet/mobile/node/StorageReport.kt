package org.elektronnet.mobile.node

import java.io.File

enum class StorageKind { Blocks, Index, Utxo, Snapshots, Wallet, Tor, I2p, Logs, OtherNode, App }

data class StorageItem(val kind: StorageKind, val bytes: Long, val name: String = "")

data class StorageReport(val items: List<StorageItem>) {
    val total: Long get() = items.sumOf { it.bytes }
}

object StorageScan {
    fun size(file: File): Long = when {
        file.isFile -> file.length()
        file.isDirectory -> file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        else -> 0L
    }

    // filesDir/<datadirName> is the node, <chainSubdir> is empty on the main network
    fun scan(filesDir: File, datadirName: String, chainSubdir: String): StorageReport {
        val datadir = File(filesDir, datadirName)
        val chain = if (chainSubdir.isEmpty()) datadir else File(datadir, chainSubdir)
        val blocksDir = File(chain, "blocks")
        val index = size(File(blocksDir, "index"))
        val blocks = (size(blocksDir) - index).coerceAtLeast(0L)
        // after a snapshot bootstrap the UTXO set lives in chainstate_snapshot, the plain chainstate stays empty
        val utxo = chain.listFiles().orEmpty().filter { it.isDirectory && (it.name == "chainstate" || it.name == "chainstate_snapshot") }.sumOf { size(it) }
        val snapshots = listOf(File(chain, "snapshots"), File(datadir, "snapshots")).distinct().sumOf { size(it) }
        val wallets = File(chain, "wallets").listFiles().orEmpty().sortedBy { it.name }.map { StorageItem(StorageKind.Wallet, size(it), it.name) }
        val logs = chain.listFiles().orEmpty().filter { it.isFile && it.name.startsWith("debug.log") }.sumOf { it.length() }
        val network = File(filesDir, "network")
        val tor = size(File(network, "tor"))
        val i2p = size(File(network, "i2pd"))
        val node = size(datadir)
        val known = blocks + index + utxo + snapshots + wallets.sumOf { it.bytes } + logs
        val app = (size(filesDir) - node - size(network)).coerceAtLeast(0L)
        val items = buildList {
            add(StorageItem(StorageKind.Blocks, blocks))
            add(StorageItem(StorageKind.Index, index))
            add(StorageItem(StorageKind.Utxo, utxo))
            if (snapshots > 0) add(StorageItem(StorageKind.Snapshots, snapshots))
            addAll(wallets)
            add(StorageItem(StorageKind.Logs, logs))
            add(StorageItem(StorageKind.OtherNode, (node - known).coerceAtLeast(0L)))
            if (tor > 0) add(StorageItem(StorageKind.Tor, tor))
            if (i2p > 0) add(StorageItem(StorageKind.I2p, i2p))
            add(StorageItem(StorageKind.App, app))
        }
        return StorageReport(items)
    }
}
