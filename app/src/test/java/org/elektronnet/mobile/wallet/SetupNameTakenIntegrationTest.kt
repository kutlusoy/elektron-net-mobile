package org.elektronnet.mobile.wallet

import java.io.File
import java.nio.file.Files
import org.elektronnet.mobile.node.Chain
import org.elektronnet.mobile.node.NodeConfig
import org.elektronnet.mobile.node.NodeState
import org.elektronnet.mobile.node.NodeSupervisor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class SetupNameTakenIntegrationTest {
    private val phrase = Bip39.parse("legal winner thank year wave sausage worth useful legal winner thank yellow")

    @Test
    fun aTakenWalletNameMovesTheNewWalletToTheNextFreeOne() {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dir = Files.createTempDirectory("setup-name-it").toFile()
        val supervisor = NodeSupervisor(NodeConfig(File(binary!!), dir, Chain.Regtest, rpcPort = 29250, p2pPort = 29251), pollIntervalMs = 100)
        supervisor.start()
        try {
            val end = System.currentTimeMillis() + 60_000
            while (supervisor.state != NodeState.Running) {
                check(System.currentTimeMillis() < end) { "node did not start" }
                Thread.sleep(100)
            }
            val rpc = supervisor.rpc.asWalletRpc()
            rpc.call("createwallet", linkedMapOf("wallet_name" to "elektron"), null)
            rpc.call("createwallet", linkedMapOf("wallet_name" to "elektron_2"), null)
            val registry = WalletRegistry(MemoryWalletRegistryStore())
            registry.add("elektron", "Milio")
            val reconciler = WalletReconciler(rpc, registry)
            val setup = WalletSetup(onNameTaken = { old ->
                val next = registry.nextName((reconciler.namesOnDisk() ?: emptyList()).toSet() + old)
                registry.changeName(old, next)
                next
            }) { name -> WalletBootstrap(rpc, Bip39(loadWordlist()), testnet = true, legacy = true, removeWalletFiles = { File(dir, "regtest/wallets/$it").deleteRecursively() }, walletName = name) }

            setup.begin(SetupRequest(phrase, "correct horse battery", restore = false, walletName = "elektron"))
            repeat(4) { setup.step(nodeSynced = false) }

            assertEquals("elektron_3", setup.walletName)
            assertEquals(SetupStatus.Ready(), setup.status)
            assertEquals(listOf("elektron_3"), registry.wallets().map { it.name })
            assertEquals("Milio", registry.wallets().single().label)
            assertTrue("elektron_3" in (rpc.call("listwallets", emptyList<Any?>(), null) as List<*>))
            val info = rpc.call("getwalletinfo", emptyList<Any?>(), "elektron_3") as Map<*, *>
            assertEquals(0L, info["unlocked_until"])
        } finally {
            supervisor.stop()
            dir.deleteRecursively()
        }
    }
}
