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
        assertTrue(config(Chain.Main, NodeProfile.Balanced).arguments().containsAll(listOf("-dbcache=128", "-maxconnections=125", "-maxmempool=50")))
        assertTrue(config(Chain.Main, NodeProfile.AlwaysOn).arguments().containsAll(listOf("-dbcache=256", "-maxconnections=125", "-maxmempool=100")))
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

    @Test
    fun extraArgumentsCannotWeakenTheRpcProtection() {
        val config = NodeConfig(File("/bin/elektrond"), File("/data/node"), Chain.Regtest, 8332, 8333, extraArgs = listOf(
            "-rpcbind=0.0.0.0", "-rpcallowip=0.0.0.0/0", "-norpcbind", "-rpcpassword=x", "-rpcauth=a:b", "-server=0", "-datadir=/tmp/x", "-conf=/tmp/y", "-whitelist=0.0.0.0/0", "-debug=tor",
        ))
        val args = config.arguments()
        assertEquals(1, args.count { it.startsWith("-rpcbind") })
        assertEquals(listOf("-rpcallowip=127.0.0.1"), args.filter { it.startsWith("-rpcallowip") })
        assertEquals(listOf("-rpcpassword="), args.filter { it.startsWith("-rpcpassword") })
        assertTrue(args.none { it.startsWith("-rpcauth") || it.startsWith("-norpc") || it == "-server=0" || it == "-datadir=/tmp/x" || it.startsWith("-conf") || it.startsWith("-whitelist") })
        assertTrue(args.contains("-debug=tor"))
    }

    @Test
    fun theProtectedOptionsAreFoundInEveryWritingStyle() {
        listOf("-RPCBIND=0.0.0.0", "--rpcbind=0.0.0.0", " -rpcallowip=1.2.3.4", "-rpcbind", "-NoRpcBind", "-norpcallowip=0", "-ZMQPubRawTx=tcp://0.0.0.0:1", "-Server=0", "-whitebind=0.0.0.0:1", "rpcuser=x").forEach {
            assertTrue(NodeConfig.protectedOption(it))
        }
        listOf("-debug=tor", "-debug=i2p", "-printtoconsole=1", "-dbcache=64", "-nolisten").forEach {
            assertFalse(NodeConfig.protectedOption(it))
        }
    }
}
