package org.elektronnet.mobile.node

import java.io.File
import java.nio.file.Files
import org.elektronnet.mobile.policy.PolicyDecision
import org.elektronnet.mobile.policy.PolicyReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class NodeControllerIntegrationTest {
    private fun decision(run: Boolean = true, net: Boolean = true, profile: NodeProfile = NodeProfile.Balanced) =
        PolicyDecision(run, net, profile, listen = false, serveSnapshots = false, reasons = emptySet<PolicyReason>())

    private fun waitFor(timeoutMs: Long = 60_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > end) throw AssertionError("timeout")
            Thread.sleep(100)
        }
    }

    private fun networkInfo(controller: NodeController) = controller.current!!.rpc.call("getnetworkinfo") as Map<*, *>

    @Test
    fun appliesPolicyDecisionsToARealNode() {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dir = Files.createTempDirectory("ctl-it").toFile()
        val base = NodeConfig(File(binary!!), dir, Chain.Regtest, rpcPort = 28432, p2pPort = 28433)
        val controller = NodeController(base) { NodeSupervisor(it, pollIntervalMs = 100) }

        controller.apply(decision(net = false))
        waitFor { controller.current!!.state == NodeState.Running }
        assertEquals(false, networkInfo(controller)["networkactive"])

        controller.apply(decision(net = true))
        assertEquals(true, networkInfo(controller)["networkactive"])
        assertEquals(true, networkInfo(controller)["localrelay"])

        val firstSupervisor = controller.current
        controller.apply(decision(net = true, profile = NodeProfile.Foreground))
        waitFor { controller.current!!.state == NodeState.Running }
        assertFalse(controller.current === firstSupervisor)
        assertEquals(false, networkInfo(controller)["localrelay"])

        controller.apply(decision(run = false, net = false))
        waitFor(20_000) { controller.current!!.state == NodeState.Stopped }
        assertTrue(File(dir, "regtest").isDirectory)
    }
}
