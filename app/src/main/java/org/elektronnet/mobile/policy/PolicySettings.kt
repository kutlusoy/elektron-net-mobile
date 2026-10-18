package org.elektronnet.mobile.policy

import org.elektronnet.mobile.node.NodeProfile

data class ScheduleWindow(val startMinute: Int, val endMinute: Int) {
    fun contains(minuteOfDay: Int): Boolean =
        if (startMinute <= endMinute) minuteOfDay in startMinute until endMinute else minuteOfDay >= startMinute || minuteOfDay < endMinute
}

data class PolicySettings(
    val userStopped: Boolean = false,
    val userPaused: Boolean = false,
    val backgroundEnabled: Boolean = true,
    val profile: NodeProfile = NodeProfile.Balanced,
    val chargingOnly: Boolean = false,
    val allowMetered: Boolean = false,
    val allowRoaming: Boolean = false,
    val monthlyCellularCapBytes: Long? = null,
    val allowInbound: Boolean = true,
    val serveSnapshots: Boolean = true,
    val storageFloorBytes: Long = 2L * 1024 * 1024 * 1024,
    val schedule: ScheduleWindow? = null,
)

data class PolicyTuning(
    val batteryPauseBelow: Int = 15,
    val batteryResumeAt: Int = 20,
    val batteryReduceBelow: Int = 30,
    val batteryFullAt: Int = 35,
    val storageResumeMarginBytes: Long = 256L * 1024 * 1024,
    val minDwellMs: Long = 10 * 60 * 1000,
)
