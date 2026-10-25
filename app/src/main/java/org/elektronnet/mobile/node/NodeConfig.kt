package org.elektronnet.mobile.node

import java.io.File

data class NodeConfig(
    val binary: File,
    val datadir: File,
    val chain: Chain,
    val rpcPort: Int,
    val p2pPort: Int,
    val profile: NodeProfile = NodeProfile.Balanced,
    val fastprune: Boolean = true,
    val listen: Boolean = false,
    val networkActive: Boolean = true,
    val extraArgs: List<String> = emptyList(),
    val homeDir: File? = null,
    val network: NetworkSettings = NetworkSettings(),
    val ports: NetworkPorts = NetworkPorts(),
) {
    val cookieFile: File get() = File(datadir, ".rpc-cookie")
    val confFile: File get() = File(datadir, "bitcoin.conf")

    fun networkArgs(): List<String> = NetworkArgs.forNode(network.normalized(), ports, p2pPort, listen)

    fun arguments(): List<String> {
        val args = mutableListOf(
            "-datadir=${datadir.absolutePath}",
            "-server=1",
            "-rpcbind=127.0.0.1",
            "-rpcallowip=127.0.0.1",
            "-rpcport=$rpcPort",
            "-rpccookiefile=${cookieFile.absolutePath}",
            "-rpcpassword=",
            "-port=$p2pPort",
            "-printtoconsole=1",
            "-natpmp=0",
            "-listen=${if (listen) 1 else 0}",
            "-networkactive=${if (networkActive) 1 else 0}",
        )
        chain.arg?.let { args.add(it) }
        args.addAll(profile.args)
        if (fastprune) args.add("-fastprune")
        args.addAll(networkArgs())
        network.activePeers().forEach { args.add("-addnode=$it") }
        args.addAll(extraArgs.filterNot { protectedOption(it) })
        return args
    }

    companion object {
        private val PROTECTED = setOf("server", "datadir", "conf", "bind", "whitebind", "whitelist", "rest")

        // options that decide who can reach the node must not be set or reset from the extra arguments, also not in the form -noname
        fun protectedOption(arg: String): Boolean {
            val name = arg.trimStart('-').substringBefore('=').lowercase()
            val base = if (name.startsWith("no")) name.removePrefix("no") else name
            return listOf(name, base).any { it.startsWith("rpc") || it.startsWith("zmq") || it in PROTECTED }
        }
    }
}
