package org.elektronnet.mobile.policy

import org.elektronnet.mobile.node.NodeProfile

enum class PolicyReason {
    UserStopped,
    UserPaused,
    BackgroundDisabled,
    StorageLow,
    NoNetwork,
    MeteredNotAllowed,
    RoamingNotAllowed,
    DataSaver,
    DataCapReached,
    NeedsCharging,
    BatteryLow,
    ThermalPause,
    OutsideSchedule,
    ReducedBattery,
    ReducedThermal,
}

data class PolicyDecision(
    val run: Boolean,
    val networkActive: Boolean,
    val profile: NodeProfile,
    val listen: Boolean,
    val serveSnapshots: Boolean,
    val reasons: Set<PolicyReason>,
)
