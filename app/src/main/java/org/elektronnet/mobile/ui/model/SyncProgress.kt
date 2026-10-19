package org.elektronnet.mobile.ui.model

enum class NodePhase { LoadingIndex, VerifyingBlocks, LoadingWallet, StartingNetwork, SyncingHeaders, Other }

object NodeLogPhase {
    private val rules = listOf(
        "Verifying blocks" to NodePhase.VerifyingBlocks,
        "Loading block index" to NodePhase.LoadingIndex,
        "Opening LevelDB" to NodePhase.LoadingIndex,
        "Loading wallet" to NodePhase.LoadingWallet,
        "Wallet completed loading" to NodePhase.LoadingWallet,
        "Starting network threads" to NodePhase.StartingNetwork,
        "net thread start" to NodePhase.StartingNetwork,
        "headers" to NodePhase.SyncingHeaders,
    )

    fun of(lines: List<String>): NodePhase {
        for (line in lines.asReversed().take(40)) {
            rules.firstOrNull { line.contains(it.first, ignoreCase = true) }?.let { return it.second }
        }
        return NodePhase.Other
    }
}

class SyncEta(private val windowMs: Long = 5 * 60_000L) {
    private val samples = ArrayDeque<Pair<Long, Long>>()

    fun add(nowMs: Long, blocks: Long) {
        samples.addLast(nowMs to blocks)
        while (samples.size > 2 && nowMs - samples.first().first > windowMs) samples.removeFirst()
    }

    fun remainingMs(remainingBlocks: Long): Long? {
        if (remainingBlocks <= 0 || samples.size < 2) return null
        val (t0, b0) = samples.first()
        val (t1, b1) = samples.last()
        if (t1 <= t0 || b1 <= b0) return null
        val perMs = (b1 - b0).toDouble() / (t1 - t0)
        return (remainingBlocks / perMs).toLong()
    }
}
