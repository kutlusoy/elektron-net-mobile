package org.elektronnet.mobile.node

import java.io.File

object I2pConfig {
    fun conf(settings: I2pSettings, dataDir: File, ports: NetworkPorts): String {
        val i2p = settings.normalized()
        return buildString {
            appendLine("daemon = false")
            appendLine("service = false")
            appendLine("log = stdout")
            appendLine("loglevel = info")
            appendLine("ipv4 = true")
            appendLine("ipv6 = true")
            appendLine("notransit = ${!i2p.transit}")
            appendLine("floodfill = false")
            appendLine("bandwidth = ${i2p.bandwidthKbps}")
            appendLine("share = ${i2p.sharePercent}")
            appendLine("[sam]")
            appendLine("enabled = true")
            appendLine("address = ${NetworkArgs.LOOPBACK}")
            appendLine("port = ${ports.i2pSam}")
            for (section in DISABLED) {
                appendLine("[$section]")
                appendLine("enabled = false")
            }
            appendLine("[reseed]")
            appendLine("verify = true")
        }
    }

    fun arguments(conf: File, dataDir: File): List<String> = listOf("--conf=${conf.absolutePath}", "--datadir=${dataDir.absolutePath}")

    private val DISABLED = listOf("http", "httpproxy", "socksproxy", "bob", "i2cp", "i2pcontrol", "upnp")
}
