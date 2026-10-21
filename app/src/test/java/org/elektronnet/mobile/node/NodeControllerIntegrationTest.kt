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
    private fun decision(run: Boolean = true, net: Boolean = true, profile: NodeProfile = NodeProfile.Balanced, network: NetworkSettings = NetworkSettings()) =
        PolicyDecision(run, net, profile, listen = false, serveSnapshots = false, reasons = emptySet<PolicyReason>(), network = network)

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

    @Test
    fun onlyTheSettingsThatChangeTheNodeArgumentsRestartIt() {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dir = Files.createTempDirectory("ctl-net-it").toFile()
        val base = NodeConfig(File(binary!!), dir, Chain.Regtest, rpcPort = 28442, p2pPort = 28443, ports = NetworkPorts(torSocks = 28450, torControl = 28451, i2pSam = 28452))
        val controller = NodeController(base) { NodeSupervisor(it, pollIntervalMs = 100) }
        try {
            controller.apply(decision())
            waitFor { controller.current!!.state == NodeState.Running }
            val first = controller.current

            val withTor = NetworkSettings(tor = TorSettings(enabled = true))
            controller.apply(decision(network = withTor))
            waitFor { controller.current!!.state == NodeState.Running }
            val second = controller.current
            assertFalse(second === first)
            val onion = (networkInfo(controller)["networks"] as List<*>).map { it as Map<*, *> }.first { it["name"] == "onion" }
            assertEquals("127.0.0.1:28450", onion["proxy"])

            val relay = withTor.copy(tor = withTor.tor.copy(role = TorRole.Relay, nickname = "Elektron", bandwidthKbps = 512))
            controller.apply(decision(network = relay))
            assertTrue(controller.current === second)

            controller.apply(decision(network = relay.copy(tor = relay.tor.copy(routing = TorRouting.AllTraffic))))
            waitFor { controller.current!!.state == NodeState.Running && controller.current !== second }
            val allTraffic = (networkInfo(controller)["networks"] as List<*>).map { it as Map<*, *> }
            assertTrue(allTraffic.filter { it["name"] == "ipv4" || it["name"] == "onion" }.all { it["proxy"] == "127.0.0.1:28450" })
        } finally {
            controller.shutdown()
            dir.deleteRecursively()
        }
    }

    @Test
    fun restartingOnlyTheNodeLeavesTheHelpersRunning() {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        val dir = Files.createTempDirectory("ctl-restart-it").toFile()
        val ports = NetworkPorts(torSocks = 28460, torControl = 28461, i2pSam = 28462)
        val tor = File(dir, "tor").apply {
            writeText("#!/bin/sh\necho started\nexec python3 -c \"import socket,time\ns=socket.socket()\ns.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)\ns.bind(('127.0.0.1',28461))\ns.listen(5)\ntime.sleep(600)\"\n")
            setExecutable(true)
        }
        val services = NetworkServices(ServiceBinaries(tor, null), File(dir, "svc"), ports)
        val base = NodeConfig(File(binary!!), File(dir, "node"), Chain.Regtest, rpcPort = 28452, p2pPort = 28453, ports = ports)
        val controller = NodeController(base, services = services) { NodeSupervisor(it, pollIntervalMs = 100) }
        val network = NetworkSettings(tor = TorSettings(enabled = true))
        try {
            controller.apply(decision(network = network))
            waitFor { controller.current!!.state == NodeState.Running && services.torState == ServiceState.Running }
            val first = controller.current
            val torOutput = services.torOutput()
            controller.restartNode()
            waitFor(20_000) { first!!.state == NodeState.Stopped }
            assertEquals(ServiceState.Running, services.torState)
            controller.apply(decision(network = network))
            waitFor { controller.current!!.state == NodeState.Running && controller.current !== first }
            assertEquals(ServiceState.Running, services.torState)
            assertEquals(torOutput, services.torOutput())
        } finally {
            controller.shutdown()
            dir.deleteRecursively()
        }
    }
}
