package org.elektronnet.mobile.runtime

import org.elektronnet.mobile.node.NodeFailure
import org.elektronnet.mobile.node.NodeProfile
import org.elektronnet.mobile.node.NodeState
import org.elektronnet.mobile.node.NodeStatus
import org.elektronnet.mobile.policy.PolicyDecision
import org.elektronnet.mobile.policy.PolicyReason
import org.junit.Assert.assertEquals
import org.junit.Test

class HeadlineTest {
    private fun decision(run: Boolean = true, net: Boolean = true, reasons: Set<PolicyReason> = emptySet()) =
        PolicyDecision(run, net, NodeProfile.Balanced, listen = false, serveSnapshots = false, reasons = reasons)

    private fun status(blocks: Long, headers: Long, ibd: Boolean = false) =
        NodeStatus("main", blocks, headers, 0.5, ibd, true, 100, 7, "/Elektron/")

    @Test
    fun stoppedWhenPolicyDoesNotRun() {
        assertEquals(Headline.Stopped, Headline.from(decision(run = false, net = false), NodeState.Stopped, null))
        assertEquals(Headline.Stopped, Headline.from(decision(), null, null))
    }

    @Test
    fun startingAndStopping() {
        assertEquals(Headline.Starting, Headline.from(decision(), NodeState.Starting, null))
        assertEquals(Headline.Stopping, Headline.from(decision(run = false), NodeState.Stopping, null))
    }

    @Test
    fun crashWinsOverEverything() {
        assertEquals(Headline.Crashed(NodeFailure.DiskFull), Headline.from(decision(), NodeState.Crashed(1, NodeFailure.DiskFull), null))
    }

    @Test
    fun pausedCarriesTheReasons() {
        val reasons = setOf(PolicyReason.BatteryLow)
        assertEquals(Headline.Paused(reasons), Headline.from(decision(net = false, reasons = reasons), NodeState.Running, null))
    }

    @Test
    fun syncingVersusSynchronized() {
        assertEquals(Headline.Syncing(100, 500, 0.5, 7), Headline.from(decision(), NodeState.Running, status(100, 500)))
        assertEquals(Headline.Syncing(500, 500, 0.5, 7), Headline.from(decision(), NodeState.Running, status(500, 500, ibd = true)))
        assertEquals(Headline.Synchronized(499, 7), Headline.from(decision(), NodeState.Running, status(499, 500)))
    }

    @Test
    fun connectingWhileStatusIsMissing() {
        assertEquals(Headline.Connecting(emptySet()), Headline.from(decision(), NodeState.Running, null))
    }
}
