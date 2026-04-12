package com.flex

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.flex.data.holidays.HolidaySyncService
import com.flex.data.local.AppIconPreferences
import com.flex.domain.repository.SettingsRepository
import com.flex.geofence.GeofenceManager
import com.flex.wifi.WifiMonitor
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FlexApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var geofenceManager: GeofenceManager

    @Inject
    lateinit var wifiMonitor: WifiMonitor

    @Inject
    lateinit var holidaySyncService: HolidaySyncService

    override fun onCreate() {
        // Fix PackageManager alias state before Hilt init — prevents stuck launcher icon
        AppIconPreferences.fixOnStartup(this)
        super.onCreate()
        reRegisterGeofenceIfNeeded()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                holidaySyncService.syncForCurrentAndNextYear()
            } catch (e: Exception) {
                Log.e("FlexApplication", "Failed to sync holidays: ${e.message}")
            }
        }
    }

    private fun reRegisterGeofenceIfNeeded() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = settingsRepository.getSettings().first()
                if (settings.geofenceEnabled && settings.geofenceLat != 0.0 && settings.geofenceLon != 0.0) {
                    Log.d("FlexApplication", "Re-registering geofence on app start")
                    geofenceManager.registerGeofence(
                        settings.geofenceLat,
                        settings.geofenceLon,
                        settings.geofenceRadiusMeters
                    )
                }
                if (settings.wifiAutoStampEnabled && settings.wifiSsid.isNotBlank()) {
                    Log.d("FlexApplication", "Registering WiFi monitor for SSID: ${settings.wifiSsid}")
                    wifiMonitor.register(settings.wifiSsid)
                }
            } catch (e: Exception) {
                Log.e("FlexApplication", "Failed to re-register geofence/wifi: ${e.message}")
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
