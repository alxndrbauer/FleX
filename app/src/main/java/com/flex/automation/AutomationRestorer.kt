package com.flex.automation

import android.util.Log
import com.flex.data.local.GeofencePreferences
import com.flex.domain.model.Settings
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.geofence.GeofenceManager
import com.flex.wifi.WifiMonitor
import com.google.android.gms.location.GeofencingRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Restores location/WiFi based automation after process starts, reboots and app updates.
 *
 * Geofences live in Play services and usually survive an app update, but Play Services can
 * silently drop them on location toggles, Doze/background restrictions or Play Services updates.
 * WiFi callbacks live in the app process and are always lost when the process dies.
 */
@Singleton
class AutomationRestorer @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val geofenceManager: GeofenceManager,
    private val wifiMonitor: WifiMonitor,
    private val geofencePreferences: GeofencePreferences,
    private val workDayRepository: WorkDayRepository,
    private val clock: Clock
) {
    private val mutex = Mutex()

    suspend fun restore(
        initialGeofenceTrigger: Int = 0,
        source: String = "Wiederherstellung"
    ) {
        mutex.withLock {
            val settings = settingsRepository.getSettings().first()

            runCatching { restoreWifi(settings) }
                .onFailure { Log.e(TAG, "Failed to restore WiFi monitor: ${it.message}", it) }

            runCatching { restoreGeofence(settings, initialGeofenceTrigger, source) }
                .onFailure { Log.e(TAG, "Failed to restore geofence: ${it.message}", it) }
        }
    }

    suspend fun restoreSmart(source: String = "App-Start") {
        mutex.withLock {
            val settings = settingsRepository.getSettings().first()

            runCatching { restoreWifi(settings) }
                .onFailure { Log.e(TAG, "Failed to restore WiFi monitor: ${it.message}", it) }

            val trigger = smartInitialTrigger()
            runCatching { restoreGeofence(settings, trigger, source) }
                .onFailure { Log.e(TAG, "Failed to restore geofence: ${it.message}", it) }
        }
    }

    suspend fun restoreGeofenceSmart(source: String) {
        mutex.withLock {
            val settings = settingsRepository.getSettings().first()
            val trigger = smartInitialTrigger()
            runCatching { restoreGeofence(settings, trigger, source) }
                .onFailure { Log.e(TAG, "Failed to restore geofence: ${it.message}", it) }
        }
    }

    suspend fun healGeofenceIfStale(maxAgeMs: Long = STALE_THRESHOLD_MS) {
        val settings = settingsRepository.getSettings().first()
        if (!settings.geofenceEnabled || settings.geofenceLat == 0.0 || settings.geofenceLon == 0.0) {
            return
        }
        val registration = geofencePreferences.registration.value
        val ageMs = registration.atEpochMs?.let { clock.millis() - it }
        if (registration.success == true && ageMs != null && ageMs < maxAgeMs) {
            return
        }
        restoreGeofenceSmart(source = "Vordergrund")
    }

    private suspend fun smartInitialTrigger(): Int {
        val today = LocalDate.now(clock)
        val existingDay = workDayRepository.getWorkDay(today).first()
        val hasClockedBlockToday = existingDay?.timeBlocks?.any { !it.isDuration } == true
        return if (hasClockedBlockToday) 0 else GeofencingRequest.INITIAL_TRIGGER_ENTER
    }

    private suspend fun restoreGeofence(settings: Settings, initialTrigger: Int, source: String) {
        if (settings.geofenceEnabled && settings.geofenceLat != 0.0 && settings.geofenceLon != 0.0) {
            Log.d(TAG, "Re-registering geofence (source=$source, initialTrigger=$initialTrigger)")
            geofenceManager.scheduleMorningCheck(settings.defaultStartTime)
            withTimeoutOrNull(GEOFENCE_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    geofenceManager.registerGeofence(
                        lat = settings.geofenceLat,
                        lon = settings.geofenceLon,
                        radiusMeters = settings.geofenceRadiusMeters,
                        initialTrigger = initialTrigger,
                        source = source,
                        onSuccess = {
                            if (continuation.isActive) continuation.resume(Unit)
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Failed to re-register geofence: ${error.message}")
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    )
                }
            }
        } else {
            geofenceManager.removeGeofence()
        }
    }

    private fun restoreWifi(settings: Settings) {
        if (settings.wifiAutoStampEnabled && settings.wifiSsid.isNotBlank()) {
            Log.d(TAG, "Re-registering WiFi monitor")
            wifiMonitor.register(settings.wifiSsid)
        } else {
            wifiMonitor.unregister()
        }
    }

    private companion object {
        const val TAG = "AutomationRestorer"
        const val GEOFENCE_TIMEOUT_MS = 10_000L
        const val STALE_THRESHOLD_MS = 60 * 60 * 1000L
    }
}
