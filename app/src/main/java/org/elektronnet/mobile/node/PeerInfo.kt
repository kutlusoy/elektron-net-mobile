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
                )
            }.sortedWith(compareBy<PeerInfo> { it.inbound }.thenBy { it.id })
        }
    }
}
