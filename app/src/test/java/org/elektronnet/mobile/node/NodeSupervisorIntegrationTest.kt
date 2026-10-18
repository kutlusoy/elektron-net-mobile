package org.elektronnet.mobile.node

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class NodeSupervisorIntegrationTest {
    @Test
    fun startsRegtestNodeQueriesItAndStopsGracefully() {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dir = Files.createTempDirectory("node-it").toFile()
        val config = NodeConfig(File(binary!!), dir, Chain.Regtest, rpcPort = 28332, p2pPort = 28333, listen = false)
        val node = NodeSupervisor(config, pollIntervalMs = 100)
        node.start()
        val end = System.currentTimeMillis() + 60_000
        while (node.state == NodeState.Starting && System.currentTimeMillis() < end) Thread.sleep(100)
        assertEquals(NodeState.Running, node.state)
        val status = node.status()
        assertEquals("regtest", status.chain)
        assertTrue(status.pruned)
        assertTrue(node.stop())
        val stopEnd = System.currentTimeMillis() + 10_000
        while (node.state != NodeState.Stopped && System.currentTimeMillis() < stopEnd) Thread.sleep(50)
        assertEquals(NodeState.Stopped, node.state)
        assertTrue(File(dir, "regtest").isDirectory)
    }
}
