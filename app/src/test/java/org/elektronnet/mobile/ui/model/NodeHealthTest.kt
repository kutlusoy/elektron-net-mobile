package org.elektronnet.mobile.ui.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeHealthTest {
    @Test
    fun healthyStartAndRun() {
        assertEquals(NodeHealth.Ok, NodeHealthRules.assess(true, false, 10_000, 1_000, 0, 0))
        assertEquals(NodeHealth.Ok, NodeHealthRules.assess(false, true, 500_000, 1_000, 3, 0))
    }

    @Test
    fun detectsProblems() {
        assertEquals(NodeHealth.SlowStart, NodeHealthRules.assess(true, false, 130_000, 5_000, 0, 0))
        assertEquals(NodeHealth.Silent, NodeHealthRules.assess(true, false, 130_000, 125_000, 0, 0))
        assertEquals(NodeHealth.NoPeers, NodeHealthRules.assess(false, true, 500_000, 1_000, 0, 61_000))
        assertEquals(NodeHealth.Ok, NodeHealthRules.assess(false, true, 500_000, 1_000, 0, 20_000))
    }

    @Test
    fun formatsTheClock() {
        assertEquals("0:42", NodeHealthRules.clock(42_000))
        assertEquals("12:05", NodeHealthRules.clock(725_000))
    }
}
