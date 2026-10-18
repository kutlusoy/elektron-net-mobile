package org.elektronnet.mobile.service

import android.content.Context
import java.time.YearMonth
import org.elektronnet.mobile.node.Json
import org.elektronnet.mobile.ui.model.UiSettings
import org.elektronnet.mobile.node.NodeProfile
import org.elektronnet.mobile.wallet.EscrowRecord
import org.elektronnet.mobile.wallet.EscrowStore
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
        )
    }

    fun walletReady(): Boolean = prefs.getBoolean(WALLET_READY, false)

    fun setWalletReady(value: Boolean) = prefs.edit().putBoolean(WALLET_READY, value).apply()

    var receiveAddress: String?
        get() = prefs.getString(RECEIVE_ADDRESS, null)
        set(value) = prefs.edit().putString(RECEIVE_ADDRESS, value).apply()

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
        UiSettings.restore(prefs.getString(THEME_KEY, null), prefs.getString(THEME_MODE, null), prefs.getString(EFFECTS, null), validKeys)

    fun saveUi(ui: UiSettings) {
        prefs.edit().putString(THEME_KEY, ui.themeKey).putString(THEME_MODE, ui.mode.name).putString(EFFECTS, ui.effects.name).apply()
    }

    fun fastprune(): Boolean = prefs.getBoolean(FASTPRUNE, true)

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
        const val EFFECTS = "effects"
        const val RESCAN_PENDING = "rescan_pending"
        const val ESCROWS = "escrows"
        const val RECEIVE_ADDRESS = "receive_address"
        const val BUDGET_MONTH = "budget_month"
        const val BUDGET_USED = "budget_used"
    }
}
