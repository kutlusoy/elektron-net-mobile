package org.elektronnet.mobile.node

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeConfigFileTest {
    private fun config(chain: Chain, dir: File) = NodeConfig(File("/bin/elektrond"), dir, chain, 18332, 18333)

    private fun tempDir(): File = Files.createTempDirectory("conf-test").toFile()

    @Test
    fun createsManagedFileForTestnet() {
        val dir = tempDir()
        NodeConfigFile.ensure(config(Chain.Testnet, dir))
        assertTrue(File(dir, "bitcoin.conf").readText().startsWith("#"))
    }

    @Test
    fun rewritesGeneratedMainnetFileForTestnetAndKeepsBackup() {
        val dir = tempDir()
        File(dir, "bitcoin.conf").writeText("addnode=1.2.3.4:8333\n")
        NodeConfigFile.ensure(config(Chain.Testnet, dir))
        assertFalse(File(dir, "bitcoin.conf").readText().contains("addnode"))
        assertEquals("addnode=1.2.3.4:8333\n", File(dir, "bitcoin.conf.bak").readText())
    }

    @Test
    fun keepsFileWithOwnSectionOnly() {
        val dir = tempDir()
        val text = "# note\n[test]\nmaxconnections=8\n"
        File(dir, "bitcoin.conf").writeText(text)
        NodeConfigFile.ensure(config(Chain.Testnet, dir))
        assertEquals(text, File(dir, "bitcoin.conf").readText())
    }

    @Test
    fun leavesMainnetFileToTheNode() {
        val dir = tempDir()
        NodeConfigFile.ensure(config(Chain.Main, dir))
        assertFalse(File(dir, "bitcoin.conf").exists())
    }

    @Test
    fun detectsSettingsOutsideChainSection() {
        assertTrue(NodeConfigFile.hasForeignSettings(listOf("[main]", "addnode=x"), "test"))
        assertTrue(NodeConfigFile.hasForeignSettings(listOf("addnode=x"), "test"))
        assertFalse(NodeConfigFile.hasForeignSettings(listOf("# c", "", "[test]", "a=1"), "test"))
    }
}
