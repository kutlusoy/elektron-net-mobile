package org.elektronnet.mobile.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainScheduleTest {
    @Test
    fun halvingAndRewards() {
        val s = ChainSchedule.compute(272_904, 1_000_000, null, null)
        assertEquals(2_102_400L, s.nextHalvingHeight)
        assertEquals(2_102_400L - 272_904L, s.blocksToHalving)
        assertEquals(500_000_000L, s.rewardNowLep)
        assertEquals(250_000_000L, s.rewardNextLep)
        assertEquals(1_000_000_000L + s.blocksToHalving * 60_000L, s.halvingAtMs)
        val later = ChainSchedule.compute(2_102_400, 0, null, null)
        assertEquals(250_000_000L, later.rewardNowLep)
        assertEquals(125_000_000L, later.rewardNextLep)
        assertEquals(4_204_800L, later.nextHalvingHeight)
    }

    @Test
    fun epochProjectionFollowsTheBlockPace() {
        val start = 135L * 2016
        val s = ChainSchedule.compute(start + 867, 1_000_000 + 53_615, 1_000_000, 104_932.0)
        assertEquals(867L, s.epochBlocksMined)
        assertEquals(2016L - 867L, s.retargetBlocksLeft)
        assertEquals(61.84, s.avgBlockSeconds!!, 0.01)
        assertEquals(0.970, s.difficultyFactor!!, 0.002)
        assertEquals(101_800.0, s.projectedDifficulty!!, 400.0)
    }

    @Test
    fun clampsAndHandlesTheFirstBlockOfAnEpoch() {
        val fast = ChainSchedule.compute(2016 + 10, 1_000_010, 1_000_000, 100.0)
        assertEquals(4.0, fast.difficultyFactor!!, 0.0)
        val slow = ChainSchedule.compute(2016 + 10, 1_000_000 + 10_000_000, 1_000_000, 100.0)
        assertEquals(0.25, slow.difficultyFactor!!, 0.0)
        val first = ChainSchedule.compute(4032, 5, 5, 100.0)
        assertNull(first.avgBlockSeconds)
        assertNull(first.projectedDifficulty)
        assertEquals(2016L, first.retargetBlocksLeft)
        assertTrue(first.halvingAtMs > 0)
    }
}
