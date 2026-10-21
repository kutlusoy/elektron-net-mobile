package org.elektronnet.mobile.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletRegistryTest {
    @Test
    fun askingForTheActiveWalletNeverCreatesOne() {
        val registry = WalletRegistry(MemoryWalletRegistryStore())
        assertEquals(WalletBootstrap.DEFAULT_WALLET, registry.active().name)
        assertTrue(registry.wallets().isEmpty())
        assertEquals(WalletBootstrap.DEFAULT_WALLET, registry.nextName())
    }

    @Test
    fun namesAlreadyOnDiskAreSkipped() {
        val registry = WalletRegistry(MemoryWalletRegistryStore())
        registry.add("elektron", "")
        assertEquals("elektron_3", registry.nextName(setOf("elektron_2")))
        assertEquals("elektron_2", registry.nextName())
    }

    @Test
    fun theActiveWalletIsTheChosenOneOrTheFirst() {
        val registry = WalletRegistry(MemoryWalletRegistryStore())
        registry.add("elektron", "")
        registry.add("elektron_2", "second")
        assertEquals("elektron", registry.active().name)
        registry.setActive("elektron_2")
        assertEquals("elektron_2", registry.active().name)
        registry.remove("elektron_2")
        assertEquals("elektron", registry.active().name)
    }
}
