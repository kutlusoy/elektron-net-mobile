package org.elektronnet.mobile.node

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeConfigTest {
    private fun config(chain: Chain, profile: NodeProfile = NodeProfile.Balanced, fastprune: Boolean = true, listen: Boolean = false) =
        NodeConfig(File("/bin/elektrond"), File("/data/node"), chain, 8332, 8333, profile, fastprune, listen)

    @Test
    fun mainnetHasNoChainArgument() {
        val args = config(Chain.Main).arguments()
        assertFalse(args.any { it == "-testnet" || it == "-regtest" })
        assertTrue(args.contains("-datadir=/data/node"))
        assertTrue(args.contains("-rpcbind=127.0.0.1"))
        assertTrue(args.contains("-rpcallowip=127.0.0.1"))
        assertTrue(args.contains("-rpccookiefile=/data/node/.rpc-cookie"))
        assertTrue(args.contains("-natpmp=0"))
        assertTrue(args.contains("-printtoconsole=1"))
    }

    @Test
    fun testnetUsesTestnetArgument() {
        assertTrue(config(Chain.Testnet).arguments().contains("-testnet"))
    }

    @Test
    fun neverPutsSecretsInArguments() {
        val args = config(Chain.Main).arguments()
        assertFalse(args.any { it.startsWith("-rpcuser") })
        assertTrue(args.filter { it.startsWith("-rpcpassword") } == listOf("-rpcpassword="))
    }

    @Test
    fun profilesMapToPlanValues() {
        assertTrue(config(Chain.Main, NodeProfile.Foreground).arguments().containsAll(listOf("-dbcache=64", "-maxconnections=8", "-maxmempool=30", "-blocksonly=1")))
        assertTrue(config(Chain.Main, NodeProfile.Balanced).arguments().containsAll(listOf("-dbcache=128", "-maxconnections=16", "-maxmempool=50")))
        assertTrue(config(Chain.Main, NodeProfile.AlwaysOn).arguments().containsAll(listOf("-dbcache=256", "-maxconnections=24", "-maxmempool=100")))
        assertFalse(config(Chain.Main, NodeProfile.Balanced).arguments().contains("-blocksonly=1"))
    }

    @Test
    fun fastpruneAndListenAreSwitchable() {
        assertTrue(config(Chain.Main, fastprune = true).arguments().contains("-fastprune"))
        assertFalse(config(Chain.Main, fastprune = false).arguments().contains("-fastprune"))
        assertTrue(config(Chain.Main, listen = true).arguments().contains("-listen=1"))
        assertTrue(config(Chain.Main, listen = false).arguments().contains("-listen=0"))
    }

    @Test
    fun networkActiveIsPassedExplicitly() {
        val base = config(Chain.Main)
        assertTrue(base.arguments().contains("-networkactive=1"))
        assertTrue(base.copy(networkActive = false).arguments().contains("-networkactive=0"))
    }

    @Test
    fun chainFromBuildName() {
        assertEquals(Chain.Main, Chain.fromBuildName("main"))
        assertEquals(Chain.Testnet, Chain.fromBuildName("testnet"))
    }

    @Test
    fun cookieAuthSurvivesCredentialsInTheDefaultConfFile() {
        assertTrue(config(Chain.Main).arguments().contains("-rpcpassword="))
    }
}
