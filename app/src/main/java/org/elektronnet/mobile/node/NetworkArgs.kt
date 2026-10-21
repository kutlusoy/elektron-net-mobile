package org.elektronnet.mobile.node

object NetworkArgs {
    const val LOOPBACK = "127.0.0.1"

    fun forNode(network: NetworkSettings, ports: NetworkPorts, p2pPort: Int, listen: Boolean): List<String> {
        val args = mutableListOf<String>()
        val tor = network.tor
        if (tor.enabled) {
            val socks = "$LOOPBACK:${ports.torSocks}"
            args.add("-torcontrol=$LOOPBACK:${ports.torControl}")
            args.add("-listenonion=${if (listen) 1 else 0}")
            args.add(if (tor.routing == TorRouting.AllTraffic) "-proxy=$socks" else "-onion=$socks")
            if (tor.routing == TorRouting.AllTraffic && listen) {
                args.add("-bind=$LOOPBACK:$p2pPort")
                args.add("-bind=$LOOPBACK:${p2pPort + 1}=onion")
            }
        } else {
            args.add("-listenonion=0")
        }
        val i2p = network.i2p
        if (i2p.enabled) {
            args.add("-i2psam=$LOOPBACK:${ports.i2pSam}")
            args.add("-i2pacceptincoming=${if (i2p.acceptIncoming && listen) 1 else 0}")
        }
        return args
    }
}
