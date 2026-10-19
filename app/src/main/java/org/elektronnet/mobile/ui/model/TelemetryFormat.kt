package org.elektronnet.mobile.ui.model

import java.util.Locale

object TelemetryFormat {
    fun bytes(value: Long): String {
        if (value < 1024) return "$value B"
        val units = listOf("KB", "MB", "GB", "TB")
        var v = value.toDouble()
        var unit = -1
        while (v >= 1024 && unit < units.size - 1) {
            v /= 1024
            unit++
        }
        return String.format(Locale.US, "%.1f %s", v, units[unit])
    }

    fun hashRate(value: Double): String {
        if (value <= 0.0) return "-"
        val units = listOf("H/s", "kH/s", "MH/s", "GH/s", "TH/s", "PH/s")
        var v = value
        var unit = 0
        while (v >= 1000 && unit < units.size - 1) {
            v /= 1000
            unit++
        }
        return String.format(Locale.US, "%.2f %s", v, units[unit])
    }

    fun difficulty(value: Double): String = when {
        value >= 1000 -> String.format(Locale.US, "%.0f", value)
        else -> String.format(Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
    }
}

data class PruneSchedule(val keepBlocks: Long, val remainingBlocks: Long, val estimatedMs: Long?) {
    companion object {
        const val KEEP_BLOCKS = 197_280L
        const val BLOCK_SECONDS = 60L

        fun of(blocks: Long, nowMs: Long, keepBlocks: Long = KEEP_BLOCKS): PruneSchedule {
            val remaining = (keepBlocks - blocks).coerceAtLeast(0)
            return PruneSchedule(keepBlocks, remaining, if (remaining > 0) nowMs + remaining * BLOCK_SECONDS * 1000 else null)
        }
    }
}
