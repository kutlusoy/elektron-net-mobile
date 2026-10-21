package org.elektronnet.mobile.node

import org.junit.Assert.assertEquals
import org.junit.Test

class TorLogStatusTest {
    private val start = listOf(
        "Oct 21 16:32:23.000 [notice] Bootstrapped 0% (starting): Starting",
        "Oct 21 16:32:30.000 [notice] Bootstrapped 14% (handshake): Handshaking with a relay",
    )
    private val done = start + "Oct 21 16:32:31.000 [notice] Bootstrapped 100% (done): Done"
    private val checking = done + "Oct 21 16:33:30.000 [notice] Now checking whether IPv4 ORPort <ip> is reachable... (this may take up to 20 minutes -- look for log messages indicating success)"

    @Test
    fun theLastBootstrapStepCounts() {
        assertEquals(TorLogStatus(14, RelayReach.Unknown), TorLogStatus.parse(start))
        assertEquals(TorLogStatus(100, RelayReach.Unknown), TorLogStatus.parse(done))
        assertEquals(TorLogStatus(null, RelayReach.Unknown), TorLogStatus.parse(emptyList()))
    }

    @Test
    fun theRelayPortTestMovesFromCheckingToItsResult() {
        assertEquals(RelayReach.Checking, TorLogStatus.parse(checking).reach)
        val failed = checking + "Oct 21 16:53:11.000 [warn] Your server has not managed to confirm reachability for its ORPort(s) at <ip> and <ip>. Relays do not publish descriptors until their ORPort(s) are reachable."
        assertEquals(RelayReach.Unreachable, TorLogStatus.parse(failed).reach)
        val worked = checking + "Oct 21 16:40:00.000 [notice] Self-testing indicates your ORPort <ip> is reachable from the outside. Excellent. Publishing server descriptor."
        assertEquals(RelayReach.Reachable, TorLogStatus.parse(worked).reach)
        assertEquals(RelayReach.Reachable, TorLogStatus.parse(worked + checking.last()).reach)
    }
}
