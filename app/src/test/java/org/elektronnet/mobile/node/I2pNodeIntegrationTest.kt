package org.elektronnet.mobile.node

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class I2pNodeIntegrationTest {
    private val samServer = """
        import socket, sys, threading
        port, log = int(sys.argv[1]), sys.argv[2]
        s = socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind(('127.0.0.1', port)); s.listen(5)
        def serve(c):
            f = c.makefile('rw')
            for line in f:
                open(log, 'a').write(line)
                if line.startswith('HELLO'):
                    f.write('HELLO REPLY RESULT=OK VERSION=3.1\n'); f.flush()
                else:
                    f.write('SESSION STATUS RESULT=I2P_ERROR MESSAGE=test\n'); f.flush()
        while True:
            c, _ = s.accept()
            threading.Thread(target=serve, args=(c,), daemon=True).start()
    """.trimIndent()

    @Test
    fun theNodeConnectsToTheConfiguredSamPort() {
        val node = System.getenv("ELEKTROND_BIN")
        assumeTrue("ELEKTROND_BIN not set", node != null && File(node).canExecute())
        val root = Files.createTempDirectory("i2pnode").toFile()
        val ports = NetworkPorts(torSocks = 29220, torControl = 29221, i2pSam = 29222)
        val received = File(root, "sam.log")
        val script = File(root, "sam.py").apply { writeText(samServer) }
        val sam = ProcessBuilder("python3", script.path, ports.i2pSam.toString(), received.path).redirectErrorStream(true).start()
        val config = NodeConfig(
            File(node!!), File(root, "node"), Chain.Regtest, rpcPort = 29223, p2pPort = 29224, listen = true,
            network = NetworkSettings(i2p = I2pSettings(enabled = true)), ports = ports, extraArgs = listOf("-debug=i2p"),
        )
        val supervisor = NodeSupervisor(config, pollIntervalMs = 100)
        try {
            supervisor.start()
            val end = System.currentTimeMillis() + 60_000
            while (supervisor.state != NodeState.Running) {
                check(System.currentTimeMillis() < end) { "node did not start" }
                Thread.sleep(100)
            }
            val logEnd = System.currentTimeMillis() + 30_000
            while ((!received.exists() || !received.readText().contains("HELLO")) && System.currentTimeMillis() < logEnd) Thread.sleep(200)
            assertTrue("the node never reached the SAM port: ${supervisor.output.tail(10)}", received.exists() && received.readText().contains("HELLO VERSION"))
            val info = supervisor.rpc.call("getnetworkinfo") as Map<*, *>
            val i2p = (info["networks"] as List<*>).map { it as Map<*, *> }.first { it["name"] == "i2p" }
            assertEquals(true, i2p["reachable"])
        } finally {
            supervisor.stop()
            sam.destroy()
            root.deleteRecursively()
        }
    }
}
