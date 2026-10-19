package org.elektronnet.mobile.node

sealed interface NodeState {
    data object Stopped : NodeState
    data object Starting : NodeState
    data object Running : NodeState
    data object Stopping : NodeState
    data class Crashed(val exitCode: Int, val failure: NodeFailure, val detail: String = "") : NodeState
}

data class NodeStatus(
    val chain: String,
    val blocks: Long,
    val headers: Long,
    val verificationProgress: Double,
    val initialBlockDownload: Boolean,
    val pruned: Boolean,
    val pruneHeight: Long?,
    val connections: Long,
    val subversion: String,
)
