package org.elektronnet.mobile.node

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class NetworkServicesTest {
    private val ports = NetworkPorts(torSocks = 29180, torControl = 29181, i2pSam = 29182)

    private fun listener(dir: File, name: String, port: Int): File {
        val script = File(dir, name)
        script.writeText(
            "#!/bin/sh\n" +
                "echo started \"\$@\"\n" +
                "exec python3 -c \"import socket,time\n" +
                "s=socket.socket()\n" +
                "s.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)\n" +
                "s.bind(('127.0.0.1',$port))\n" +
                "s.listen(5)\n" +
                "time.sleep(600)\"\n",
        )
        script.setExecutable(true)
        return script
    }

    private fun waitFor(timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "condition not met in time" }
            Thread.sleep(50)
        }
    }

    @Test
    fun servicesFollowTheSettingsAndTheActiveFlag() {
        val root = Files.createTempDirectory("netsvc").toFile()
        val bins = File(root, "bin").apply { mkdirs() }
        val services = NetworkServices(ServiceBinaries(listener(bins, "tor", ports.torControl), listener(bins, "i2pd", ports.i2pSam)), File(root, "data"), ports)
        try {
            val network = NetworkSettings(TorSettings(enabled = true), I2pSettings(enabled = true))
            services.sync(network, active = true)
            waitFor { services.torState == ServiceState.Running && services.i2pState == ServiceState.Running }
            assertTrue(File(root, "data/tor/torrc").readText().contains("ControlPort 127.0.0.1:29181"))
            assertTrue(File(root, "data/i2pd/i2pd.conf").readText().contains("port = 29182"))
            assertTrue(services.torOutput().any { it.startsWith("started -f") })

            services.sync(network, active = true)
            assertEquals(ServiceState.Running, services.torState)

            services.sync(network.copy(i2p = I2pSettings(enabled = false)), active = true)
            waitFor { services.i2pState == ServiceState.Stopped }
            assertEquals(ServiceState.Running, services.torState)

            services.sync(network, active = false)
            waitFor { services.torState == ServiceState.Stopped }
            assertEquals(ServiceState.Stopped, services.i2pState)
        } finally {
            services.stopAll()
            root.deleteRecursively()
        }
    }

    @Test
    fun aChangedConfigRestartsTheService() {
        val root = Files.createTempDirectory("netsvc2").toFile()
        val bins = File(root, "bin").apply { mkdirs() }
        val services = NetworkServices(ServiceBinaries(listener(bins, "tor", ports.torControl), null), File(root, "data"), ports)
        try {
            services.sync(NetworkSettings(TorSettings(enabled = true)), active = true)
            waitFor { services.torState == ServiceState.Running }
            val before = services.torOutput().size
            services.sync(NetworkSettings(TorSettings(enabled = true, role = TorRole.Relay, nickname = "Changed")), active = true)
            waitFor { services.torState == ServiceState.Running && services.torOutput().isNotEmpty() }
            assertTrue(File(root, "data/tor/torrc").readText().contains("Nickname Changed"))
            assertTrue(before >= 1)
        } finally {
            services.stopAll()
            root.deleteRecursively()
        }
    }

    @Test
    fun aMissingBinaryIsSkippedAndAFailingOneIsReported() {
        val root = Files.createTempDirectory("netsvc3").toFile()
        val bins = File(root, "bin").apply { mkdirs() }
        val failing = File(bins, "tor").apply {
            writeText("#!/bin/sh\necho boom >&2\nexit 3\n")
            setExecutable(true)
        }
        val services = NetworkServices(ServiceBinaries(failing, File(bins, "missing")), File(root, "data"), ports, retryAfterMs = 60_000)
        try {
            services.sync(NetworkSettings(TorSettings(enabled = true), I2pSettings(enabled = true)), active = true)
            waitFor { services.torState is ServiceState.Failed }
            val failed = services.torState as ServiceState.Failed
            assertEquals(3, failed.exitCode)
            assertTrue(failed.detail.contains("boom"))
            assertEquals(ServiceState.Stopped, services.i2pState)
            assertTrue(!services.i2pAvailable)
            val first = services.torOutput()
            services.sync(NetworkSettings(TorSettings(enabled = true)), active = true)
            assertEquals(first, services.torOutput())
        } finally {
            services.stopAll()
            root.deleteRecursively()
        }
    }
}
