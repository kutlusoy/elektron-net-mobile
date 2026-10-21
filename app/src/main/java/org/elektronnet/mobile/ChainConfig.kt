package org.elektronnet.mobile

object ChainConfig {
    const val chain: String = BuildConfig.CHAIN
    const val nodeChainArg: String = BuildConfig.NODE_CHAIN_ARG
    const val datadirName: String = BuildConfig.DATADIR_NAME
    const val bech32Hrp: String = BuildConfig.BECH32_HRP
    const val p2pPort: Int = BuildConfig.P2P_PORT
    const val rpcPort: Int = BuildConfig.RPC_PORT
    const val isTestnet: Boolean = BuildConfig.IS_TESTNET
    const val ticker: String = "ELEK"
    val networkPorts: org.elektronnet.mobile.node.NetworkPorts = if (isTestnet) org.elektronnet.mobile.node.NetworkPorts.Testnet else org.elektronnet.mobile.node.NetworkPorts()
    val uriScheme: String = if (isTestnet) "elektest" else "elek"
}
