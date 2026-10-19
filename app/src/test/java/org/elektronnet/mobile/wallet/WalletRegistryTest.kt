package org.elektronnet.mobile.wallet

import org.junit.Assert.assertEquals
import org.junit.Test

class WalletRegistryTest {
    private fun registry() = WalletRegistry(MemoryWalletRegistryStore())

    @Test
    fun startsWithTheDefaultWallet() {
        val r = registry()
        assertEquals(emptyList<OwnWallet>(), r.wallets())
        assertEquals(WalletBootstrap.DEFAULT_WALLET, r.active().name)
        assertEquals(1, r.wallets().size)
        r.ensureDefault()
        assertEquals(1, r.wallets().size)
    }

    @Test
    fun addsAndSwitchesWallets() {
        val r = registry()
        r.ensureDefault()
        val name = r.nextName()
        assertEquals("elektron_2", name)
        r.add(name, " Savings ")
        assertEquals("elektron_3", r.nextName())
        r.setActive(name)
        assertEquals("Savings", r.active().label)
        r.setActive("unknown")
        assertEquals(name, r.active().name)
    }

    @Test
    fun renameAndRemove() {
        val r = registry()
        r.ensureDefault()
        r.add("elektron_2", "A")
        r.setActive("elektron_2")
        r.rename("elektron_2", "B")
        assertEquals("B", r.active().label)
        r.remove("elektron_2")
        assertEquals(WalletBootstrap.DEFAULT_WALLET, r.active().name)
        assertEquals(1, r.wallets().size)
    }

    @Test
    fun brokenStoredDataFallsBackToDefault() {
        val store = MemoryWalletRegistryStore().apply { json = "not json" }
        assertEquals(WalletBootstrap.DEFAULT_WALLET, WalletRegistry(store).active().name)
    }
}
