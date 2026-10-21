package org.elektronnet.mobile.node

data class LocalAddress(val address: String, val port: Int) {
    val isOnion: Boolean get() = address.endsWith(".onion")
    val isI2p: Boolean get() = address.endsWith(".i2p")
    fun display(): String = if (port > 0) "$address:$port" else address
}

data class Telemetry(
    val difficulty: Double?,
    val hashPerSecond: Double?,
    val mempoolTx: Long?,
    val mempoolBytes: Long?,
    val sizeOnDisk: Long?,
    val protocolVersion: Long? = null,
    val connectionsIn: Long? = null,
    val connectionsOut: Long? = null,
    val networkActive: Boolean? = null,
    val initialBlockDownload: Boolean? = null,
    val schedule: ChainSchedule? = null,
    val localAddresses: List<LocalAddress> = emptyList(),
) {
    companion object {
        fun parse(chain: Any?, mining: Any?, mempool: Any?, network: Any? = null): Telemetry {
            val c = chain as? Map<*, *>
            val m = mining as? Map<*, *>
            val p = mempool as? Map<*, *>
            val n = network as? Map<*, *>
            return Telemetry(
                difficulty = (c?.get("difficulty") as? Number)?.toDouble() ?: (m?.get("difficulty") as? Number)?.toDouble(),
                hashPerSecond = (m?.get("networkhashps") as? Number)?.toDouble(),
                mempoolTx = (p?.get("size") as? Number)?.toLong(),
                mempoolBytes = (p?.get("bytes") as? Number)?.toLong(),
                sizeOnDisk = (c?.get("size_on_disk") as? Number)?.toLong(),
                protocolVersion = (n?.get("protocolversion") as? Number)?.toLong(),
                connectionsIn = (n?.get("connections_in") as? Number)?.toLong(),
                connectionsOut = (n?.get("connections_out") as? Number)?.toLong(),
                networkActive = n?.get("networkactive") as? Boolean,
                initialBlockDownload = c?.get("initialblockdownload") as? Boolean,
                localAddresses = (n?.get("localaddresses") as? List<*>).orEmpty().mapNotNull { entry ->
                    val map = entry as? Map<*, *> ?: return@mapNotNull null
                    LocalAddress(map["address"] as? String ?: return@mapNotNull null, (map["port"] as? Number)?.toInt() ?: 0)
                },
            )
        }
    }
}
