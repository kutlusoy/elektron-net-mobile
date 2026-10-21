package org.elektronnet.mobile.policy

import org.elektronnet.mobile.node.NodeProfile

class PolicyEngine(private val tuning: PolicyTuning = PolicyTuning()) {
    private var batteryPaused = false
    private var batteryReduced = false
    private var thermalPaused = false
    private var thermalReduced = false
    private var storageStopped = false
    private var last: PolicyDecision? = null
    private var lastChangeAt = 0L

    fun evaluate(settings: PolicySettings, device: DeviceState, nowMs: Long): PolicyDecision {
        updateHysteresis(settings, device)
        val reasons = linkedSetOf<PolicyReason>()

        val backgroundAllowed = settings.backgroundEnabled || device.appVisible
        if (settings.userStopped) reasons.add(PolicyReason.UserStopped)
        if (storageStopped) reasons.add(PolicyReason.StorageLow)
        if (!backgroundAllowed) reasons.add(PolicyReason.BackgroundDisabled)
        val run = reasons.isEmpty()

        if (run) networkBlockers(settings, device, reasons)
        val networkActive = run && reasons.isEmpty()

        var profile = if (settings.backgroundEnabled) settings.profile else NodeProfile.Foreground
        if (batteryReduced) reasons.add(PolicyReason.ReducedBattery)
        if (thermalReduced) reasons.add(PolicyReason.ReducedThermal)
        if ((batteryReduced || thermalReduced) && profile != NodeProfile.Foreground) profile = NodeProfile.Foreground
        var listen = settings.allowInbound && device.network == NetworkType.Unmetered

        val previous = last
        if (previous != null && previous.run && run && (previous.profile != profile || previous.listen != listen)) {
            if (nowMs - lastChangeAt < tuning.minDwellMs) {
                profile = previous.profile
                listen = previous.listen
            } else {
                lastChangeAt = nowMs
            }
        } else if (previous == null || !previous.run) {
            lastChangeAt = nowMs
        }

        val serve = settings.serveSnapshots && networkActive && device.network == NetworkType.Unmetered && device.charging
        val decision = PolicyDecision(run, networkActive, profile, listen, serve, reasons, settings.network)
        last = decision
        return decision
    }

    private fun networkBlockers(settings: PolicySettings, device: DeviceState, reasons: MutableSet<PolicyReason>) {
        if (settings.userPaused) reasons.add(PolicyReason.UserPaused)
        when (device.network) {
            NetworkType.None -> reasons.add(PolicyReason.NoNetwork)
            NetworkType.Unmetered -> {}
            NetworkType.Metered, NetworkType.Roaming -> {
                if (device.network == NetworkType.Roaming && !(settings.allowRoaming && settings.allowMetered)) {
                    reasons.add(PolicyReason.RoamingNotAllowed)
                } else if (!settings.allowMetered) {
                    reasons.add(PolicyReason.MeteredNotAllowed)
                } else if (device.dataSaver) {
                    reasons.add(PolicyReason.DataSaver)
                } else if (settings.monthlyCellularCapBytes != null && device.cellularBytesThisMonth >= settings.monthlyCellularCapBytes) {
                    reasons.add(PolicyReason.DataCapReached)
                }
            }
        }
        if (settings.chargingOnly && !device.charging) reasons.add(PolicyReason.NeedsCharging)
        if (batteryPaused) reasons.add(PolicyReason.BatteryLow)
        if (thermalPaused) reasons.add(PolicyReason.ThermalPause)
        val window = settings.schedule
        if (window != null && !window.contains(device.minuteOfDay)) reasons.add(PolicyReason.OutsideSchedule)
    }

    private fun updateHysteresis(settings: PolicySettings, device: DeviceState) {
        if (device.charging) {
            batteryPaused = false
            batteryReduced = false
        } else {
            if (device.batteryPercent < tuning.batteryPauseBelow) batteryPaused = true
            if (device.batteryPercent >= tuning.batteryResumeAt) batteryPaused = false
            if (device.batteryPercent < tuning.batteryReduceBelow) batteryReduced = true
            if (device.batteryPercent >= tuning.batteryFullAt) batteryReduced = false
        }
        if (device.thermal >= ThermalLevel.Severe) thermalPaused = true
        if (device.thermal <= ThermalLevel.Light) thermalPaused = false
        if (device.thermal >= ThermalLevel.Moderate) thermalReduced = true
        if (device.thermal <= ThermalLevel.Light) thermalReduced = false
        if (device.freeStorageBytes < settings.storageFloorBytes) storageStopped = true
        if (device.freeStorageBytes >= settings.storageFloorBytes + tuning.storageResumeMarginBytes) storageStopped = false
    }
}
