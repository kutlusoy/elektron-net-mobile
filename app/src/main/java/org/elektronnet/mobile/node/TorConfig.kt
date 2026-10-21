package org.elektronnet.mobile.node

import java.io.File

object TorConfig {
    fun torrc(settings: TorSettings, dataDir: File, ports: NetworkPorts, resolvConf: File? = null): String {
        val tor = settings.normalized()
        val lines = mutableListOf(
            "DataDirectory ${dataDir.absolutePath}",
            "SocksPort ${NetworkArgs.LOOPBACK}:${ports.torSocks}",
            "ControlPort ${NetworkArgs.LOOPBACK}:${ports.torControl}",
            "CookieAuthentication 1",
            "CookieAuthFile ${File(dataDir, "control_auth_cookie").absolutePath}",
            "AvoidDiskWrites 1",
            "Log notice stdout",
        )
        if (tor.role == TorRole.Client) {
            lines.add("ClientOnly 1")
            lines.add("ORPort 0")
            lines.add("ExitRelay 0")
            lines.add("ExitPolicy reject *:*")
        } else {
            lines.add("ORPort ${tor.orPort}")
            lines.add("PublishServerDescriptor 1")
            lines.add("RelayBandwidthRate ${tor.bandwidthKbps} KBytes")
            lines.add("RelayBandwidthBurst ${tor.bandwidthKbps * 2} KBytes")
            if (tor.nickname.isNotEmpty()) lines.add("Nickname ${tor.nickname}")
            if (tor.contact.isNotEmpty()) lines.add("ContactInfo ${tor.contact}")
            if (tor.monthlyLimitGb > 0) {
                lines.add("AccountingMax ${tor.monthlyLimitGb} GBytes")
                lines.add("AccountingStart month 1 00:00")
            }
            if (tor.role == TorRole.Exit) {
                lines.add("ExitRelay 1")
                lines.add("IPv6Exit 0")
                resolvConf?.let { lines.add("ServerDNSResolvConfFile ${it.absolutePath}") }
                when (tor.exitPolicy) {
                    ExitPolicyKind.Reduced -> lines.add("ReducedExitPolicy 1")
                    ExitPolicyKind.Web -> {
                        lines.add("ExitPolicy accept *:80")
                        lines.add("ExitPolicy accept *:443")
                        lines.add("ExitPolicy reject *:*")
                    }
                }
            } else {
                lines.add("ExitRelay 0")
                lines.add("ExitPolicy reject *:*")
            }
        }
        return lines.joinToString("\n", postfix = "\n")
    }

    fun arguments(torrc: File): List<String> = listOf("-f", torrc.absolutePath, "--ignore-missing-torrc")
}
