package com.example.mistreal_mini.monitor

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide battery/thermal/network/memory signal source. Features that run
 * continuous loops (screen-cast, GPS polling) read `pressureNow()`/`snapshot`
 * to back off instead of running at a fixed rate regardless of device state.
 *
 * Call `ensureMonitoring()` once from each consumer's entry point — it's
 * idempotent, same guard idiom as WebRtcInitializer.ensureInitialized().
 * Listeners are never unregistered; cost at app-singleton scope is
 * negligible and there's no natural "done" point shared across consumers.
 */
@Singleton
class ResourceMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val batteryManager: BatteryManager,
    private val powerManager: PowerManager,
    private val connectivityManager: ConnectivityManager,
    private val activityManager: ActivityManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _snapshot = MutableStateFlow(ResourceSnapshot.UNKNOWN)
    val snapshot: StateFlow<ResourceSnapshot> = _snapshot.asStateFlow()

    @Volatile private var started = false

    fun pressureNow(): PressureLevel = _snapshot.value.pressure

    @Synchronized
    fun ensureMonitoring() {
        if (started) return
        started = true
        registerBatteryReceiver()
        registerThermalListener()
        registerNetworkCallback()
        startMemoryTicker()
    }

    private fun registerBatteryReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                val percent = if (level >= 0 && scale > 0) (level * 100) / scale else 100
                updateSnapshot { it.copy(batteryPercent = percent, isCharging = plugged != 0) }
            }
        }
        // Sticky intent — registering for it returns the current battery
        // state immediately via the first onReceive, no separate sync read needed.
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun registerThermalListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        powerManager.addThermalStatusListener { status ->
            updateSnapshot { it.copy(thermalStatus = status) }
        }
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val type = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                    else -> NetworkType.OTHER
                }
                val metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                updateSnapshot { it.copy(networkType = type, isNetworkMetered = metered) }
            }

            override fun onLost(network: Network) {
                updateSnapshot { it.copy(networkType = NetworkType.NONE, isNetworkMetered = false) }
            }
        })
    }

    private fun startMemoryTicker() {
        scope.launch {
            val info = ActivityManager.MemoryInfo()
            while (true) {
                activityManager.getMemoryInfo(info)
                updateSnapshot { it.copy(isMemoryLow = info.lowMemory) }
                delay(20_000)
            }
        }
    }

    private fun updateSnapshot(transform: (ResourceSnapshot) -> ResourceSnapshot) {
        _snapshot.update { current ->
            val next = transform(current)
            next.copy(pressure = derivePressure(next))
        }
    }

    private fun derivePressure(s: ResourceSnapshot): PressureLevel {
        val lowBattery = s.batteryPercent <= 15 && !s.isCharging
        val midBattery = s.batteryPercent <= 30 && !s.isCharging
        val highBattery = s.batteryPercent <= 50 && !s.isCharging
        return when {
            s.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE || s.isMemoryLow || lowBattery -> PressureLevel.SEVERE
            s.thermalStatus == PowerManager.THERMAL_STATUS_MODERATE || midBattery -> PressureLevel.MODERATE
            s.thermalStatus == PowerManager.THERMAL_STATUS_LIGHT || highBattery || s.isNetworkMetered -> PressureLevel.MILD
            else -> PressureLevel.NONE
        }
    }
}
