package org.elektronnet.mobile.policy

import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthlyDataBudgetTest {
    private fun ms(year: Int, month: Int, day: Int) = ZonedDateTime.of(year, month, day, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun accumulatesWithinAMonthAndResetsOnTheNext() {
        val budget = MonthlyDataBudget(ZoneOffset.UTC)
        budget.add(100, ms(2026, 10, 3))
        budget.add(50, ms(2026, 10, 20))
        assertEquals(150L, budget.usedAt(ms(2026, 10, 25)))
        assertEquals(0L, budget.usedAt(ms(2026, 11, 1)))
        budget.add(7, ms(2026, 11, 1))
        assertEquals(7L, budget.usedAt(ms(2026, 11, 2)))
    }

    @Test
    fun totalsTrackerHandlesCounterResets() {
        val tracker = NetTotalsTracker()
        assertEquals(500L, tracker.delta(500))
        assertEquals(300L, tracker.delta(800))
        assertEquals(40L, tracker.delta(40))
        assertEquals(10L, tracker.delta(50))
    }
}
