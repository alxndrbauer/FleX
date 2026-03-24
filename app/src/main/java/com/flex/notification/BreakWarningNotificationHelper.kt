package com.flex.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.flex.MainActivity
import com.flex.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BreakWarningNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val NOTIF_ID = 2001
        private const val CHANNEL_ID = "break_warning_channel"
    }

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pausenzeitverletzungen",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Warnung bei Verstößen gegen die gesetzliche Pausenregelung (§4 ArbZG)"
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

    fun showBreakWarningNotification() {
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Pausenzeit prüfen")
            .setContentText("Du arbeitest seit über 6 Stunden. Mach jetzt eine Pause (§4 ArbZG).")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Du arbeitest seit über 6 Stunden ohne ausreichende Pause. Das Gesetz (§4 ArbZG) schreibt eine Pause von mindestens 15 Minuten vor. Mach jetzt eine Pause!"))
            .setContentIntent(mainActivityPendingIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
        } catch (_: SecurityException) { }
    }

    fun cancelBreakWarningNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }
}
