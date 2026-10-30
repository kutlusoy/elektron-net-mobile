package org.elektronnet.mobile.policy

import org.elektronnet.mobile.node.NodeProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyEngineTest {
    private val gib = 1024L * 1024 * 1024

    private fun device(
        charging: Boolean = true,
        battery: Int = 80,
        thermal: ThermalLevel = ThermalLevel.None,
        network: NetworkType = NetworkType.Unmetered,
        dataSaver: Boolean = false,
        free: Long = 20 * gib,
        visible: Boolean = false,
        minute: Int = 12 * 60,
        cellular: Long = 0,
    ) = DeviceState(charging, battery, thermal, network, dataSaver, free, visible, minute, cellular)

    private val hour = 60 * 60 * 1000L

    @Test
    fun chargingOnWifiRunsFullAndServesSnapshots() {
        val d = PolicyEngine().evaluate(PolicySettings(), device(), 0)
        assertTrue(d.run)
        assertTrue(d.networkActive)
        assertEquals(NodeProfile.Balanced, d.profile)
        assertTrue(d.listen)
        assertTrue(d.serveSnapshots)
        assertTrue(d.reasons.isEmpty())
    }

    @Test
    fun snapshotsAreServedOnlyOnWifiWhileCharging() {
        val engine = PolicyEngine()
        assertFalse(engine.evaluate(PolicySettings(), device(charging = false), 0).serveSnapshots)
        assertFalse(engine.evaluate(PolicySettings(allowMetered = true), device(network = NetworkType.Metered), hour).serveSnapshots)
        assertFalse(engine.evaluate(PolicySettings(serveSnapshots = false), device(), 2 * hour).serveSnapshots)
        assertTrue(engine.evaluate(PolicySettings(), device(), 3 * hour).serveSnapshots)
    }

    @Test
    fun meteredNeedsConsentAndNeverListens() {
        val engine = PolicyEngine()
        val blocked = engine.evaluate(PolicySettings(), device(network = NetworkType.Metered), 0)
        assertTrue(blocked.run)
        assertFalse(blocked.networkActive)
        assertTrue(blocked.reasons.contains(PolicyReason.MeteredNotAllowed))
        val allowed = engine.evaluate(PolicySettings(allowMetered = true), device(network = NetworkType.Metered), hour)
        assertTrue(allowed.networkActive)
        assertFalse(allowed.listen)
    }

    @Test
    fun roamingNeedsItsOwnConsent() {
        val engine = PolicyEngine()
        val settings = PolicySettings(allowMetered = true)
        assertTrue(engine.evaluate(settings, device(network = NetworkType.Roaming), 0).reasons.contains(PolicyReason.RoamingNotAllowed))
        assertTrue(engine.evaluate(settings.copy(allowRoaming = true), device(network = NetworkType.Roaming), hour).networkActive)
    }

    @Test
    fun dataSaverAndCapBlockMeteredUse() {
        val engine = PolicyEngine()
        val settings = PolicySettings(allowMetered = true, monthlyCellularCapBytes = 1 * gib)
        assertTrue(engine.evaluate(settings, device(network = NetworkType.Metered, dataSaver = true), 0).reasons.contains(PolicyReason.DataSaver))
        assertTrue(engine.evaluate(settings, device(network = NetworkType.Metered, cellular = 2 * gib), hour).reasons.contains(PolicyReason.DataCapReached))
        assertTrue(engine.evaluate(settings, device(network = NetworkType.Metered, cellular = gib / 2), 2 * hour).networkActive)
        assertTrue(engine.evaluate(settings, device(network = NetworkType.Unmetered, cellular = 5 * gib), 3 * hour).networkActive)
    }

    @Test
    fun noNetworkPausesNetworking() {
        val d = PolicyEngine().evaluate(PolicySettings(), device(network = NetworkType.None), 0)
        assertTrue(d.run)
        assertFalse(d.networkActive)
        assertTrue(d.reasons.contains(PolicyReason.NoNetwork))
    }

    @Test
    fun chargingOnlyPausesOnBattery() {
        val d = PolicyEngine().evaluate(PolicySettings(chargingOnly = true), device(charging = false), 0)
        assertFalse(d.networkActive)
        assertTrue(d.reasons.contains(PolicyReason.NeedsCharging))
    }

    @Test
    fun batteryReducesThenPausesWithHysteresis() {
        val engine = PolicyEngine()
        val settings = PolicySettings()
        var now = 0L
        fun at(pct: Int, charging: Boolean = false): PolicyDecision {
            now += hour
            return engine.evaluate(settings, device(charging = charging, battery = pct), now)
        }
        assertEquals(NodeProfile.Balanced, at(50).profile)
        val low = at(29)
        assertEquals(NodeProfile.Foreground, low.profile)
        assertTrue(low.networkActive)
        assertTrue(low.reasons.contains(PolicyReason.ReducedBattery))
        assertEquals(NodeProfile.Foreground, at(32).profile)
        assertEquals(NodeProfile.Balanced, at(35).profile)
        val critical = at(14)
        assertFalse(critical.networkActive)
        assertTrue(critical.reasons.contains(PolicyReason.BatteryLow))
        assertFalse(at(18).networkActive)
        assertTrue(at(20).networkActive)
        assertTrue(at(10, charging = true).networkActive)
    }

    @Test
    fun thermalPausesAndRecoversWithHysteresis() {
        val engine = PolicyEngine()
        val settings = PolicySettings()
        var now = 0L
        fun at(level: ThermalLevel): PolicyDecision {
            now += hour
            return engine.evaluate(settings, device(thermal = level), now)
        }
        assertTrue(at(ThermalLevel.Light).networkActive)
        val moderate = at(ThermalLevel.Moderate)
        assertTrue(moderate.networkActive)
        assertEquals(NodeProfile.Foreground, moderate.profile)
        assertFalse(at(ThermalLevel.Severe).networkActive)
        assertFalse(at(ThermalLevel.Moderate).networkActive)
        assertTrue(at(ThermalLevel.Light).networkActive)
    }

    @Test
    fun lowStorageStopsTheNodeAndResumesWithMargin() {
        val engine = PolicyEngine()
        val settings = PolicySettings(storageFloorBytes = 2 * gib)
        val stopped = engine.evaluate(settings, device(free = gib), 0)
        assertFalse(stopped.run)
        assertTrue(stopped.reasons.contains(PolicyReason.StorageLow))
        assertFalse(engine.evaluate(settings, device(free = 2 * gib + 100), hour).run)
        assertTrue(engine.evaluate(settings, device(free = 3 * gib), 2 * hour).run)
    }

    @Test
    fun userStopAndPauseAreDistinct() {
        val engine = PolicyEngine()
        val stopped = engine.evaluate(PolicySettings(userStopped = true), device(), 0)
        assertFalse(stopped.run)
        assertFalse(stopped.networkActive)
        val paused = engine.evaluate(PolicySettings(userPaused = true), device(), hour)
        assertTrue(paused.run)
        assertFalse(paused.networkActive)
        assertTrue(paused.reasons.contains(PolicyReason.UserPaused))
    }

    @Test
    fun backgroundDisabledRunsOnlyWhileVisibleWithForegroundProfile() {
        val engine = PolicyEngine()
        val settings = PolicySettings(backgroundEnabled = false)
        val hidden = engine.evaluate(settings, device(visible = false), 0)
        assertFalse(hidden.run)
        assertTrue(hidden.reasons.contains(PolicyReason.BackgroundDisabled))
        val visible = engine.evaluate(settings, device(visible = true), hour)
        assertTrue(visible.run)
        assertEquals(NodeProfile.Foreground, visible.profile)
    }

    @Test
    fun scheduleWindowIncludingMidnightWrap() {
        val window = ScheduleWindow(22 * 60, 6 * 60)
        assertTrue(window.contains(23 * 60))
        assertTrue(window.contains(5 * 60))
        assertFalse(window.contains(12 * 60))
        val engine = PolicyEngine()
        val settings = PolicySettings(schedule = window)
        assertTrue(engine.evaluate(settings, device(minute = 23 * 60), 0).networkActive)
        val outside = engine.evaluate(settings, device(minute = 12 * 60), hour)
        assertFalse(outside.networkActive)
        assertTrue(outside.reasons.contains(PolicyReason.OutsideSchedule))
    }

    @Test
    fun alwaysOnProfileIsUsedWhenSelected() {
        assertEquals(NodeProfile.AlwaysOn, PolicyEngine().evaluate(PolicySettings(profile = NodeProfile.AlwaysOn), device(), 0).profile)
    }

    @Test
    fun profileChangesAreRateLimited() {
        val engine = PolicyEngine(PolicyTuning(minDwellMs = 10 * 60 * 1000))
        val settings = PolicySettings()
        assertEquals(NodeProfile.Balanced, engine.evaluate(settings, device(), 0).profile)
        val soon = engine.evaluate(settings, device(thermal = ThermalLevel.Moderate), 60_000)
        assertEquals(NodeProfile.Balanced, soon.profile)
        val later = engine.evaluate(settings, device(thermal = ThermalLevel.Moderate), 11 * 60_000)
        assertEquals(NodeProfile.Foreground, later.profile)
    }

    @Test
    fun listenDoesNotDependOnTheNetworkType() {
        val settings = PolicySettings(allowMetered = true)
        listOf(NetworkType.Unmetered, NetworkType.Metered, NetworkType.Roaming).forEach {
            assertTrue(it.name, PolicyEngine().evaluate(settings.copy(allowRoaming = true), device(network = it), 0).listen)
        }
    }

    @Test
    fun inboundCanBeDisabled() {
        assertFalse(PolicyEngine().evaluate(PolicySettings(allowInbound = false), device(), 0).listen)
    }
}
