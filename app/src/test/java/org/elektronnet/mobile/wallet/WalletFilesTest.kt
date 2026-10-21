package org.elektronnet.mobile.wallet

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class WalletFilesTest {
    @Test
    fun removesTheWholeWalletFolder() {
        val dir = File.createTempFile("wallet", "").also { it.delete(); File(it, "sub").mkdirs(); File(it, "sub/wallet.dat").writeText("x") }
        deleteWalletDir(dir)
        assertFalse(dir.exists())
    }
}
