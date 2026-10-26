package org.elektronnet.mobile.node

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistryPeersTest {
    private val tor = "yr3vxmifwm5z7ukcfexf2idk3eud2zimilyxoa3rxkfyxspjhizczsqd.onion:8333\nkabp527z72xrtzewimdbh22hpc2zwhdri6yvpinw2vzqur7ioqhcqoid.onion:8333"
    private val i2p = "aomzpobs3m5n3g64j3tvpqegn7o5qeaazgok74uruczxbqep3hxq.b32.i2p:8333\n" +
        "jzdvgthwvwambs4ruyli7eadhcncyrln4f5cbfwyajhouldohzwa.b32.i2p\nmakaqdsa3qb7nwkzk5exn3sj3jjvut5cuk6xlxeerpago2qq4xgq.b32.i2p"

    @Test
    fun theRegistryFilesAreRead() {
        assertEquals(2, RegistryPeers.parse(tor, true).size)
        assertEquals(listOf("yr3vxmifwm5z7ukcfexf2idk3eud2zimilyxoa3rxkfyxspjhizczsqd.onion:8333", "kabp527z72xrtzewimdbh22hpc2zwhdri6yvpinw2vzqur7ioqhcqoid.onion:8333"), RegistryPeers.parse(tor, true))
        assertEquals(3, RegistryPeers.parse(i2p, false).size)
        assertTrue(RegistryPeers.parse(i2p, false).all { it.endsWith(".b32.i2p:0") })
    }

    @Test
    fun foreignOrBrokenLinesAreSkipped() {
        val text = "# comment\n\n  YR3VXMIFWM5Z7UKCFEXF2IDK3EUD2ZIMILYXOA3RXKFYXSPJHIZCZSQD.onion  \n1.2.3.4:8333\nshort.onion\n" +
            "kabp527z72xrtzewimdbh22hpc2zwhdri6yvpinw2vzqur7ioqhcqoid.onion:99999\n" + i2p + "\nyr3vxmifwm5z7ukcfexf2idk3eud2zimilyxoa3rxkfyxspjhizczsqd.onion\n<script>"
        assertEquals(listOf("yr3vxmifwm5z7ukcfexf2idk3eud2zimilyxoa3rxkfyxspjhizczsqd.onion"), RegistryPeers.parse(text, true))
        assertEquals(3, RegistryPeers.parse(text, false).size)
        assertEquals(emptyList<String>(), RegistryPeers.parse("", true))
        assertEquals(RegistryPeers.parse((1..300).joinToString("\n") { n -> "a".repeat(52) + "%04d".format(n).map { "abcdefghij"[it - '0'] }.joinToString("") + ".onion" }, true).size, 200)
    }

    @Test
    fun timingRules() {
        val day = RegistryPeers.REFRESH_MS
        assertTrue(RegistryPeers.refreshDue(false, 0, 0, 1_000_000))
        assertFalse(RegistryPeers.refreshDue(false, 0, 1_000_000 - 1000, 1_000_000))
        assertFalse(RegistryPeers.refreshDue(true, 1_000, 2_000, day / 2))
        assertTrue(RegistryPeers.refreshDue(true, 1_000, 2_000, day + 1_000_000))
        assertFalse(RegistryPeers.contactDue(1_000, 1_000 + RegistryPeers.CONTACT_EVERY_MS - 1))
        assertTrue(RegistryPeers.contactDue(1_000, 1_000 + RegistryPeers.CONTACT_EVERY_MS))
    }

    private fun peer(id: Long, network: String) = PeerInfo(id, "x", false, network, "", null, null, "outbound-full-relay")

    @Test
    fun neededOnlyWithFewPeersOnThatNetwork() {
        assertTrue(RegistryPeers.needed(emptyList(), "onion"))
        assertTrue(RegistryPeers.needed(listOf(peer(1, "ipv4"), peer(2, "ipv4"), peer(3, "onion")), "onion"))
        assertFalse(RegistryPeers.needed(listOf(peer(1, "onion"), peer(2, "onion")), "onion"))
        assertTrue(RegistryPeers.needed(listOf(peer(1, "onion"), peer(2, "onion")), "i2p"))
    }

    private class Store : RegistryStore {
        val lists = mutableMapOf<String, List<String>>()
        val ok = mutableMapOf<String, Long>()
        val attempt = mutableMapOf<String, Long>()
        val contact = mutableMapOf<String, Long>()
        override fun list(network: String) = lists[network].orEmpty()
        override fun saveList(network: String, list: List<String>, nowMs: Long) { lists[network] = list; ok[network] = nowMs }
        override fun lastOk(network: String) = ok[network] ?: 0L
        override fun lastAttempt(network: String) = attempt[network] ?: 0L
        override fun setAttempt(network: String, nowMs: Long) { attempt[network] = nowMs }
        override fun lastContact(network: String) = contact[network] ?: 0L
        override fun setContact(network: String, nowMs: Long) { contact[network] = nowMs }
    }

    private fun both(torOn: Boolean = true, i2pOn: Boolean = true) = NetworkSettings(TorSettings(enabled = torOn), I2pSettings(enabled = i2pOn))

    @Test
    fun aFreshNodeFetchesTheListsAndContactsSomeEntries() {
        val store = Store()
        val fetched = mutableListOf<Pair<String, Int?>>()
        val connected = mutableListOf<String>()
        val boot = RegistryBootstrap(store, { url, port -> fetched.add(url to port); if (url == RegistryPeers.TOR_URL) tor else i2p }, { emptyList() }, { connected.add(it); true }, Random(1)) { 1_000_000_000L }
        boot.step(both(), 9050)
        assertEquals(listOf(RegistryPeers.TOR_URL to 9050, RegistryPeers.I2P_URL to 9050), fetched)
        assertEquals(2 + 3, connected.size)
        assertTrue(connected.filter { PeerAddress.isOnion(it) }.size == 2 && connected.filter { PeerAddress.isI2p(it) }.size == 3)
        assertEquals(2, store.list("onion").size)
    }

    @Test
    fun withoutTorTheI2pListIsFetchedDirectlyAndTorIsLeftAlone() {
        val store = Store()
        val fetched = mutableListOf<Pair<String, Int?>>()
        RegistryBootstrap(store, { url, port -> fetched.add(url to port); i2p }, { emptyList() }, { true }, Random(1)) { 5_000_000_000L }.step(both(torOn = false), 9050)
        assertEquals(listOf(RegistryPeers.I2P_URL to null), fetched)
        assertEquals(emptyList<String>(), store.list("onion"))
    }

    @Test
    fun noRegistryWhenTheNodeAlreadyHasPeersAndNoContactTooOften() {
        val store = Store()
        store.lists["onion"] = RegistryPeers.parse(tor, true)
        store.ok["onion"] = 1_000_000_000L
        var connects = 0
        val full = listOf(peer(1, "onion"), peer(2, "onion"))
        RegistryBootstrap(store, { _, _ -> error("not due") }, { full }, { connects++; true }, Random(1)) { 1_000_000_100L }.step(both(i2pOn = false), 9050)
        assertEquals(0, connects)
        val boot = RegistryBootstrap(store, { _, _ -> error("not due") }, { emptyList() }, { connects++; true }, Random(1)) { 1_000_000_100L }
        boot.step(both(i2pOn = false), 9050)
        assertEquals(2, connects)
        boot.step(both(i2pOn = false), 9050)
        assertEquals(2, connects)
    }

    @Test
    fun aFailedFetchKeepsTheOldListAndRetriesLater() {
        val store = Store()
        store.lists["onion"] = RegistryPeers.parse(tor, true)
        store.ok["onion"] = 1L
        var fetches = 0
        var now = 10_000_000_000L
        val boot = RegistryBootstrap(store, { _, _ -> fetches++; throw java.io.IOException("offline") }, { listOf(peer(1, "onion"), peer(2, "onion")) }, { true }, Random(1)) { now }
        boot.step(both(i2pOn = false), 9050)
        boot.step(both(i2pOn = false), 9050)
        assertEquals(1, fetches)
        assertEquals(2, store.list("onion").size)
        now += RegistryPeers.RETRY_MS
        boot.step(both(i2pOn = false), 9050)
        assertEquals(2, fetches)
        val garbage = RegistryBootstrap(store, { _, _ -> "<html>404</html>" }, { listOf(peer(1, "onion"), peer(2, "onion")) }, { true }, Random(1)) { now + 10 * RegistryPeers.RETRY_MS }
        garbage.step(both(i2pOn = false), 9050)
        assertEquals(2, store.list("onion").size)
    }
}
