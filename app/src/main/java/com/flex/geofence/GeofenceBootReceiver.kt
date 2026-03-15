package com.flex.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.flex.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceBootReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var geofenceManager: GeofenceManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = settingsRepository.getSettings().first()
                if (settings.geofenceEnabled && settings.geofenceLat != 0.0 && settings.geofenceLon != 0.0) {
                    Log.d("GeofenceBootReceiver", "Re-registering geofence after boot")
                    geofenceManager.registerGeofence(
                        settings.geofenceLat,
                        settings.geofenceLon,
                        settings.geofenceRadiusMeters
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
