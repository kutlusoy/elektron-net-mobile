package org.elektronnet.mobile.node

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class NetworkConfigTest {
    private val ports = NetworkPorts()
    private val dir = File("/data/tor")

    @Test
    fun nodeArgumentsWithoutTorOrI2pTurnOnionListeningOff() {
        val args = NetworkArgs.forNode(NetworkSettings(), ports, 8333, listen = true)
        assertEquals(listOf("-listenonion=0"), args)
    }

    @Test
    fun torAdditionalUsesTheProxyForOnionPeersOnly() {
        val network = NetworkSettings(tor = TorSettings(enabled = true))
        val args = NetworkArgs.forNode(network, ports, 8333, listen = true)
        assertTrue("-onion=127.0.0.1:9050" in args)
        assertTrue("-torcontrol=127.0.0.1:9051" in args)
        assertTrue("-listenonion=1" in args)
        assertTrue("-debug=tor" in args)
        assertTrue(args.none { it.startsWith("-proxy=") || it.startsWith("-bind=") })
        assertTrue("-listenonion=0" in NetworkArgs.forNode(network, ports, 8333, listen = false))
    }

    @Test
    fun torForAllTrafficProxiesEverythingAndBindsOnlyLoopback() {
        val network = NetworkSettings(tor = TorSettings(enabled = true, routing = TorRouting.AllTraffic))
        val listening = NetworkArgs.forNode(network, ports, 8333, listen = true)
        assertTrue("-proxy=127.0.0.1:9050" in listening)
        assertTrue("-bind=127.0.0.1:8333" in listening)
        assertTrue("-bind=127.0.0.1:8334=onion" in listening)
        val quiet = NetworkArgs.forNode(network, ports, 8333, listen = false)
        assertTrue(quiet.none { it.startsWith("-bind=") })
    }

    @Test
    fun i2pArgumentsPointAtTheSamBridge() {
        val network = NetworkSettings(i2p = I2pSettings(enabled = true))
        assertTrue("-i2psam=127.0.0.1:7656" in NetworkArgs.forNode(network, ports, 8333, listen = true))
        assertTrue("-debug=i2p" in NetworkArgs.forNode(network, ports, 8333, listen = true))
        assertTrue("-i2pacceptincoming=1" in NetworkArgs.forNode(network, ports, 8333, listen = true))
        assertTrue("-i2pacceptincoming=0" in NetworkArgs.forNode(network, ports, 8333, listen = false))
        val refusing = NetworkSettings(i2p = I2pSettings(enabled = true, acceptIncoming = false))
        assertTrue("-i2pacceptincoming=0" in NetworkArgs.forNode(refusing, ports, 8333, listen = true))
    }

    @Test
    fun testnetUsesItsOwnPorts() {
        val args = NetworkArgs.forNode(NetworkSettings(TorSettings(enabled = true), I2pSettings(enabled = true)), NetworkPorts.Testnet, 18333, listen = false)
        assertTrue("-torcontrol=127.0.0.1:19051" in args)
        assertTrue("-onion=127.0.0.1:19050" in args)
        assertTrue("-i2psam=127.0.0.1:17656" in args)
    }

    @Test
    fun clientTorrcNeverRelaysOrExits() {
        val text = TorConfig.torrc(TorSettings(enabled = true), dir, ports)
        assertTrue("SocksPort 127.0.0.1:9050" in text)
        assertTrue("ControlPort 127.0.0.1:9051" in text)
        assertTrue("CookieAuthentication 1" in text)
        assertTrue("ClientOnly 1" in text)
        assertTrue("ORPort 0" in text)
        assertFalse("RelayBandwidthRate" in text)
        assertTrue("ExitPolicy reject *:*" in text)
    }

    @Test
    fun relayTorrcCarriesTheRelaySettings() {
        val settings = TorSettings(
            enabled = true,
            role = TorRole.Relay,
            orPort = 9101,
            nickname = "Elektron Mobile 1!",
            contact = "ali@example.org\nExitRelay 1",
            bandwidthKbps = 512,
            monthlyLimitGb = 50,
        )
        val text = TorConfig.torrc(settings, dir, ports)
        assertTrue("ORPort 9101" in text)
        assertTrue("Nickname ElektronMobile1" in text)
        assertTrue("ContactInfo ali@example.orgExitRelay 1" in text)
        assertEquals(1, text.lines().count { it.startsWith("ExitRelay") })
        assertTrue("ExitRelay 0" in text)
        assertTrue("RelayBandwidthRate 512 KBytes" in text)
        assertTrue("RelayBandwidthBurst 1024 KBytes" in text)
        assertTrue("AccountingMax 50 GBytes" in text)
        assertFalse("ClientOnly" in text)
    }

    @Test
    fun exitTorrcAppliesTheChosenExitPolicy() {
        val reduced = TorConfig.torrc(TorSettings(enabled = true, role = TorRole.Exit), dir, ports, File("/data/tor/resolv.conf"))
        assertTrue("ExitRelay 1" in reduced)
        assertTrue("ReducedExitPolicy 1" in reduced)
        assertTrue("ServerDNSResolvConfFile /data/tor/resolv.conf" in reduced)
        val web = TorConfig.torrc(TorSettings(enabled = true, role = TorRole.Exit, exitPolicy = ExitPolicyKind.Web), dir, ports)
        assertTrue("ExitPolicy accept *:80" in web)
        assertTrue("ExitPolicy accept *:443" in web)
        assertFalse("ReducedExitPolicy" in web)
    }

    @Test
    fun valuesAreClamped() {
        val tor = TorSettings(orPort = 80, bandwidthKbps = 1, monthlyLimitGb = -5).normalized()
        assertEquals(1024, tor.orPort)
        assertEquals(TorSettings.MIN_BANDWIDTH_KBPS, tor.bandwidthKbps)
        assertEquals(0, tor.monthlyLimitGb)
        val i2p = I2pSettings(bandwidthKbps = 99999, sharePercent = 400).normalized()
        assertEquals(I2pSettings.MAX_BANDWIDTH_KBPS, i2p.bandwidthKbps)
        assertEquals(100, i2p.sharePercent)
    }

    @Test
    fun i2pConfigEnablesOnlyTheSamBridge() {
        val text = I2pConfig.conf(I2pSettings(enabled = true, transit = true, bandwidthKbps = 300, sharePercent = 40), File("/data/i2pd"), ports)
        assertTrue("[sam]\nenabled = true\naddress = 127.0.0.1\nport = 7656" in text)
        assertTrue("notransit = false" in text)
        assertTrue("bandwidth = 300" in text)
        assertTrue("share = 40" in text)
        assertTrue("[http]\nenabled = false" in text)
        assertTrue("notransit = true" in I2pConfig.conf(I2pSettings(enabled = true), File("/data/i2pd"), ports))
    }

    @Test
    fun theRealTorAcceptsEveryGeneratedConfig() {
        val binary = System.getenv("TOR_BIN")
        assumeTrue("TOR_BIN not set", binary != null && File(binary).canExecute())
        val root = Files.createTempDirectory("torrc-it").toFile()
        try {
            val variants = listOf(
                TorSettings(enabled = true),
                TorSettings(enabled = true, role = TorRole.Relay, nickname = "ElektronTest", contact = "dev@example.org", monthlyLimitGb = 20),
                TorSettings(enabled = true, role = TorRole.Exit),
                TorSettings(enabled = true, role = TorRole.Exit, exitPolicy = ExitPolicyKind.Web, bandwidthKbps = 1024),
            )
            variants.forEachIndexed { index, settings ->
                val data = File(root, "d$index").apply { mkdirs() }
                File(data, "resolv.conf").writeText("nameserver 9.9.9.9\n")
                val torrc = File(root, "torrc$index").apply { writeText(TorConfig.torrc(settings, data, ports, File(data, "resolv.conf"))) }
                val process = ProcessBuilder(binary, "-f", torrc.path, "--ignore-missing-torrc", "--verify-config").redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                assertTrue(process.waitFor(30, TimeUnit.SECONDS))
                assertEquals("variant $index: $output", 0, process.exitValue())
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun theRealI2pdAcceptsTheGeneratedConfig() {
        val binary = System.getenv("I2PD_BIN")
        assumeTrue("I2PD_BIN not set", binary != null && File(binary).canExecute())
        val root = Files.createTempDirectory("i2pd-it").toFile()
        try {
            val settings = I2pSettings(enabled = true, transit = true, bandwidthKbps = 512, sharePercent = 30)
            val conf = File(root, "i2pd.conf").apply { writeText(I2pConfig.conf(settings, root, NetworkPorts(i2pSam = 29210))) }
            val process = ProcessBuilder(listOf(binary!!) + I2pConfig.arguments(conf, root)).redirectErrorStream(true).start()
            val lines = mutableListOf<String>()
            Thread { process.inputStream.bufferedReader().forEachLine { synchronized(lines) { lines.add(it) } } }.apply { isDaemon = true }.start()
            Thread.sleep(4_000)
            val alive = process.isAlive
            process.destroy()
            process.waitFor(10, TimeUnit.SECONDS)
            val log = synchronized(lines) { lines.joinToString("\n") }
            assertTrue("i2pd exited early: $log", alive)
            assertTrue("config problem: $log", !(log.contains("unrecognised") || log.contains("Failed to parse") || log.contains("can not parse")))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun busyPortsMoveToTheNextFreeOne() {
        val busy = setOf(9050, 9051, 9060, 7656)
        val resolved = NetworkPorts().resolved { it !in busy }
        assertEquals(NetworkPorts(torSocks = 9070, torControl = 9061, i2pSam = 7666), resolved)
        assertEquals(NetworkPorts(), NetworkPorts().resolved { true })
    }

    @Test
    fun resolvedPortsAreDistinctAndTheNodeAcceptsThem() {
        val resolved = NetworkPorts(torSocks = 9050, torControl = 9050, i2pSam = 9050).resolved { true }
        assertEquals(3, setOf(resolved.torSocks, resolved.torControl, resolved.i2pSam).size)
        val held = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName(NetworkArgs.LOOPBACK))
        try {
            assertFalse(NetworkPorts.loopbackFree(held.localPort))
        } finally {
            held.close()
        }
        assertTrue(NetworkPorts.loopbackFree(held.localPort))
    }
}
