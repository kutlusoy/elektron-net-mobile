package org.elektronnet.mobile.node

import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

sealed interface ServiceState {
    data object Stopped : ServiceState
    data object Starting : ServiceState
    data object Running : ServiceState
    data class Failed(val exitCode: Int, val detail: String) : ServiceState
}

class ChildService(
    val name: String,
    private val command: List<String>,
    private val workDir: File,
    private val readyPort: Int?,
    private val readyTimeoutMs: Long = 60_000,
    private val environment: Map<String, String> = emptyMap(),
    val output: OutputBuffer = OutputBuffer(500),
) {
    private val lock = Any()
    private var process: Process? = null

    @Volatile
    var state: ServiceState = ServiceState.Stopped
        private set

    @Volatile
    var startedAt: Long = 0
        private set

    fun start() {
        synchronized(lock) {
            if (state == ServiceState.Starting || state == ServiceState.Running) return
            val started = try {
                workDir.mkdirs()
                ProcessBuilder(command).directory(workDir).redirectErrorStream(true).also { it.environment().putAll(environment) }.start()
            } catch (e: IOException) {
                val message = e.message ?: e.javaClass.simpleName
                output.add(message)
                state = ServiceState.Failed(-1, message)
                return
            }
            process = started
            startedAt = System.currentTimeMillis()
            state = ServiceState.Starting
            val reader = thread("$name-output") { try { started.inputStream.bufferedReader().useLines { lines -> lines.forEach { output.add(it) } } } catch (e: IOException) { } }
            thread("$name-exit") { awaitExit(started, reader) }
            thread("$name-ready") { awaitReady(started) }
        }
    }

    fun stop(timeoutMs: Long = 10_000) {
        val target: Process
        synchronized(lock) {
            target = process ?: return
            process = null
            state = ServiceState.Stopped
        }
        target.destroy()
        if (!target.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            target.destroyForcibly()
            target.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        }
    }

    private fun awaitExit(started: Process, reader: Thread) {
        val code = started.waitFor()
        reader.join(DRAIN_JOIN_MS)
        synchronized(lock) {
            if (process !== started) return
            process = null
            state = if (code == 0) ServiceState.Stopped else ServiceState.Failed(code, output.tail(4).joinToString("\n"))
        }
    }

    private fun awaitReady(started: Process) {
        val port = readyPort
        val fastUntil = System.currentTimeMillis() + readyTimeoutMs
        while (started.isAlive) {
            if (port == null || reachable(port)) {
                synchronized(lock) { if (process === started && state == ServiceState.Starting) state = ServiceState.Running }
                return
            }
            Thread.sleep(if (System.currentTimeMillis() < fastUntil) POLL_MS else SLOW_POLL_MS)
        }
    }

    private fun reachable(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(NetworkArgs.LOOPBACK, port), CONNECT_TIMEOUT_MS) }
        true
    } catch (e: IOException) {
        false
    }

    private fun thread(name: String, body: () -> Unit): Thread = Thread(body, name).apply { isDaemon = true }.also { it.start() }

    private companion object {
        const val POLL_MS = 250L
        const val DRAIN_JOIN_MS = 500L
        const val SLOW_POLL_MS = 5_000L
        const val CONNECT_TIMEOUT_MS = 500
    }
}
