package org.elektronnet.mobile.node

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class TorNodeIntegrationTest {
    @Test
    fun theNodeUsesTheTorProxyAndAuthenticatesAtTheControlPort() {
        val node = System.getenv("ELEKTROND_BIN")
        val tor = System.getenv("TOR_BIN")
        assumeTrue("ELEKTROND_BIN or TOR_BIN not set", node != null && File(node).canExecute() && tor != null && File(tor).canExecute())
        val root = Files.createTempDirectory("tornode").toFile()
        val ports = NetworkPorts(torSocks = 29190, torControl = 29191, i2pSam = 29192)
        val services = NetworkServices(ServiceBinaries(File(tor!!), null), File(root, "svc"), ports)
        val network = NetworkSettings(tor = TorSettings(enabled = true))
        val config = NodeConfig(
            File(node!!), File(root, "node"), Chain.Regtest, rpcPort = 29193, p2pPort = 29194, listen = true,
            network = network, ports = ports, extraArgs = listOf("-debug=tor"),
        )
        val supervisor = NodeSupervisor(config, pollIntervalMs = 100)
        try {
            services.sync(network, active = true)
            val torEnd = System.currentTimeMillis() + 60_000
            while (services.torState != ServiceState.Running) {
                check(System.currentTimeMillis() < torEnd) { "tor did not open its control port: ${services.torOutput().takeLast(5)}" }
                Thread.sleep(100)
            }
            supervisor.start()
            val end = System.currentTimeMillis() + 60_000
            while (supervisor.state != NodeState.Running) {
                check(System.currentTimeMillis() < end) { "node did not start" }
                Thread.sleep(100)
            }
            val info = supervisor.rpc.call("getnetworkinfo") as Map<*, *>
            val onion = (info["networks"] as List<*>).map { it as Map<*, *> }.first { it["name"] == "onion" }
            assertEquals("127.0.0.1:29190", onion["proxy"])
            val logEnd = System.currentTimeMillis() + 30_000
            while (supervisor.output.snapshot().none { it.contains("[tor]") && it.contains("ADD_ONION successful") } && System.currentTimeMillis() < logEnd) Thread.sleep(200)
            val torLines = supervisor.output.snapshot().filter { it.contains("[tor]") }
            assertTrue("no authentication: $torLines", torLines.any { it.contains("Authentication successful") })
            assertTrue("no onion service: $torLines", torLines.any { it.contains("ADD_ONION successful") })
            val local = (supervisor.rpc.call("getnetworkinfo") as Map<*, *>)["localaddresses"] as List<*>
            val onionAddress = local.map { it as Map<*, *> }.firstOrNull { it["address"].toString().endsWith(".onion") }
            assertTrue("no onion address in getnetworkinfo: $local", onionAddress != null)
        } finally {
            supervisor.stop()
            services.stopAll()
            root.deleteRecursively()
        }
    }
}
