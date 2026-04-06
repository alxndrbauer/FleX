package com.flex.geofence

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.flex.MainActivity
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
    @param:ApplicationContext private val context: Context
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

    private fun mainActivityPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showClockInNotification() {
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val undoIntent = Intent(context, GeofenceUndoReceiver::class.java)
        val undoPending = PendingIntent.getBroadcast(
            context, 0, undoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("FleX")
            .setContentText("Automatisch eingestempelt um $time")
            .setContentIntent(mainActivityPendingIntent())
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
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("FleX")
            .setContentText("Automatisch ausgestempelt um $time")
            .setContentIntent(mainActivityPendingIntent())
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
        } catch (_: SecurityException) { }
    }
}
