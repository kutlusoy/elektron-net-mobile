package org.elektronnet.mobile.node

import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class NodeSupervisor(
    private val config: NodeConfig,
    val output: OutputBuffer = OutputBuffer(),
    private val pollIntervalMs: Long = 300,
) {
    val rpc = RpcClient(config.rpcPort, config.cookieFile)

    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<(NodeState) -> Unit>()
    private var process: Process? = null
    @Volatile
    private var stopRequested = false

    @Volatile
    var startedAt: Long = 0
        private set

    @Volatile
    var warmup: String? = null
        private set

    @Volatile
    var rpcProblem: String? = null
        private set

    @Volatile
    var state: NodeState = NodeState.Stopped
        private set

    fun addListener(listener: (NodeState) -> Unit) {
        listeners.add(listener)
    }

    fun start() {
        synchronized(lock) {
            if (state == NodeState.Starting || state == NodeState.Running || state == NodeState.Stopping) return
            stopRequested = false
            val started = try {
                check(config.binary.exists()) { "node binary is missing: ${config.binary}" }
                check(config.binary.canExecute()) { "node binary is not executable: ${config.binary}" }
                NodeConfigFile.ensure(config)
                DatadirVersion.ensure(config.datadir)
                config.cookieFile.delete()
                val builder = ProcessBuilder(listOf(config.binary.absolutePath) + config.arguments())
                    .directory(config.datadir)
                    .redirectErrorStream(true)
                config.homeDir?.let { builder.environment()["HOME"] = it.absolutePath }
                builder.start()
            } catch (e: Exception) {
                val message = e.message ?: e.javaClass.simpleName
                output.add(message)
                setState(NodeState.Crashed(-1, NodeFailure.StartFailed, message))
                return
            }
            process = started
            startedAt = System.currentTimeMillis()
            warmup = null
            setState(NodeState.Starting)
            val drainer = thread("node-output") { drain(started) }
            thread("node-exit") { awaitExit(started, drainer) }
            thread("node-ready") { awaitReady(started) }
        }
    }

    fun stop(timeoutMs: Long = 60_000): Boolean {
        val target: Process
        synchronized(lock) {
            target = process ?: return true
            if (!target.isAlive) return true
            stopRequested = true
            setState(NodeState.Stopping)
        }
        try {
            rpc.call("stop", timeoutMs = 10_000)
        } catch (e: IOException) {
        } catch (e: RpcException) {
        }
        if (!target.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            target.destroy()
            if (!target.waitFor(10_000, TimeUnit.MILLISECONDS)) {
                target.destroyForcibly()
                target.waitFor(10_000, TimeUnit.MILLISECONDS)
            }
        }
        return !target.isAlive
    }

    fun status(): NodeStatus {
        val chain = rpc.call("getblockchaininfo") as Map<*, *>
        val network = rpc.call("getnetworkinfo") as Map<*, *>
        return NodeStatus(
            chain = chain["chain"] as String,
            blocks = chain["blocks"] as Long,
            headers = chain["headers"] as Long,
            verificationProgress = (chain["verificationprogress"] as Number).toDouble(),
            initialBlockDownload = chain["initialblockdownload"] as Boolean,
            pruned = chain["pruned"] as Boolean,
            pruneHeight = chain["pruneheight"] as? Long,
            connections = network["connections"] as Long,
            subversion = network["subversion"] as String,
        )
    }

    fun telemetry(): Telemetry {
        fun call(method: String) = try { rpc.call(method, timeoutMs = 5_000) } catch (e: RpcException) { null }
        return Telemetry.parse(call("getblockchaininfo"), call("getmininginfo"), call("getmempoolinfo"), call("getnetworkinfo"))
    }

    fun peers(): List<PeerInfo> = PeerInfo.parse(rpc.call("getpeerinfo", timeoutMs = 5_000))

    private fun drain(started: Process) {
        started.inputStream.bufferedReader().useLines { lines -> lines.forEach { output.add(it) } }
    }

    private fun awaitExit(started: Process, drainer: Thread) {
        val code = started.waitFor()
        drainer.join(DRAIN_JOIN_MS)
        synchronized(lock) {
            if (process !== started) return
            process = null
            if (stopRequested || code == 0) {
                setState(NodeState.Stopped)
            } else {
                setState(NodeState.Crashed(code, FailureClassifier.classify(output.tail(100)), output.tail(6).joinToString("\n")))
            }
        }
    }

    private fun awaitReady(started: Process) {
        while (started.isAlive) {
            try {
                rpc.call("getblockchaininfo", timeoutMs = 5_000)
                markRunning(started)
                return
            } catch (e: RpcException) {
                if (e.inWarmup) warmup = e.message
                if (!e.inWarmup) {
                    markRunning(started)
                    return
                }
            } catch (e: IOException) {
                rpcProblem = e.message ?: e.javaClass.simpleName
            }
            Thread.sleep(pollIntervalMs)
        }
    }

    private fun markRunning(started: Process) {
        synchronized(lock) {
            if (process === started && state == NodeState.Starting) {
                warmup = null
                rpcProblem = null
                setState(NodeState.Running)
            }
        }
    }

    private fun setState(next: NodeState) {
        state = next
        listeners.forEach { it(next) }
    }

    private fun thread(name: String, body: () -> Unit): Thread =
        Thread(body, name).apply { isDaemon = true }.also { it.start() }

    private companion object {
        const val DRAIN_JOIN_MS = 500L
    }
}
