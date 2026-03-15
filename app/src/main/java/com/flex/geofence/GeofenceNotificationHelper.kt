package com.flex.geofence

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.flex.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "geofence_channel"
    }

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Automatisches Stempeln",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Benachrichtigungen beim automatischen Ein-/Ausstempeln"
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun showClockInNotification() {
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val undoIntent = Intent(context, GeofenceUndoReceiver::class.java)
        val undoPending = PendingIntent.getBroadcast(
            context, 0, undoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("FleX")
            .setContentText("Automatisch eingestempelt um $time")
            .addAction(0, "Rückgängig", undoPending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
        } catch (_: SecurityException) { }
    }

    fun showClockOutNotification() {
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("FleX")
            .setContentText("Automatisch ausgestempelt um $time")
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
        } catch (_: SecurityException) { }
    }
}
