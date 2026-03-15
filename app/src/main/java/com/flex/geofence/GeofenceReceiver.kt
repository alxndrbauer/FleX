package com.flex.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flex.domain.usecase.AutoClockInUseCase
import com.flex.domain.usecase.AutoClockOutUseCase
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceReceiver : BroadcastReceiver() {

    @Inject lateinit var autoClockIn: AutoClockInUseCase
    @Inject lateinit var autoClockOut: AutoClockOutUseCase
    @Inject lateinit var notificationHelper: GeofenceNotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (event.geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> {
                        val blockId = autoClockIn()
                        if (blockId != null) notificationHelper.showClockInNotification()
                    }
                    Geofence.GEOFENCE_TRANSITION_EXIT -> {
                        autoClockOut()
                        notificationHelper.showClockOutNotification()
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
