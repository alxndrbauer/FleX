package com.flex.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.flex.data.local.GeofencePreferences
import com.flex.di.IoDispatcher
import com.flex.domain.usecase.AutoClockInUseCase
import com.flex.domain.usecase.AutoClockOutUseCase
import com.flex.geofence.GeofenceNotificationHelper
import com.flex.notification.BreakWarningScheduler
import com.flex.wearable.WearSyncHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val autoClockIn: AutoClockInUseCase,
    private val autoClockOut: AutoClockOutUseCase,
    private val notificationHelper: GeofenceNotificationHelper,
    private val wifiPreferences: WifiPreferences,
    private val geofencePreferences: GeofencePreferences,
    private val wearSyncHelper: WearSyncHelper,
    private val breakWarningScheduler: BreakWarningScheduler,
    @IoDispatcher dispatcher: CoroutineDispatcher
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var clockOutJob: Job? = null

    fun register(targetSsid: String) {
        unregister()
        Log.d("WifiMonitor", "Registering WiFi monitor for SSID: $targetSsid")
        networkCallback = buildNetworkCallback(targetSsid)
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.e("WifiMonitor", "Failed to register network callback: ${e.message}")
            networkCallback = null
        }
    }

    internal fun buildNetworkCallback(targetSsid: String): ConnectivityManager.NetworkCallback {
        return object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val ssid = getSsidFromCapabilities(capabilities) ?: getConnectedSsidLegacy()
                Log.d("WifiMonitor", "onCapabilitiesChanged: ssid=$ssid target=$targetSsid connected=${wifiPreferences.wasConnectedToTarget}")
                if (ssid == targetSsid) {
                    // Cancel any pending clock-out — WiFi is (re)connected to target
                    clockOutJob?.cancel()
                    clockOutJob = null
                    if (!wifiPreferences.wasConnectedToTarget) {
                        wifiPreferences.wasConnectedToTarget = true
                        scope.launch {
                            val blockId = autoClockIn()
                            if (blockId != null) {
                                geofencePreferences.lastAutoTimeBlockId = blockId
                                notificationHelper.showClockInNotification()
                                breakWarningScheduler.scheduleWarning(java.time.LocalTime.now())
                                wearSyncHelper.push()
                                Log.d("WifiMonitor", "Clocked in via WiFi, blockId=$blockId")
                            } else {
                                // Clock-in skipped (already clocked in) → reset flag
                                wifiPreferences.wasConnectedToTarget = false
                                Log.d("WifiMonitor", "Clock-in skipped (already clocked in?), resetting flag")
                            }
                        }
                    }
                }
            }

            override fun onLost(network: Network) {
                Log.d("WifiMonitor", "onLost: wasConnectedToTarget=${wifiPreferences.wasConnectedToTarget}")
                if (wifiPreferences.wasConnectedToTarget) {
                    clockOutJob?.cancel()
                    clockOutJob = scope.launch {
                        delay(30_000)
                        wifiPreferences.wasConnectedToTarget = false
                        val clocked = autoClockOut()
                        if (clocked) {
                            notificationHelper.showClockOutNotification()
                            breakWarningScheduler.cancelWarning()
                            wearSyncHelper.push()
                            Log.d("WifiMonitor", "Clocked out via WiFi")
                        } else {
                            Log.d("WifiMonitor", "onLost: no running block, skipping notification")
                        }
                    }
                }
            }
        }
    }

    fun unregister() {
        clockOutJob?.cancel()
        clockOutJob = null
        networkCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {}
            networkCallback = null
            wifiPreferences.wasConnectedToTarget = false
            Log.d("WifiMonitor", "Unregistered WiFi monitor")
        }
    }

    @SuppressLint("MissingPermission")
    private fun getSsidFromCapabilities(capabilities: NetworkCapabilities): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val wifiInfo = capabilities.transportInfo as? WifiInfo ?: return null
            return wifiInfo.ssid?.removeSurrounding("\"")
                ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun getConnectedSsidLegacy(): String? {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        return wifiManager.connectionInfo?.ssid?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }
}
