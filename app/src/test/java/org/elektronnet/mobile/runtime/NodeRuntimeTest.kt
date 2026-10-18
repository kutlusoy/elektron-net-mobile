package org.elektronnet.mobile.runtime

import java.io.File
import java.nio.file.Files
import org.elektronnet.mobile.node.Chain
import org.elektronnet.mobile.node.NodeConfig
import org.elektronnet.mobile.node.NodeController
import org.elektronnet.mobile.node.NodeState
import org.elektronnet.mobile.node.NodeSupervisor
import org.elektronnet.mobile.policy.DeviceState
import org.elektronnet.mobile.policy.NetworkType
import org.elektronnet.mobile.policy.PolicyEngine
import org.elektronnet.mobile.policy.PolicySettings
import org.elektronnet.mobile.policy.ThermalLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class NodeRuntimeTest {
    private val gib = 1024L * 1024 * 1024

    private fun device(network: NetworkType = NetworkType.Unmetered, visible: Boolean = false) =
        DeviceState(true, 90, ThermalLevel.None, network, false, 50 * gib, visible, 600)

    private fun runtime(binary: File, settings: () -> PolicySettings, device: () -> DeviceState, rpcPort: Int): NodeRuntime {
        val dir = Files.createTempDirectory("rt").toFile()
        val config = NodeConfig(binary, dir, Chain.Regtest, rpcPort, rpcPort + 1)
        return NodeRuntime(NodeController(config, stopTimeoutMs = 300) { NodeSupervisor(it, pollIntervalMs = 50) }, PolicyEngine(), settings, device, { 1_000L })
    }

    @Test
    fun budgetCountsOnlyCellularTraffic() {
        val fake = Files.createTempFile("n", ".sh").toFile().apply { writeText("#!/bin/sh\nsleep 30\n"); setExecutable(true) }
        val rt = runtime(fake, { PolicySettings() }, { device() }, 1)
        rt.recordTotals(1_000, NetworkType.Unmetered, 1_000)
        rt.recordTotals(1_600, NetworkType.Metered, 1_000)
        rt.recordTotals(1_700, NetworkType.Roaming, 1_000)
        rt.recordTotals(1_900, NetworkType.Unmetered, 1_000)
        assertEquals(700L, rt.budget.usedAt(1_000))
    }

    @Test
    fun tickStartsAndStopsTheNodeFromPolicy() {
        val fake = Files.createTempFile("n", ".sh").toFile().apply { writeText("#!/bin/sh\necho up\nsleep 30\n"); setExecutable(true) }
        var settings = PolicySettings()
        val rt = runtime(fake, { settings }, { device() }, 1)
        val first = rt.tick()
        assertEquals(Headline.Starting, first.headline)
        assertEquals(1_000L, rt.suggestedDelayMs(first, appVisible = false))
        settings = PolicySettings(userStopped = true)
        val stopped = rt.tick()
        assertEquals(Headline.Stopped, stopped.headline)
        assertEquals(NodeState.Stopped, stopped.state)
    }

    @Test
    fun tickAgainstARealNodeReachesSynchronizedOrSyncing() {
        val binary = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", binary != null && File(binary).canExecute())
        var settings = PolicySettings()
        val rt = runtime(File(binary!!), { settings }, { device(visible = true) }, 28532)
        var snapshot = rt.tick()
        val end = System.currentTimeMillis() + 60_000
        while (snapshot.status == null && System.currentTimeMillis() < end) {
            Thread.sleep(200)
            snapshot = rt.tick()
        }
        assertTrue(snapshot.headline is Headline.Synchronized || snapshot.headline is Headline.Syncing)
        assertEquals(2_000L, rt.suggestedDelayMs(snapshot, appVisible = true))
        settings = PolicySettings(userStopped = true)
        assertEquals(NodeState.Stopped, rt.tick().state)
    }
}
