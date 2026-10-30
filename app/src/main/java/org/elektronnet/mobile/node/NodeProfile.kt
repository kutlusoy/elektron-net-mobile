package org.elektronnet.mobile.node

enum class NodeProfile(val args: List<String>) {
    Foreground(listOf("-dbcache=64", "-maxconnections=8", "-maxmempool=30", "-blocksonly=1")),
    Balanced(listOf("-dbcache=128", "-maxconnections=125", "-maxmempool=50")),
    AlwaysOn(listOf("-dbcache=256", "-maxconnections=24", "-maxmempool=100")),
}

enum class Chain(val buildName: String, val arg: String?, val section: String?, val subdir: String) {
    Main("main", null, null, ""),
    Testnet("testnet", "-testnet", "test", "testnet3"),
    Regtest("regtest", "-regtest", "regtest", "regtest"),
    ;

    companion object {
        fun fromBuildName(name: String): Chain =
            entries.firstOrNull { it.buildName == name } ?: throw IllegalArgumentException("unknown chain $name")
    }
}
