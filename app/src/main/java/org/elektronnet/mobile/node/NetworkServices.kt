package org.elektronnet.mobile.node

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class ServiceBinaries(val tor: File?, val i2pd: File?)

class NetworkServices(
    private val binaries: ServiceBinaries,
    private val root: File,
    private val ports: NetworkPorts,
    private val dnsServers: () -> List<String> = { emptyList() },
    private val prepareI2pData: (File) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val retryAfterMs: Long = 30_000,
) {
    private class Running(val service: ChildService, val config: String, val startedAt: Long)

    private var tor: Running? = null
    private var i2p: Running? = null

    val torDir: File get() = File(root, "tor")
    val i2pDir: File get() = File(root, "i2pd")

    val torState: ServiceState @Synchronized get() = tor?.service?.state ?: ServiceState.Stopped
    val i2pState: ServiceState @Synchronized get() = i2p?.service?.state ?: ServiceState.Stopped
    val torAvailable: Boolean get() = binaries.tor?.canExecute() == true
    val i2pAvailable: Boolean get() = binaries.i2pd?.canExecute() == true

    fun torOutput(): List<String> = tor?.service?.output?.snapshot().orEmpty()

    fun i2pOutput(): List<String> = i2p?.service?.output?.snapshot().orEmpty()

    fun clearTorOutput() {
        tor?.service?.output?.clear()
    }

    fun clearI2pOutput() {
        i2p?.service?.output?.clear()
    }

    @Synchronized
    fun sync(network: NetworkSettings, active: Boolean) {
        val settings = network.normalized()
        val now = clock()
        val torBinary = binaries.tor
        tor = reconcile(tor, active && settings.tor.enabled && torBinary?.canExecute() == true, now) {
            privateDir(torDir)
            val resolv = File(torDir, "resolv.conf").takeIf { settings.tor.role != TorRole.Client }?.also { writeResolv(it) }
            val torrc = TorConfig.torrc(settings.tor, torDir, ports, resolv)
            val file = File(torDir, "torrc").also { writeIfChanged(it, torrc) }
            torrc to ChildService("tor", listOf(torBinary!!.absolutePath) + TorConfig.arguments(file), torDir, ports.torControl)
        }
        val i2pBinary = binaries.i2pd
        i2p = reconcile(i2p, active && settings.i2p.enabled && i2pBinary?.canExecute() == true, now) {
            privateDir(i2pDir)
            prepareI2pData(i2pDir)
            val conf = I2pConfig.conf(settings.i2p, i2pDir, ports)
            val file = File(i2pDir, "i2pd.conf").also { writeIfChanged(it, conf) }
            conf to ChildService("i2pd", listOf(i2pBinary!!.absolutePath) + I2pConfig.arguments(file, i2pDir), i2pDir, ports.i2pSam)
        }
    }

    @Synchronized
    fun stopAll() {
        tor?.service?.stop()
        i2p?.service?.stop()
        tor = null
        i2p = null
    }

    private fun reconcile(current: Running?, wanted: Boolean, now: Long, build: () -> Pair<String, ChildService>): Running? {
        if (!wanted) {
            current?.service?.stop()
            return null
        }
        val (config, service) = build()
        if (current != null) {
            val state = current.service.state
            val alive = state == ServiceState.Starting || state == ServiceState.Running
            if (alive && config == current.config) return current
            if (alive) current.service.stop()
            else if (config == current.config && now - current.startedAt < retryAfterMs) return current
        }
        service.start()
        return Running(service, config, now)
    }

    private fun writeIfChanged(file: File, text: String) {
        if (!file.exists() || file.readText() != text) file.writeText(text)
    }

    private fun privateDir(dir: File) {
        dir.mkdirs()
        runCatching { Files.setPosixFilePermissions(dir.toPath(), PosixFilePermissions.fromString("rwx------")) }
    }

    private fun writeResolv(file: File) {
        val servers = dnsServers().ifEmpty { FALLBACK_DNS }
        file.writeText(servers.joinToString("\n", postfix = "\n") { "nameserver $it" })
    }

    private companion object {
        val FALLBACK_DNS = listOf("9.9.9.9", "149.112.112.112")
    }
}
