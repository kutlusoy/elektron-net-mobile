package org.elektronnet.mobile.policy

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class MonthlyDataBudget(
    private val zone: ZoneId = ZoneId.systemDefault(),
    private var month: YearMonth? = null,
    used: Long = 0,
) {
    var usedBytes: Long = used
        private set

    fun add(deltaBytes: Long, nowMs: Long) {
        require(deltaBytes >= 0) { "negative delta" }
        val current = YearMonth.from(Instant.ofEpochMilli(nowMs).atZone(zone))
        if (month != current) {
            month = current
            usedBytes = 0
        }
        usedBytes += deltaBytes
    }

    fun usedAt(nowMs: Long): Long {
        val current = YearMonth.from(Instant.ofEpochMilli(nowMs).atZone(zone))
        return if (month == current) usedBytes else 0
    }
}

class NetTotalsTracker {
    private var lastTotal: Long? = null

    fun delta(totalBytes: Long): Long {
        val previous = lastTotal
        lastTotal = totalBytes
        return if (previous == null || totalBytes < previous) totalBytes else totalBytes - previous
    }
}
