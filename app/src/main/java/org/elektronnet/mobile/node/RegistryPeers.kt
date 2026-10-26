package org.elektronnet.mobile.node

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import kotlin.random.Random

// Stepping stones for the first contact on Tor and I2P: the public lists torpeers.txt and i2ppeers.txt of the registry repository
object RegistryPeers {
    const val BASE_URL = "https://raw.githubusercontent.com/kutlusoy/elektron-net-registry/main/"
    const val TOR_URL = BASE_URL + "torpeers.txt"
    const val I2P_URL = BASE_URL + "i2ppeers.txt"
    const val REFRESH_MS = 24L * 60 * 60 * 1000
    const val RETRY_MS = 15L * 60 * 1000
    const val CONTACT_EVERY_MS = 10L * 60 * 1000
    const val WANTED_PEERS = 2
    const val TRY_PER_ROUND = 3
    private const val MAX_ENTRIES = 200
    private const val MAX_BYTES = 64 * 1024

    // one address per line; blank lines, comments and anything that is not an address of the wanted network are skipped
    fun parse(text: String, onion: Boolean): List<String> = text.lineSequence()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull(PeerAddress::parse)
        .filter { if (onion) PeerAddress.isOnion(it) else PeerAddress.isI2p(it) }
        .distinct()
        .take(MAX_ENTRIES)
        .toList()

    fun pick(entries: List<String>, count: Int, random: Random = Random.Default): List<String> = entries.shuffled(random).take(count)

    // the list is fetched again after a day, after a failed fetch the next try comes after a quarter of an hour
    fun refreshDue(cached: Boolean, lastOkMs: Long, lastAttemptMs: Long, nowMs: Long): Boolean =
        (!cached || nowMs - lastOkMs >= REFRESH_MS) && nowMs - lastAttemptMs >= RETRY_MS

    // the registry is only used while the node has hardly any peer on that network
    fun needed(peers: List<PeerInfo>, network: String): Boolean = peers.count { it.network == network } < WANTED_PEERS

    fun contactDue(lastContactMs: Long, nowMs: Long): Boolean = nowMs - lastContactMs >= CONTACT_EVERY_MS

    // proxy: the SOCKS port of the own Tor, so that the registry does not see the address of the phone
    fun fetch(url: String, proxyPort: Int?, timeoutMs: Int = 15_000): String {
        require(url.startsWith(BASE_URL)) { "registry only" }
        val target = URL(url)
        val connection = (if (proxyPort != null) target.openConnection(Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(NetworkArgs.LOOPBACK, proxyPort))) else target.openConnection()) as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "ElektronMobile")
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.use { stream ->
                val out = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var left = MAX_BYTES
                while (left > 0) {
                    val n = stream.read(chunk, 0, minOf(chunk.size, left))
                    if (n < 0) break
                    out.write(chunk, 0, n)
                    left -= n
                }
                out.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }
}

interface RegistryStore {
    fun list(network: String): List<String>
    fun saveList(network: String, list: List<String>, nowMs: Long)
    fun lastOk(network: String): Long
    fun lastAttempt(network: String): Long
    fun setAttempt(network: String, nowMs: Long)
    fun lastContact(network: String): Long
    fun setContact(network: String, nowMs: Long)
}

class RegistryBootstrap(
    private val store: RegistryStore,
    private val fetch: (url: String, proxyPort: Int?) -> String,
    private val peers: () -> List<PeerInfo>,
    private val connect: (String) -> Boolean,
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    // one round: refresh the lists that are due, then give a node without peers on Tor or I2P a few stepping stones
    fun step(network: NetworkSettings, torSocksPort: Int) {
        if (network.tor.enabled) round("onion", RegistryPeers.TOR_URL, true, torSocksPort)
        if (network.i2p.enabled) round("i2p", RegistryPeers.I2P_URL, false, if (network.tor.enabled) torSocksPort else null)
    }

    private fun round(name: String, url: String, onion: Boolean, proxyPort: Int?) {
        val now = clock()
        if (RegistryPeers.refreshDue(store.list(name).isNotEmpty(), store.lastOk(name), store.lastAttempt(name), now)) {
            store.setAttempt(name, now)
            runCatching { RegistryPeers.parse(fetch(url, proxyPort), onion) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { store.saveList(name, it, now) }
        }
        val entries = store.list(name)
        if (entries.isEmpty() || !RegistryPeers.contactDue(store.lastContact(name), now)) return
        val current = runCatching { peers() }.getOrNull() ?: return
        if (!RegistryPeers.needed(current, name)) return
        store.setContact(name, now)
        RegistryPeers.pick(entries, RegistryPeers.TRY_PER_ROUND, random).forEach { connect(it) }
    }
}
