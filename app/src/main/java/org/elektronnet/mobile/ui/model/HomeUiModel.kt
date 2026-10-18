package org.elektronnet.mobile.ui.model

import org.elektronnet.mobile.node.NodeFailure
import org.elektronnet.mobile.policy.PolicyReason
import org.elektronnet.mobile.runtime.Headline

enum class NodeChip { Synced, Syncing, Paused, Offline, Stopped, Problem }

sealed interface SyncNote {
    data class CatchingUp(val percent: Int) : SyncNote
    data object Starting : SyncNote
    data class Paused(val reason: PolicyReason?) : SyncNote
    data object Stopped : SyncNote
    data class Problem(val failure: NodeFailure) : SyncNote
}

data class HomeUiModel(
    val chip: NodeChip,
    val syncNote: SyncNote?,
    val canSend: Boolean,
) {
    companion object {
        fun from(headline: Headline?): HomeUiModel = when (headline) {
            null, Headline.Stopped, Headline.Stopping -> HomeUiModel(NodeChip.Stopped, SyncNote.Stopped, false)
            Headline.Starting -> HomeUiModel(NodeChip.Syncing, SyncNote.Starting, false)
            is Headline.Connecting -> HomeUiModel(NodeChip.Syncing, SyncNote.CatchingUp(0), false)
            is Headline.Paused -> {
                val reason = headline.reasons.firstOrNull()
                val chip = if (reason == PolicyReason.NoNetwork) NodeChip.Offline else NodeChip.Paused
                HomeUiModel(chip, SyncNote.Paused(reason), false)
            }
            is Headline.Syncing -> HomeUiModel(NodeChip.Syncing, SyncNote.CatchingUp(percent(headline.progress)), false)
            is Headline.Synchronized -> HomeUiModel(NodeChip.Synced, null, true)
            is Headline.Crashed -> HomeUiModel(NodeChip.Problem, SyncNote.Problem(headline.failure), false)
        }

        fun percent(progress: Double): Int = (progress * 100).toInt().coerceIn(0, 99)
    }
}
