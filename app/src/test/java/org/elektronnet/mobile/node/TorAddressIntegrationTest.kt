package org.elektronnet.mobile.node

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class TorAddressIntegrationTest {
    private fun onionAddress(supervisor: NodeSupervisor): String {
        val end = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < end) {
            val local = runCatching { (supervisor.rpc.call("getnetworkinfo") as Map<*, *>)["localaddresses"] as List<*> }.getOrNull().orEmpty()
            local.map { it as Map<*, *> }.firstOrNull { it["address"].toString().endsWith(".onion") }?.let { return it["address"].toString() }
            Thread.sleep(300)
        }
        throw AssertionError("no onion address: ${supervisor.output.tail(10)}")
    }

    private fun run(config: NodeConfig): String {
        val supervisor = NodeSupervisor(config, pollIntervalMs = 100)
        supervisor.start()
        try {
            val end = System.currentTimeMillis() + 60_000
            while (supervisor.state != NodeState.Running) {
                check(System.currentTimeMillis() < end) { "node did not start" }
                Thread.sleep(100)
            }
            return onionAddress(supervisor)
        } finally {
            supervisor.stop()
        }
    }

    @Test
    fun theOnionAddressStaysUntilTheKeyIsRenewed() {
        val node = System.getenv("ELEKTROND_BIN")
        val tor = System.getenv("TOR_BIN")
        assumeTrue("ELEKTROND_BIN or TOR_BIN not set", node != null && File(node).canExecute() && tor != null && File(tor).canExecute())
        val root = Files.createTempDirectory("toraddr").toFile()
        val ports = NetworkPorts(torSocks = 29240, torControl = 29241, i2pSam = 29242)
        val services = NetworkServices(ServiceBinaries(File(tor!!), null), File(root, "svc"), ports)
        val network = NetworkSettings(tor = TorSettings(enabled = true))
        val config = NodeConfig(File(node!!), File(root, "node"), Chain.Regtest, rpcPort = 29243, p2pPort = 29244, listen = true, network = network, ports = ports)
        try {
            services.sync(network, active = true)
            val end = System.currentTimeMillis() + 60_000
            while (services.torState != ServiceState.Running) {
                check(System.currentTimeMillis() < end) { "tor did not start" }
                Thread.sleep(100)
            }
            val keys = AddressKeys(File(root, "node/regtest"))
            val first = run(config)
            assertTrue(keys.exists(AddressKind.Onion))
            assertEquals(first, run(config))
            assertEquals(first, run(config))
            assertTrue(keys.renew(AddressKind.Onion))
            val renewed = run(config)
            assertNotEquals(first, renewed)
            assertEquals(renewed, run(config))
        } finally {
            services.stopAll()
            root.deleteRecursively()
        }
    }
}
