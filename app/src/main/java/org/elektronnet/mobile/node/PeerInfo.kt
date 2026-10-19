package org.elektronnet.mobile.node

data class PeerInfo(
    val id: Long,
    val address: String,
    val inbound: Boolean,
    val network: String,
    val subversion: String,
    val pingMs: Double?,
    val startingHeight: Long?,
    val connectionType: String,
    val transport: String = "",
    val version: Long? = null,
    val syncedHeaders: Long? = null,
    val syncedBlocks: Long? = null,
    val minPingMs: Double? = null,
    val bytesRecv: Long? = null,
    val bytesSent: Long? = null,
    val timeOffset: Long? = null,
    val localBind: String = "",
) {
    companion object {
        fun parse(result: Any?): List<PeerInfo> {
            val list = result as? List<*> ?: return emptyList()
            return list.mapNotNull { entry ->
                val peer = entry as? Map<*, *> ?: return@mapNotNull null
                PeerInfo(
                    id = (peer["id"] as? Number)?.toLong() ?: return@mapNotNull null,
                    address = peer["addr"] as? String ?: return@mapNotNull null,
                    inbound = peer["inbound"] as? Boolean ?: false,
                    network = peer["network"] as? String ?: "",
                    subversion = peer["subver"] as? String ?: "",
                    pingMs = (peer["pingtime"] as? Number)?.toDouble()?.times(1000.0),
                    startingHeight = (peer["startingheight"] as? Number)?.toLong(),
                    connectionType = peer["connection_type"] as? String ?: "",
                    transport = peer["transport_protocol_type"] as? String ?: "",
                    version = (peer["version"] as? Number)?.toLong(),
                    syncedHeaders = (peer["synced_headers"] as? Number)?.toLong(),
                    syncedBlocks = (peer["synced_blocks"] as? Number)?.toLong(),
                    minPingMs = (peer["minping"] as? Number)?.toDouble()?.times(1000.0),
                    bytesRecv = (peer["bytesrecv"] as? Number)?.toLong(),
                    bytesSent = (peer["bytessent"] as? Number)?.toLong(),
                    timeOffset = (peer["timeoffset"] as? Number)?.toLong(),
                    localBind = peer["addrbind"] as? String ?: "",
                )
            }.sortedWith(compareBy<PeerInfo> { it.inbound }.thenBy { it.id })
        }
    }
}
