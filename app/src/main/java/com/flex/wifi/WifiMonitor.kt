package com.flex.wifi

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.service.quicksettings.TileService
import android.util.Log
import com.flex.data.local.GeofencePreferences
import com.flex.di.IoDispatcher
import com.flex.domain.usecase.AutoClockInUseCase
import com.flex.domain.usecase.AutoClockOutUseCase
import com.flex.domain.usecase.IsAutoClockedInUseCase
import com.flex.geofence.GeofenceNotificationHelper
import com.flex.notification.BreakWarningScheduler
import com.flex.notification.WorkTimerService
import com.flex.tile.QuickSettingsTileService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val autoClockIn: AutoClockInUseCase,
    private val autoClockOut: AutoClockOutUseCase,
    private val isAutoClockedIn: IsAutoClockedInUseCase,
    private val notificationHelper: GeofenceNotificationHelper,
    private val wifiPreferences: WifiPreferences,
    private val geofencePreferences: GeofencePreferences,
    private val breakWarningScheduler: BreakWarningScheduler,
    private val connectionInfo: WifiConnectionInfo,
    private val scheduler: WifiAutoStampScheduler,
    private val clock: Clock,
    @IoDispatcher dispatcher: CoroutineDispatcher
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var clockOutJob: Job? = null
    private val stateMutex = Mutex()

    @Synchronized
    fun register(targetSsid: String) {
        stopNetworkCallback()
        Log.d("WifiMonitor", "Registering WiFi monitor")
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
        scope.launch { reconcile(targetSsid) }
        scheduler.schedule()
    }

    internal fun buildNetworkCallback(targetSsid: String): ConnectivityManager.NetworkCallback {
        return object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (!connectionInfo.hasLocationPermission()) return
                val ssid = connectionInfo.ssidFrom(capabilities)
                Log.d("WifiMonitor", "WiFi network capabilities changed")
                if (ssid == targetSsid) {
                    // Cancel any pending clock-out — WiFi is (re)connected to target
                    clockOutJob?.cancel()
                    clockOutJob = null
                    if (!wifiPreferences.wasConnectedToTarget) {
                        scope.launch { handleTargetConnected() }
                    }
                }
            }

            override fun onLost(network: Network) {
                Log.d("WifiMonitor", "onLost: wasConnectedToTarget=${wifiPreferences.wasConnectedToTarget}")
                if (!wifiPreferences.wasConnectedToTarget) return
                clockOutJob?.cancel()
                clockOutJob = scope.launch {
                    delay(CLOCK_OUT_GRACE_MS)
                    handleTargetDisconnected()
                }
            }
        }
    }

    /**
     * Compares the expected auto-stamp state with the actual connection.
     *
     * Called immediately after registration and periodically by [WifiAutoStampWorker]. Keeps
     * working after app updates, reboots and process deaths, where the network callback is gone.
     */
    suspend fun reconcile(targetSsid: String) {
        if (!connectionInfo.hasLocationPermission()) return
        val currentSsid = connectionInfo.currentSsid()

        if (currentSsid == targetSsid) {
            stateMutex.withLock {
                if (isAutoClockedIn()) {
                    wifiPreferences.wasConnectedToTarget = true
                } else {
                    wifiPreferences.wasConnectedToTarget = false
                    handleTargetConnectedLocked()
                }
            }
        } else {
            val autoBlockOpen = stateMutex.withLock {
                val open = isAutoClockedIn()
                wifiPreferences.wasConnectedToTarget = open
                open
            }
            if (autoBlockOpen) {
                delay(CLOCK_OUT_GRACE_MS)
                if (connectionInfo.currentSsid() != targetSsid) {
                    handleTargetDisconnected()
                }
            }
        }
    }

    internal suspend fun handleTargetConnected() {
        stateMutex.withLock {
            handleTargetConnectedLocked()
        }
    }

    /** Must be called while [stateMutex] is held. */
    private suspend fun handleTargetConnectedLocked() {
        if (wifiPreferences.wasConnectedToTarget) return
        wifiPreferences.wasConnectedToTarget = true
        val blockId = try {
            autoClockIn()
        } catch (e: Exception) {
            wifiPreferences.wasConnectedToTarget = false
            throw e
        }
        if (blockId == null) {
            // Clock-in skipped (already clocked in) → do not auto clock out later
            wifiPreferences.wasConnectedToTarget = false
            Log.d("WifiMonitor", "Clock-in skipped (already clocked in?), resetting flag")
            return
        }
        geofencePreferences.lastAutoTimeBlockId = blockId
        notificationHelper.showClockInNotification()
        breakWarningScheduler.scheduleWarning(LocalTime.now(clock))
        try {
            TileService.requestListeningState(
                context,
                ComponentName(context, QuickSettingsTileService::class.java)
            )
        } catch (_: Exception) {}
        Log.d("WifiMonitor", "Clocked in via WiFi, blockId=$blockId")
    }

    internal suspend fun handleTargetDisconnected() {
        stateMutex.withLock {
            handleTargetDisconnectedLocked()
        }
    }

    /** Must be called while [stateMutex] is held. */
    private suspend fun handleTargetDisconnectedLocked() {
        if (!wifiPreferences.wasConnectedToTarget) return
        wifiPreferences.wasConnectedToTarget = false
        val clocked = autoClockOut()
        if (clocked) {
            notificationHelper.showClockOutNotification()
            breakWarningScheduler.cancelWarning()
            try {
                context.stopService(Intent(context, WorkTimerService::class.java))
            } catch (_: Exception) {}
            try {
                TileService.requestListeningState(
                    context,
                    ComponentName(context, QuickSettingsTileService::class.java)
                )
            } catch (_: Exception) {}
            Log.d("WifiMonitor", "Clocked out via WiFi")
        } else {
            Log.d("WifiMonitor", "onLost: no running block, skipping notification")
        }
    }

    @Synchronized
    fun unregister() {
        stopNetworkCallback()
        wifiPreferences.wasConnectedToTarget = false
        scheduler.cancel()
    }

    private fun stopNetworkCallback() {
        clockOutJob?.cancel()
        clockOutJob = null
        networkCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {}
            networkCallback = null
            Log.d("WifiMonitor", "Unregistered WiFi monitor")
        }
    }

    private companion object {
        const val CLOCK_OUT_GRACE_MS = 30_000L
    }
}
