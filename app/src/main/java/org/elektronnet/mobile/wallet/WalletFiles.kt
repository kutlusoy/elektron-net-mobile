package org.elektronnet.mobile.wallet

import java.io.File
import java.io.IOException

// throws when anything of the wallet is left, so a removal never reports success with key material still on disk
fun deleteWalletDir(dir: File) {
    dir.deleteRecursively()
    if (dir.exists()) throw IOException("could not delete ${dir.name}")
}

private val SAFE_WALLET_NAME = Regex("^[A-Za-z0-9_-]{1,64}$")

/**
 * Deletes the folder of a wallet of the node. It is looked up where the node keeps it for this network and, if it is not
 * there, in the wallet folders of the other networks of the same data directory, so a wrong guess of the network folder
 * cannot leave a wallet behind. Throws when nothing was found to delete or something is left.
 */
fun deleteWalletEverywhere(datadir: File, chainSubdir: String, name: String) {
    if (!SAFE_WALLET_NAME.matches(name)) throw IOException("unsafe wallet name")
    val expected = File(if (chainSubdir.isEmpty()) datadir else File(datadir, chainSubdir), "wallets/$name")
    if (expected.exists()) return deleteWalletDir(expected)
    val others = listOf(File(datadir, "wallets/$name")) + (datadir.listFiles()?.filter { it.isDirectory }?.map { File(it, "wallets/$name") }.orEmpty())
    val found = others.filter { it.exists() }
    if (found.isEmpty()) return
    found.forEach { deleteWalletDir(it) }
}
