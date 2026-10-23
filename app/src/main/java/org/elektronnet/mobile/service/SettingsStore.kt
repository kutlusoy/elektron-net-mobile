package org.elektronnet.mobile.service

import android.content.Context
import org.elektronnet.mobile.wallet.AmountUnit
import java.time.YearMonth
import org.elektronnet.mobile.node.Json
import org.elektronnet.mobile.ui.model.UiSettings
import org.elektronnet.mobile.node.ExitPolicyKind
import org.elektronnet.mobile.node.I2pSettings
import org.elektronnet.mobile.node.NetworkSettings
import org.elektronnet.mobile.node.NodeProfile
import org.elektronnet.mobile.node.TorRole
import org.elektronnet.mobile.node.TorRouting
import org.elektronnet.mobile.node.TorSettings
import org.elektronnet.mobile.wallet.EscrowRecord
import org.elektronnet.mobile.wallet.EscrowStore
import org.elektronnet.mobile.wallet.WalletBootstrap
import org.elektronnet.mobile.wallet.WalletRegistryStore
import org.elektronnet.mobile.policy.MonthlyDataBudget
import org.elektronnet.mobile.policy.PolicySettings
import org.elektronnet.mobile.policy.ScheduleWindow

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("policy", Context.MODE_PRIVATE)

    fun load(): PolicySettings {
        val defaults = PolicySettings()
        val start = prefs.getInt(SCHEDULE_START, -1)
        val end = prefs.getInt(SCHEDULE_END, -1)
        val cap = prefs.getLong(CAP, -1)
        return PolicySettings(
            userStopped = prefs.getBoolean(USER_STOPPED, defaults.userStopped),
            userPaused = prefs.getBoolean(USER_PAUSED, defaults.userPaused),
            backgroundEnabled = prefs.getBoolean(BACKGROUND, defaults.backgroundEnabled),
            profile = runCatching { NodeProfile.valueOf(prefs.getString(PROFILE, null) ?: "") }.getOrDefault(defaults.profile),
            chargingOnly = prefs.getBoolean(CHARGING_ONLY, defaults.chargingOnly),
            allowMetered = prefs.getBoolean(ALLOW_METERED, defaults.allowMetered),
            allowRoaming = prefs.getBoolean(ALLOW_ROAMING, defaults.allowRoaming),
            monthlyCellularCapBytes = if (cap >= 0) cap else null,
            allowInbound = prefs.getBoolean(ALLOW_INBOUND, defaults.allowInbound),
            serveSnapshots = prefs.getBoolean(SERVE_SNAPSHOTS, defaults.serveSnapshots),
            storageFloorBytes = prefs.getLong(STORAGE_FLOOR, defaults.storageFloorBytes),
            schedule = if (start >= 0 && end >= 0) ScheduleWindow(start, end) else null,
            network = loadNetwork(),
        )
    }

    fun loadNetwork(): NetworkSettings {
        val tor = TorSettings()
        val i2p = I2pSettings()
        return NetworkSettings(
            tor = TorSettings(
                enabled = prefs.getBoolean(TOR_ENABLED, tor.enabled),
                routing = enumOf(prefs.getString(TOR_ROUTING, null), tor.routing),
                role = enumOf(prefs.getString(TOR_ROLE, null), tor.role),
                orPort = prefs.getInt(TOR_OR_PORT, tor.orPort),
                nickname = prefs.getString(TOR_NICKNAME, null) ?: tor.nickname,
                contact = prefs.getString(TOR_CONTACT, null) ?: tor.contact,
                bandwidthKbps = prefs.getInt(TOR_BANDWIDTH, tor.bandwidthKbps),
                monthlyLimitGb = prefs.getInt(TOR_LIMIT, tor.monthlyLimitGb),
                exitPolicy = enumOf(prefs.getString(TOR_EXIT_POLICY, null), tor.exitPolicy),
            ),
            i2p = I2pSettings(
                enabled = prefs.getBoolean(I2P_ENABLED, i2p.enabled),
                acceptIncoming = prefs.getBoolean(I2P_ACCEPT, i2p.acceptIncoming),
                transit = prefs.getBoolean(I2P_TRANSIT, i2p.transit),
                bandwidthKbps = prefs.getInt(I2P_BANDWIDTH, i2p.bandwidthKbps),
                sharePercent = prefs.getInt(I2P_SHARE, i2p.sharePercent),
            ),
            peers = manualPeers,
        ).normalized()
    }

    // kept apart from the network switches so saving those never overwrites a peer added meanwhile
    var manualPeers: List<String>
        get() = runCatching { (Json.parse(prefs.getString(NET_PEERS, null) ?: "[]") as List<*>).mapNotNull { it as? String } }.getOrDefault(emptyList())
        set(value) = prefs.edit().putString(NET_PEERS, Json.stringify(value)).apply()

    private inline fun <reified T : Enum<T>> enumOf(name: String?, default: T): T = enumValues<T>().firstOrNull { it.name == name } ?: default

    fun walletReady(): Boolean = prefs.getBoolean(WALLET_READY, false)

    fun setWalletReady(value: Boolean) = prefs.edit().putBoolean(WALLET_READY, value).apply()

    var receiveAddress: String?
        get() = prefs.getString(RECEIVE_ADDRESS, null)
        set(value) = prefs.edit().putString(RECEIVE_ADDRESS, value).apply()

    fun receiveAddressFor(wallet: String): String? =
        if (wallet == WalletBootstrap.DEFAULT_WALLET) receiveAddress else prefs.getString("$RECEIVE_ADDRESS.$wallet", null)

    fun setReceiveAddressFor(wallet: String, value: String?) {
        if (wallet == WalletBootstrap.DEFAULT_WALLET) receiveAddress = value else prefs.edit().putString("$RECEIVE_ADDRESS.$wallet", value).apply()
    }

    fun walletRegistryStore(): WalletRegistryStore = object : WalletRegistryStore {
        override var json: String?
            get() = prefs.getString(WALLETS, null)
            set(value) = prefs.edit().putString(WALLETS, value).apply()
        override var active: String?
            get() = prefs.getString(ACTIVE_WALLET, null)
            set(value) = prefs.edit().putString(ACTIVE_WALLET, value).apply()
    }

    var rescanWallet: String
        get() = prefs.getString(RESCAN_WALLET, null) ?: WalletBootstrap.DEFAULT_WALLET
        set(value) = prefs.edit().putString(RESCAN_WALLET, value).apply()

    fun escrowStore(): EscrowStore = object : EscrowStore {
        override fun load(): List<EscrowRecord> {
            val text = prefs.getString(ESCROWS, null) ?: return emptyList()
            return runCatching { (Json.parse(text) as List<*>).map { EscrowRecord.fromMap(it as Map<*, *>) } }.getOrDefault(emptyList())
        }

        override fun save(records: List<EscrowRecord>) {
            prefs.edit().putString(ESCROWS, Json.stringify(records.map { it.toMap() })).apply()
        }
    }

    var rescanPending: Boolean
        get() = prefs.getBoolean(RESCAN_PENDING, false)
        set(value) = prefs.edit().putBoolean(RESCAN_PENDING, value).apply()

    fun loadUi(validKeys: Set<String>): UiSettings =
        UiSettings.restore(prefs.getString(THEME_KEY, null), prefs.getString(THEME_MODE, null), validKeys)

    fun saveUi(ui: UiSettings) {
        prefs.edit().putString(THEME_KEY, ui.themeKey).putString(THEME_MODE, ui.mode.name).apply()
    }

    var amountUnit: AmountUnit
        get() = AmountUnit.entries.firstOrNull { it.name == prefs.getString(AMOUNT_UNIT, null) } ?: AmountUnit.Elek
        set(value) = prefs.edit().putString(AMOUNT_UNIT, value.name).apply()

    var fiat: org.elektronnet.mobile.fiat.FiatSettings
        get() = org.elektronnet.mobile.fiat.FiatSettings.decode(prefs.getString(FIAT, null))
        set(value) = prefs.edit().putString(FIAT, value.encode()).apply()

    var spendReused: Boolean
        get() = prefs.getBoolean(SPEND_REUSED, false)
        set(value) = prefs.edit().putBoolean(SPEND_REUSED, value).apply()

    var nodeOnly: Boolean
        get() = prefs.getBoolean(NODE_ONLY, false)
        set(value) = prefs.edit().putBoolean(NODE_ONLY, value).apply()

    var lockGraceMs: Long
        get() = prefs.getLong(LOCK_GRACE, 30_000L)
        set(value) = prefs.edit().putLong(LOCK_GRACE, value).apply()

    var biometricLock: Boolean
        get() = prefs.getBoolean(BIOMETRIC, false)
        set(value) = prefs.edit().putBoolean(BIOMETRIC, value).apply()

    var explorers: List<String>
        get() = org.elektronnet.mobile.explorer.ExplorerList.parse(prefs.getString(EXPLORERS, null))
        set(value) = prefs.edit().putString(EXPLORERS, org.elektronnet.mobile.explorer.ExplorerList.serialize(value)).apply()

    var notifyIncoming: Boolean
        get() = prefs.getBoolean(NOTIFY_INCOMING, false)
        set(value) = prefs.edit().putBoolean(NOTIFY_INCOMING, value).apply()

    fun seenIncoming(wallet: String): List<String>? = prefs.getString("$SEEN_INCOMING.$wallet", null)?.split(',')?.filter { it.isNotEmpty() }

    fun setSeenIncoming(wallet: String, value: List<String>?) =
        prefs.edit().apply { if (value == null) remove("$SEEN_INCOMING.$wallet") else putString("$SEEN_INCOMING.$wallet", value.joinToString(",")) }.apply()

    var consoleRetentionDays: Int
        get() = prefs.getInt(CONSOLE_RETENTION, 90)
        set(value) = prefs.edit().putInt(CONSOLE_RETENTION, value).apply()

    fun fastprune(): Boolean = prefs.getBoolean(FASTPRUNE, true)

    fun setFastprune(value: Boolean) = prefs.edit().putBoolean(FASTPRUNE, value).apply()

    fun savePolicy(settings: PolicySettings) {
        prefs.edit()
            .putString(PROFILE, settings.profile.name)
            .putBoolean(ALLOW_ROAMING, settings.allowRoaming)
            .putLong(CAP, settings.monthlyCellularCapBytes ?: -1)
            .putBoolean(ALLOW_INBOUND, settings.allowInbound)
            .putBoolean(SERVE_SNAPSHOTS, settings.serveSnapshots)
            .putLong(STORAGE_FLOOR, settings.storageFloorBytes)
            .putInt(SCHEDULE_START, settings.schedule?.startMinute ?: -1)
            .putInt(SCHEDULE_END, settings.schedule?.endMinute ?: -1)
            .apply()
        saveNetwork(settings.network)
    }

    private fun saveNetwork(network: NetworkSettings) {
        val n = network.normalized()
        prefs.edit()
            .putBoolean(TOR_ENABLED, n.tor.enabled)
            .putString(TOR_ROUTING, n.tor.routing.name)
            .putString(TOR_ROLE, n.tor.role.name)
            .putInt(TOR_OR_PORT, n.tor.orPort)
            .putString(TOR_NICKNAME, n.tor.nickname)
            .putString(TOR_CONTACT, n.tor.contact)
            .putInt(TOR_BANDWIDTH, n.tor.bandwidthKbps)
            .putInt(TOR_LIMIT, n.tor.monthlyLimitGb)
            .putString(TOR_EXIT_POLICY, n.tor.exitPolicy.name)
            .putBoolean(I2P_ENABLED, n.i2p.enabled)
            .putBoolean(I2P_ACCEPT, n.i2p.acceptIncoming)
            .putBoolean(I2P_TRANSIT, n.i2p.transit)
            .putInt(I2P_BANDWIDTH, n.i2p.bandwidthKbps)
            .putInt(I2P_SHARE, n.i2p.sharePercent)
            .apply()
    }

    fun setUserStopped(value: Boolean) = prefs.edit().putBoolean(USER_STOPPED, value).apply()

    fun setUserPaused(value: Boolean) = prefs.edit().putBoolean(USER_PAUSED, value).apply()

    fun setAllowMetered(value: Boolean) = prefs.edit().putBoolean(ALLOW_METERED, value).apply()

    fun setChargingOnly(value: Boolean) = prefs.edit().putBoolean(CHARGING_ONLY, value).apply()

    fun setBackgroundEnabled(value: Boolean) = prefs.edit().putBoolean(BACKGROUND, value).apply()

    fun loadBudget(): MonthlyDataBudget {
        val month = prefs.getString(BUDGET_MONTH, null)?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        return MonthlyDataBudget(month = month, used = prefs.getLong(BUDGET_USED, 0))
    }

    fun saveBudget(budget: MonthlyDataBudget) {
        prefs.edit().putString(BUDGET_MONTH, budget.month?.toString()).putLong(BUDGET_USED, budget.usedBytes).apply()
    }

    private companion object {
        const val USER_STOPPED = "user_stopped"
        const val USER_PAUSED = "user_paused"
        const val BACKGROUND = "background_enabled"
        const val PROFILE = "profile"
        const val CHARGING_ONLY = "charging_only"
        const val ALLOW_METERED = "allow_metered"
        const val ALLOW_ROAMING = "allow_roaming"
        const val CAP = "monthly_cellular_cap"
        const val ALLOW_INBOUND = "allow_inbound"
        const val SERVE_SNAPSHOTS = "serve_snapshots"
        const val STORAGE_FLOOR = "storage_floor"
        const val SCHEDULE_START = "schedule_start"
        const val SCHEDULE_END = "schedule_end"
        const val FASTPRUNE = "fastprune"
        const val WALLET_READY = "wallet_ready"
        const val THEME_KEY = "theme_key"
        const val THEME_MODE = "theme_mode"
        const val BIOMETRIC = "biometric_lock"
        const val CONSOLE_RETENTION = "console_retention_days"
        const val EXPLORERS = "explorers"
        const val WALLETS = "wallets"
        const val ACTIVE_WALLET = "active_wallet"
        const val RESCAN_WALLET = "rescan_wallet"
        const val RESCAN_PENDING = "rescan_pending"
        const val ESCROWS = "escrows"
        const val RECEIVE_ADDRESS = "receive_address"
        const val LOCK_GRACE = "lock_grace_ms"
        const val AMOUNT_UNIT = "amount_unit"
        const val FIAT = "fiat"
        const val SPEND_REUSED = "spend_reused"
        const val NET_PEERS = "net_peers"
        const val NODE_ONLY = "node_only"
        const val NOTIFY_INCOMING = "notify_incoming"
        const val SEEN_INCOMING = "seen_incoming"
        const val TOR_ENABLED = "tor_enabled"
        const val TOR_ROUTING = "tor_routing"
        const val TOR_ROLE = "tor_role"
        const val TOR_OR_PORT = "tor_or_port"
        const val TOR_NICKNAME = "tor_nickname"
        const val TOR_CONTACT = "tor_contact"
        const val TOR_BANDWIDTH = "tor_bandwidth"
        const val TOR_LIMIT = "tor_limit_gb"
        const val TOR_EXIT_POLICY = "tor_exit_policy"
        const val I2P_ENABLED = "i2p_enabled"
        const val I2P_ACCEPT = "i2p_accept"
        const val I2P_TRANSIT = "i2p_transit"
        const val I2P_BANDWIDTH = "i2p_bandwidth"
        const val I2P_SHARE = "i2p_share"
        const val BUDGET_MONTH = "budget_month"
        const val BUDGET_USED = "budget_used"
    }
}
