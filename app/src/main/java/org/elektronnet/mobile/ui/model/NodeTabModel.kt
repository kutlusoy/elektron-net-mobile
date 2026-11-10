package org.elektronnet.mobile.ui.model

import org.elektronnet.mobile.node.NodeFailure
import org.elektronnet.mobile.node.NodeState
import org.elektronnet.mobile.policy.PolicyReason
import org.elektronnet.mobile.runtime.Headline
import org.elektronnet.mobile.runtime.RuntimeSnapshot

enum class NodeTabState { UpToDate, CatchingUp, ScanningWallet, Paused, Starting, Stopping, Stopped, Problem }

data class NodeDetails(
    val chain: String,
    val blocks: Long,
    val headers: Long,
    val peers: Long,
    val pruneHeight: Long?,
    val version: String,
)

data class NodeTabModel(
    val state: NodeTabState,
    val percent: Int?,
    val reason: PolicyReason?,
    val failure: NodeFailure?,
    val canPause: Boolean,
    val canResume: Boolean,
    val canStop: Boolean,
    val canStart: Boolean,
    val details: NodeDetails?,
    val detail: String? = null,
) {
    val isScanningWallet: Boolean get() = state == NodeTabState.ScanningWallet

    companion object {
        fun from(snapshot: RuntimeSnapshot?, userPaused: Boolean): NodeTabModel {
            val headline = snapshot?.headline
            val status = snapshot?.status
            val details = status?.let {
                NodeDetails(it.chain, it.blocks, it.headers, it.connections, it.pruneHeight, it.subversion)
            }
            return when (headline) {
                null, Headline.Stopped -> model(NodeTabState.Stopped, canStart = true)
                Headline.Starting -> model(NodeTabState.Starting, canStop = true)
                Headline.Stopping -> model(NodeTabState.Stopping)
                is Headline.Connecting -> model(NodeTabState.CatchingUp, percent = 0, canPause = !userPaused, canStop = true, details = details)
                is Headline.Paused -> model(
                    NodeTabState.Paused,
                    reason = headline.reasons.firstOrNull(),
                    canResume = userPaused,
                    canStop = true,
                    details = details,
                )
                is Headline.Syncing -> {
                    val p = if (details != null && details.headers > 0) {
                        kotlin.math.round(details.blocks.toDouble() / details.headers.toDouble() * 100).toInt().coerceIn(0, 99)
                    } else {
                        HomeUiModel.percent(headline.progress)
                    }
                    model(
                        NodeTabState.CatchingUp,
                        percent = p,
                        canPause = !userPaused,
                        canStop = true,
                        details = details,
                    )
                }
                is Headline.ScanningWallet -> model(
                    NodeTabState.ScanningWallet,
                    percent = kotlin.math.round(headline.progress * 100).toInt().coerceIn(0, 100),
                    canPause = !userPaused,
                    canStop = true,
                    details = details,
                )
                is Headline.Synchronized -> model(NodeTabState.UpToDate, canPause = !userPaused, canStop = true, details = details)
                is Headline.Crashed -> model(NodeTabState.Problem, failure = headline.failure, canStart = true).copy(
                    detail = (snapshot.state as? NodeState.Crashed)?.detail?.takeIf { it.isNotBlank() },
                )
            }
        }

        private fun model(
            state: NodeTabState,
            percent: Int? = null,
            reason: PolicyReason? = null,
            failure: NodeFailure? = null,
            canPause: Boolean = false,
            canResume: Boolean = false,
            canStop: Boolean = false,
            canStart: Boolean = false,
            details: NodeDetails? = null,
        ) = NodeTabModel(state, percent, reason, failure, canPause, canResume, canStop, canStart, details)
    }
}
