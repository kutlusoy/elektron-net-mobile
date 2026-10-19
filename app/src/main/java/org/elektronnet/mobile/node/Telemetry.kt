package org.elektronnet.mobile.node

data class Telemetry(
    val difficulty: Double?,
    val hashPerSecond: Double?,
    val mempoolTx: Long?,
    val mempoolBytes: Long?,
    val sizeOnDisk: Long?,
) {
    companion object {
        fun parse(chain: Any?, mining: Any?, mempool: Any?): Telemetry {
            val c = chain as? Map<*, *>
            val m = mining as? Map<*, *>
            val p = mempool as? Map<*, *>
            return Telemetry(
                difficulty = (c?.get("difficulty") as? Number)?.toDouble() ?: (m?.get("difficulty") as? Number)?.toDouble(),
                hashPerSecond = (m?.get("networkhashps") as? Number)?.toDouble(),
                mempoolTx = (p?.get("size") as? Number)?.toLong(),
                mempoolBytes = (p?.get("bytes") as? Number)?.toLong(),
                sizeOnDisk = (c?.get("size_on_disk") as? Number)?.toLong(),
            )
        }
    }
}
