package org.elektronnet.mobile.runtime

import org.elektronnet.mobile.node.NodeFailure
import org.elektronnet.mobile.node.NodeState
import org.elektronnet.mobile.node.NodeStatus
import org.elektronnet.mobile.policy.PolicyDecision
import org.elektronnet.mobile.policy.PolicyReason

sealed interface Headline {
    data object Stopped : Headline
    data object Starting : Headline
    data object Stopping : Headline
    data class Paused(val reasons: Set<PolicyReason>) : Headline
    data class Syncing(val blocks: Long, val headers: Long, val progress: Double, val peers: Long) : Headline
    data class Synchronized(val blocks: Long, val peers: Long) : Headline
    data class Connecting(val reasons: Set<PolicyReason>) : Headline
    data class Crashed(val failure: NodeFailure) : Headline

    companion object {
        fun from(decision: PolicyDecision, state: NodeState?, status: NodeStatus?): Headline = when {
            state is NodeState.Crashed -> Crashed(state.failure)
            state == NodeState.Stopping -> Stopping
            !decision.run || state == null || state == NodeState.Stopped -> Stopped
            state == NodeState.Starting -> Starting
            !decision.networkActive -> Paused(decision.reasons)
            status == null -> Connecting(decision.reasons)
            !status.initialBlockDownload && status.blocks >= status.headers - 1 -> Synchronized(status.blocks, status.connections)
            else -> Syncing(status.blocks, status.headers, status.verificationProgress, status.connections)
        }
    }
}
