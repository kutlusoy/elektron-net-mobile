package org.elektronnet.mobile.runtime

import java.io.IOException
import org.elektronnet.mobile.node.NodeController
import org.elektronnet.mobile.node.NodeState
import org.elektronnet.mobile.node.NodeStatus
import org.elektronnet.mobile.node.RpcException
import org.elektronnet.mobile.policy.DeviceState
import org.elektronnet.mobile.policy.MonthlyDataBudget
import org.elektronnet.mobile.policy.NetTotalsTracker
import org.elektronnet.mobile.policy.NetworkType
import org.elektronnet.mobile.policy.PolicyDecision
import org.elektronnet.mobile.policy.PolicyEngine
import org.elektronnet.mobile.policy.PolicySettings

data class RuntimeSnapshot(
    val decision: PolicyDecision,
    val state: NodeState?,
    val status: NodeStatus?,
    val headline: Headline,
)

class NodeRuntime(
    private val controller: NodeController,
    private val engine: PolicyEngine,
    private val settings: () -> PolicySettings,
    private val device: () -> DeviceState,
    private val clock: () -> Long = System::currentTimeMillis,
    val budget: MonthlyDataBudget = MonthlyDataBudget(),
) {
    private val totals = NetTotalsTracker()

    fun tick(): RuntimeSnapshot {
        val now = clock()
        val observed = device()
        val decision = engine.evaluate(settings(), observed.copy(cellularBytesThisMonth = budget.usedAt(now)), now)
        controller.apply(decision)
        val supervisor = controller.current
        val state = supervisor?.state
        var status: NodeStatus? = null
        if (state == NodeState.Running && supervisor != null) {
            try {
                status = supervisor.status()
                val counters = supervisor.rpc.call("getnettotals") as Map<*, *>
                recordTotals((counters["totalbytesrecv"] as Long) + (counters["totalbytessent"] as Long), observed.network, now)
            } catch (e: IOException) {
            } catch (e: RpcException) {
            }
        }
        return RuntimeSnapshot(decision, state, status, Headline.from(decision, state, status))
    }

    fun recordTotals(totalBytes: Long, network: NetworkType, nowMs: Long) {
        val delta = totals.delta(totalBytes)
        if (delta > 0 && (network == NetworkType.Metered || network == NetworkType.Roaming)) budget.add(delta, nowMs)
    }

    fun suggestedDelayMs(snapshot: RuntimeSnapshot, appVisible: Boolean): Long = when {
        snapshot.state == NodeState.Starting || snapshot.state == NodeState.Stopping -> 1_000
        appVisible -> 2_000
        snapshot.headline is Headline.Synchronized -> 60_000
        else -> 30_000
    }
}
