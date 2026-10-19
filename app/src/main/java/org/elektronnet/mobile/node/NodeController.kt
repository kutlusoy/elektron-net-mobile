package org.elektronnet.mobile.node

import org.elektronnet.mobile.policy.PolicyDecision

class NodeController(
    private val baseConfig: NodeConfig,
    private val stopTimeoutMs: Long = 60_000,
    private val supervisorFactory: (NodeConfig) -> NodeSupervisor = { NodeSupervisor(it) },
) {
    private var supervisor: NodeSupervisor? = null
    private var appliedConfig: NodeConfig? = null
    private var appliedNetworkActive: Boolean? = null

    val current: NodeSupervisor? @Synchronized get() = supervisor

    @Synchronized
    fun apply(decision: PolicyDecision) {
        if (!decision.run) {
            supervisor?.stop(stopTimeoutMs)
            appliedNetworkActive = null
            return
        }
        val wanted = baseConfig.copy(
            profile = decision.profile,
            listen = decision.listen,
            networkActive = decision.networkActive,
        )
        val active = supervisor
        val needsRestart = appliedConfig?.let { it.profile != wanted.profile || it.listen != wanted.listen } ?: false
        when {
            active == null || active.state == NodeState.Stopped -> launch(wanted)
            active.state is NodeState.Crashed -> return
            needsRestart -> {
                active.stop(stopTimeoutMs)
                launch(wanted)
            }
        }
        applyNetworkActive(decision.networkActive)
    }

    @Synchronized
    fun shutdown() {
        supervisor?.stop(stopTimeoutMs)
        appliedNetworkActive = null
    }

    @Synchronized
    fun clearCrash() {
        if (supervisor?.state is NodeState.Crashed) supervisor = null
    }

    @Synchronized
    fun restartAfterCrash(decision: PolicyDecision) {
        clearCrash()
        apply(decision)
    }

    private fun launch(config: NodeConfig) {
        val next = supervisorFactory(config)
        supervisor = next
        appliedConfig = config
        appliedNetworkActive = config.networkActive
        next.start()
    }

    private fun applyNetworkActive(wanted: Boolean) {
        val active = supervisor ?: return
        if (active.state != NodeState.Running || appliedNetworkActive == wanted) return
        try {
            active.rpc.call("setnetworkactive", listOf(wanted))
            appliedNetworkActive = wanted
        } catch (e: java.io.IOException) {
        } catch (e: RpcException) {
        }
    }
}
