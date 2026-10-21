package org.elektronnet.mobile.ui.model

import org.elektronnet.mobile.node.NetworkSettings

enum class ConnNet(val key: String) {
    IPv4("ipv4"),
    IPv6("ipv6"),
    Tor("onion"),
    I2p("i2p"),
    Cjdns("cjdns"),
}

data class ConnChip(val net: ConnNet, val peers: Int)

object ConnectionModel {
    fun chips(peerNetworks: Map<String, Int>, network: NetworkSettings): List<ConnChip> =
        ConnNet.entries.mapNotNull { net ->
            val peers = peerNetworks[net.key] ?: 0
            val configured = (net == ConnNet.Tor && network.tor.enabled) || (net == ConnNet.I2p && network.i2p.enabled)
            if (peers > 0 || configured) ConnChip(net, peers) else null
        }

    fun count(peerNetworks: List<String>): Map<String, Int> = peerNetworks.filter { it.isNotEmpty() }.groupingBy { it }.eachCount()
}
