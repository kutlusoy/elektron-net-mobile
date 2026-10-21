package org.elektronnet.mobile.wallet

import java.io.File
import java.io.IOException

// throws when anything of the wallet is left, so a removal never reports success with key material still on disk
fun deleteWalletDir(dir: File) {
    dir.deleteRecursively()
    if (dir.exists()) throw IOException("could not delete ${dir.name}")
}
