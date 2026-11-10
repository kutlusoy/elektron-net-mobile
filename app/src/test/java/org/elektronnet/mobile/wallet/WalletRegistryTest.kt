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
        val def = WalletBootstrap.DEFAULT_WALLET
        val registry = WalletRegistry(MemoryWalletRegistryStore())
        registry.add(def, "")
        assertEquals("${def}_3", registry.nextName(setOf("${def}_2")))
        assertEquals("${def}_2", registry.nextName())
    }

    @Test
    fun theActiveWalletIsTheChosenOneOrTheFirst() {
        val def = WalletBootstrap.DEFAULT_WALLET
        val registry = WalletRegistry(MemoryWalletRegistryStore())
        registry.add(def, "")
        registry.add("${def}_2", "second")
        assertEquals(def, registry.active().name)
        registry.setActive("${def}_2")
        assertEquals("${def}_2", registry.active().name)
        registry.remove("${def}_2")
        assertEquals(def, registry.active().name)
    }

    @Test
    fun multiCoinInferenceAndPersistenceRoundTrip() {
        val registry = WalletRegistry(MemoryWalletRegistryStore())
        val elekWallet = registry.add("wallet", "My Daily Spending")
        val btcWallet = registry.add("btc_vault", "Bitcoin Cold Storage")
        val customBtc = registry.add("savings", "Emergency Fund", coin = "BTC")

        assertEquals("ELEK", elekWallet.coin)
        assertEquals("BTC", btcWallet.coin)
        assertEquals("BTC", customBtc.coin)

        val loaded = registry.wallets()
        assertEquals(3, loaded.size)
        assertEquals("ELEK", loaded[0].coin)
        assertEquals("BTC", loaded[1].coin)
        assertEquals("BTC", loaded[2].coin)
    }
}
