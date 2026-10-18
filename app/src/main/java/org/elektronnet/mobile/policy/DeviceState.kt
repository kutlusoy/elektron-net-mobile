package org.elektronnet.mobile.policy

enum class NetworkType { None, Unmetered, Metered, Roaming }

enum class ThermalLevel { None, Light, Moderate, Severe, Critical }

data class DeviceState(
    val charging: Boolean,
    val batteryPercent: Int,
    val thermal: ThermalLevel,
    val network: NetworkType,
    val dataSaver: Boolean,
    val freeStorageBytes: Long,
    val appVisible: Boolean,
    val minuteOfDay: Int,
    val cellularBytesThisMonth: Long = 0,
)
