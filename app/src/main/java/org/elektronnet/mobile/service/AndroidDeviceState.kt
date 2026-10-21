package org.elektronnet.mobile.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import java.util.Calendar
import org.elektronnet.mobile.policy.DeviceState
import org.elektronnet.mobile.policy.NetworkType
import org.elektronnet.mobile.policy.ThermalLevel

class AndroidDeviceState(private val context: Context, private val dataDir: java.io.File) {
    companion object {
        fun dnsServers(context: Context): List<String> {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val properties = manager.getLinkProperties(manager.activeNetwork) ?: return emptyList()
            return properties.dnsServers.mapNotNull { it.hostAddress }.filter { !it.contains(':') }
        }
    }

    fun read(appVisible: Boolean): DeviceState {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else 100
        val charging = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val now = Calendar.getInstance()
        return DeviceState(
            charging = charging,
            batteryPercent = percent,
            thermal = thermal(),
            network = network(),
            dataSaver = dataSaver(),
            freeStorageBytes = StatFs(dataDir.absolutePath).availableBytes,
            appVisible = appVisible,
            minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE),
        )
    }

    private fun thermal(): ThermalLevel {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ThermalLevel.None
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return when (power.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> ThermalLevel.None
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.Light
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.Moderate
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.Severe
            else -> ThermalLevel.Critical
        }
    }

    private fun network(): NetworkType {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = manager.activeNetwork ?: return NetworkType.None
        val caps = manager.getNetworkCapabilities(active) ?: return NetworkType.None
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return NetworkType.None
        return when {
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING) -> NetworkType.Roaming
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkType.Unmetered
            else -> NetworkType.Metered
        }
    }

    private fun dataSaver(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return manager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
    }
}
