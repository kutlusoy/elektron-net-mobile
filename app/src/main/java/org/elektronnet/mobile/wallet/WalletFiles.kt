package org.elektronnet.mobile.wallet

import java.io.File
import java.io.IOException

// throws when anything of the wallet is left, so a removal never reports success with key material still on disk
fun deleteWalletDir(dir: File) {
    overwrite(dir)
    dir.deleteRecursively()
    if (dir.exists()) throw IOException("could not delete ${dir.name}")
}

// best effort: the content is overwritten before the file is deleted, so a deleted wallet is not left readable in a free block
private fun overwrite(dir: File) {
    runCatching {
        dir.walkTopDown().filter { it.isFile && !java.nio.file.Files.isSymbolicLink(it.toPath()) }.forEach { file ->
            java.io.RandomAccessFile(file, "rw").use { out ->
                val zeros = ByteArray(8192)
                var left = out.length()
                out.seek(0)
                while (left > 0) {
                    val n = minOf(left, zeros.size.toLong()).toInt()
                    out.write(zeros, 0, n)
                    left -= n
                }
                out.fd.sync()
            }
        }
    }
}

private val SAFE_WALLET_NAME = Regex("^[A-Za-z0-9_-]{1,64}$")

/**
 * Deletes the folder of a wallet of the node. It is looked up where the node keeps it for this network and, if it is not
 * there, in the wallet folders of the other networks of the same data directory, so a wrong guess of the network folder
 * cannot leave a wallet behind. Throws when nothing was found to delete or something is left.
 */
fun deleteWalletEverywhere(datadir: File, chainSubdir: String, name: String, nodeWalletDir: File? = null) {
    if (!SAFE_WALLET_NAME.matches(name)) throw IOException("unsafe wallet name")
    val chainDir = if (chainSubdir.isEmpty()) datadir else File(datadir, chainSubdir)
    val expected = File(chainDir, "wallets/$name")
    if (expected.exists()) return deleteWalletDir(expected)
    val folders = listOf(datadir) + datadir.listFiles()?.filter { it.isDirectory }.orEmpty()
    val candidates = buildList {
        folders.forEach { add(File(it, "wallets/$name")) }
        // without a wallets folder the node keeps its wallets directly in the data folder
        folders.map { File(it, name) }.filter { File(it, "wallet.dat").isFile }.forEach { add(it) }
        nodeWalletDir?.takeIf { it.canonicalPath.startsWith(datadir.canonicalPath + File.separator) }?.let { add(File(it, name)) }
    }
    candidates.filter { it.exists() }.distinct().forEach { deleteWalletDir(it) }
}
