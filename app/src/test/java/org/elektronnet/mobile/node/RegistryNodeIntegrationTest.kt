package org.elektronnet.mobile.node

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class RegistryNodeIntegrationTest {
    @Test
    fun theNodeTakesTheRegistryAddressesAsOneTimeConnections() {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dir = Files.createTempDirectory("registry-it").toFile()
        val supervisor = NodeSupervisor(NodeConfig(File(binary!!), dir, Chain.Regtest, rpcPort = 29500, p2pPort = 29501), pollIntervalMs = 100)
        supervisor.start()
        try {
            val end = System.currentTimeMillis() + 60_000
            while (supervisor.state != NodeState.Running) {
                check(System.currentTimeMillis() < end) { "node did not start" }
                Thread.sleep(100)
            }
            val entries = RegistryPeers.parse(
                "yr3vxmifwm5z7ukcfexf2idk3eud2zimilyxoa3rxkfyxspjhizczsqd.onion:8333\n" +
                    "aomzpobs3m5n3g64j3tvpqegn7o5qeaazgok74uruczxbqep3hxq.b32.i2p:8333\n",
                true,
            ) + RegistryPeers.parse("aomzpobs3m5n3g64j3tvpqegn7o5qeaazgok74uruczxbqep3hxq.b32.i2p:8333", false)
            assertEquals(2, entries.size)
            entries.forEach { supervisor.rpc.call("addnode", listOf(it, "onetry")) }
            assertEquals(0, ((supervisor.rpc.call("getaddednodeinfo") as List<*>)).size)
        } finally {
            supervisor.stop()
            dir.deleteRecursively()
        }
    }
}
