package org.elektronnet.mobile.ui.model

enum class NodeHealth { Ok, SlowStart, Silent, NoPeers }

object NodeHealthRules {
    const val SLOW_START_MS = 120_000L
    const val SILENT_MS = 120_000L
    const val NO_PEERS_MS = 60_000L

    fun assess(starting: Boolean, running: Boolean, elapsedMs: Long, silentMs: Long, peers: Long, noPeersForMs: Long): NodeHealth = when {
        (starting || running) && silentMs >= SILENT_MS -> NodeHealth.Silent
        starting && elapsedMs >= SLOW_START_MS -> NodeHealth.SlowStart
        running && peers == 0L && noPeersForMs >= NO_PEERS_MS -> NodeHealth.NoPeers
        else -> NodeHealth.Ok
    }

    fun clock(ms: Long): String {
        val total = ms / 1000
        return "%d:%02d".format(total / 60, total % 60)
    }
}
