package com.flex.geofence

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flex.domain.usecase.UndoAutoClockUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceUndoReceiver : BroadcastReceiver() {

    @Inject lateinit var undoAutoClock: UndoAutoClockUseCase

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                undoAutoClock()
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(GeofenceNotificationHelper.NOTIF_ID)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
