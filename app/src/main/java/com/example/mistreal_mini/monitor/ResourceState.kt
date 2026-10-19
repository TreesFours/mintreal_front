package com.example.mistreal_mini.monitor

import android.os.PowerManager

enum class PressureLevel { NONE, MILD, MODERATE, SEVERE }

enum class NetworkType { NONE, WIFI, CELLULAR, OTHER }

data class ResourceSnapshot(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = true,
    val thermalStatus: Int = PowerManager.THERMAL_STATUS_NONE,
    val networkType: NetworkType = NetworkType.NONE,
    val isNetworkMetered: Boolean = false,
    val isMemoryLow: Boolean = false,
    val pressure: PressureLevel = PressureLevel.NONE,
) {
    companion object {
        val UNKNOWN = ResourceSnapshot()
    }
}
