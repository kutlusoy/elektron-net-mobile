package org.elektronnet.mobile.wallet

import java.io.File
import java.nio.file.Files
import org.elektronnet.mobile.node.Chain
import org.elektronnet.mobile.node.NodeConfig
import org.elektronnet.mobile.node.NodeState
import org.elektronnet.mobile.node.NodeSupervisor
import org.elektronnet.mobile.node.RpcException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class WalletAutoLoadIntegrationTest {
    @Test
    fun anUnloadedWalletIsLoadedOnFirstUseAndAMissingOneStaysAnError() {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dir = Files.createTempDirectory("autoload-it").toFile()
        val supervisor = NodeSupervisor(NodeConfig(File(binary!!), dir, Chain.Regtest, rpcPort = 29260, p2pPort = 29261), pollIntervalMs = 100)
        supervisor.start()
        try {
            val end = System.currentTimeMillis() + 60_000
            while (supervisor.state != NodeState.Running) {
                check(System.currentTimeMillis() < end) { "node did not start" }
                Thread.sleep(100)
            }
            val rpc = supervisor.rpc.asWalletRpc()
            rpc.call("createwallet", linkedMapOf("wallet_name" to "elektron"), null)
            rpc.call("unloadwallet", listOf("elektron"), null)
            assertTrue("elektron" !in (rpc.call("listwallets", emptyList<Any?>(), null) as List<*>))

            val address = rpc.call("getnewaddress", emptyList<Any?>(), "elektron").toString()
            assertTrue(address.startsWith("bcrt1"))
            assertTrue("elektron" in (rpc.call("listwallets", emptyList<Any?>(), null) as List<*>))

            val failure = try {
                rpc.call("getbalances", emptyList<Any?>(), "does-not-exist")
                null
            } catch (e: RpcException) {
                e.code
            }
            assertEquals(-18, failure)
            assertTrue("does-not-exist" !in (rpc.call("listwallets", emptyList<Any?>(), null) as List<*>))
        } finally {
            supervisor.stop()
            dir.deleteRecursively()
        }
    }
}
