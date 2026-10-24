package org.elektronnet.mobile.ui.model

import org.elektronnet.mobile.node.NetworkSettings

enum class ConnNet(val key: String, val label: String) {
    IPv4("ipv4", "IPv4"),
    IPv6("ipv6", "IPv6"),
    Tor("onion", "Tor"),
    I2p("i2p", "I2P"),
    Cjdns("cjdns", "CJDNS"),
}

data class ConnChip(val net: ConnNet, val peers: Int)

object ConnectionModel {
    fun chips(peerNetworks: Map<String, Int>, network: NetworkSettings): List<ConnChip> =
        ConnNet.entries.mapNotNull { net ->
            val peers = peerNetworks[net.key] ?: 0
            val configured = (net == ConnNet.Tor && network.tor.enabled) || (net == ConnNet.I2p && network.i2p.enabled)
            if (peers > 0 || configured) ConnChip(net, peers) else null
        }

    // for example "IPv4 9 · Tor 2 · I2P 1", empty when no peer is connected
    fun breakdown(peerNetworks: Map<String, Int>): String =
        ConnNet.entries.mapNotNull { net -> (peerNetworks[net.key] ?: 0).takeIf { it > 0 }?.let { "${net.label}: $it" } }.joinToString(" \u00b7 ")

    fun count(peerNetworks: List<String>): Map<String, Int> = peerNetworks.filter { it.isNotEmpty() }.groupingBy { it }.eachCount()
}
