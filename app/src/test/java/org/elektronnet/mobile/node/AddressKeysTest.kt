package org.elektronnet.mobile.node

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressKeysTest {
    @Test
    fun renewingDeletesOnlyTheChosenKey() {
        val dir = Files.createTempDirectory("keys").toFile()
        try {
            val keys = AddressKeys(dir)
            keys.file(AddressKind.Onion).writeText("a")
            keys.file(AddressKind.I2p).writeText("b")
            assertTrue(keys.exists(AddressKind.Onion))
            assertTrue(keys.renew(AddressKind.Onion))
            assertEquals(false, keys.exists(AddressKind.Onion))
            assertTrue(keys.exists(AddressKind.I2p))
            assertTrue(keys.renew(AddressKind.Onion))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun theKeyFilesAreTheOnesTheNodeUses() {
        assertEquals("onion_v3_private_key", AddressKind.Onion.fileName)
        assertEquals("i2p_private_key", AddressKind.I2p.fileName)
    }
}
