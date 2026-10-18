package org.elektronnet.mobile.ui.model

import org.elektronnet.mobile.node.NodeFailure
import org.elektronnet.mobile.node.NodeProfile
import org.elektronnet.mobile.node.NodeState
import org.elektronnet.mobile.node.NodeStatus
import org.elektronnet.mobile.policy.PolicyDecision
import org.elektronnet.mobile.policy.PolicyReason
import org.elektronnet.mobile.runtime.Headline
import org.elektronnet.mobile.runtime.RuntimeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiModelTest {
    private fun snapshot(headline: Headline, state: NodeState?, status: NodeStatus? = null) = RuntimeSnapshot(
        PolicyDecision(true, true, NodeProfile.Balanced, false, false, emptySet()),
        state,
        status,
        headline,
    )

    private val status = NodeStatus("main", 120, 500, 0.24, true, true, 100, 6, "/Elektron:1/")

    @Test
    fun homeChipFollowsTheHeadline() {
        assertEquals(NodeChip.Stopped, HomeUiModel.from(null).chip)
        assertEquals(NodeChip.Syncing, HomeUiModel.from(Headline.Starting).chip)
        assertEquals(NodeChip.Syncing, HomeUiModel.from(Headline.Syncing(1, 2, 0.5, 3)).chip)
        assertEquals(NodeChip.Synced, HomeUiModel.from(Headline.Synchronized(10, 3)).chip)
        assertEquals(NodeChip.Problem, HomeUiModel.from(Headline.Crashed(NodeFailure.DiskFull)).chip)
    }

    @Test
    fun offlineIsSeparateFromOtherPauses() {
        assertEquals(NodeChip.Offline, HomeUiModel.from(Headline.Paused(setOf(PolicyReason.NoNetwork))).chip)
        assertEquals(NodeChip.Paused, HomeUiModel.from(Headline.Paused(setOf(PolicyReason.BatteryLow))).chip)
    }

    @Test
    fun sendOnlyWhenSynchronized() {
        assertTrue(HomeUiModel.from(Headline.Synchronized(10, 3)).canSend)
        assertFalse(HomeUiModel.from(Headline.Syncing(1, 2, 0.99, 3)).canSend)
        assertFalse(HomeUiModel.from(Headline.Paused(emptySet())).canSend)
        assertFalse(HomeUiModel.from(null).canSend)
        assertNull(HomeUiModel.from(Headline.Synchronized(10, 3)).syncNote)
    }

    @Test
    fun percentIsClampedBelowOneHundredWhileSyncing() {
        assertEquals(0, HomeUiModel.percent(0.000002))
        assertEquals(62, HomeUiModel.percent(0.62))
        assertEquals(99, HomeUiModel.percent(1.0))
        assertEquals(0, HomeUiModel.percent(-1.0))
        assertEquals(SyncNote.CatchingUp(62), HomeUiModel.from(Headline.Syncing(1, 2, 0.62, 3)).syncNote)
    }

    @Test
    fun nodeTabOffersTheRightActions() {
        val syncing = NodeTabModel.from(snapshot(Headline.Syncing(120, 500, 0.24, 6), NodeState.Running, status), userPaused = false)
        assertEquals(NodeTabState.CatchingUp, syncing.state)
        assertEquals(24, syncing.percent)
        assertTrue(syncing.canPause)
        assertFalse(syncing.canResume)
        assertTrue(syncing.canStop)
        assertEquals(120L, syncing.details?.blocks)

        val paused = NodeTabModel.from(snapshot(Headline.Paused(setOf(PolicyReason.UserPaused)), NodeState.Running), userPaused = true)
        assertEquals(NodeTabState.Paused, paused.state)
        assertEquals(PolicyReason.UserPaused, paused.reason)
        assertTrue(paused.canResume)
        assertFalse(paused.canPause)

        val autoPaused = NodeTabModel.from(snapshot(Headline.Paused(setOf(PolicyReason.BatteryLow)), NodeState.Running), userPaused = false)
        assertFalse(autoPaused.canResume)
        assertTrue(autoPaused.canStop)
    }

    @Test
    fun stoppedAndCrashedCanBeStarted() {
        assertTrue(NodeTabModel.from(null, userPaused = false).canStart)
        val crashed = NodeTabModel.from(snapshot(Headline.Crashed(NodeFailure.CorruptedDatabase), NodeState.Crashed(1, NodeFailure.CorruptedDatabase)), false)
        assertEquals(NodeTabState.Problem, crashed.state)
        assertEquals(NodeFailure.CorruptedDatabase, crashed.failure)
        assertTrue(crashed.canStart)
    }

    @Test
    fun upToDateHidesNothingImportant() {
        val model = NodeTabModel.from(snapshot(Headline.Synchronized(499, 6), NodeState.Running, status.copy(blocks = 499, headers = 500, initialBlockDownload = false)), false)
        assertEquals(NodeTabState.UpToDate, model.state)
        assertNull(model.percent)
        assertEquals(6L, model.details?.peers)
    }
}
