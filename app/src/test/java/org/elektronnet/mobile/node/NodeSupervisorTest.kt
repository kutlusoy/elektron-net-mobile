package org.elektronnet.mobile.node

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeSupervisorTest {
    private fun script(body: String): File {
        val file = Files.createTempFile("fake-node", ".sh").toFile()
        file.writeText("#!/bin/sh\n$body\n")
        file.setExecutable(true)
        return file
    }

    private fun supervisor(binary: File): NodeSupervisor {
        val dir = Files.createTempDirectory("node-data").toFile()
        return NodeSupervisor(NodeConfig(binary, dir, Chain.Regtest, 1, 2), pollIntervalMs = 50)
    }

    private fun waitFor(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > end) throw AssertionError("timeout")
            Thread.sleep(20)
        }
    }

    @Test
    fun classifiesCrashFromOutput() {
        val node = supervisor(script("echo 'Error opening block database'; exit 1"))
        node.start()
        waitFor { node.state is NodeState.Crashed }
        assertEquals(NodeState.Crashed(1, NodeFailure.CorruptedDatabase), node.state)
        assertTrue(node.output.snapshot().contains("Error opening block database"))
    }

    @Test
    fun cleanExitIsStopped() {
        val node = supervisor(script("exit 0"))
        node.start()
        waitFor { node.state == NodeState.Stopped }
    }

    @Test
    fun stopEscalatesWhenRpcIsUnavailable() {
        val node = supervisor(script("echo up; sleep 60"))
        node.start()
        waitFor { node.output.snapshot().contains("up") }
        assertEquals(NodeState.Starting, node.state)
        assertTrue(node.stop(timeoutMs = 300))
        waitFor { node.state == NodeState.Stopped }
    }

    @Test
    fun drainsLargeOutput() {
        val node = supervisor(script("i=0; while [ \$i -lt 5000 ]; do echo line\$i; i=\$((i+1)); done; exit 0"))
        node.start()
        waitFor { node.state == NodeState.Stopped }
        assertEquals("line4999", node.output.snapshot().last())
    }

    @Test
    fun refusesToStartNonExecutable() {
        val file = Files.createTempFile("not-exec", "").toFile().apply { setExecutable(false) }
        val failed = try {
            supervisor(file).start()
            false
        } catch (e: IllegalStateException) {
            true
        }
        assertTrue(failed)
    }
}
