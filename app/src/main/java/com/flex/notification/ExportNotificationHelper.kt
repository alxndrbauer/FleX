package com.flex.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.flex.R
import com.flex.ui.month.ExportFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportNotificationHelper @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "export_channel"
        private const val BASE_NOTIF_ID = 3000
    }

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Exporte & Downloads",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Benachrichtigungen bei abgeschlossenen Exporten"
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun showExportNotification(uri: Uri, format: ExportFormat) {
        val mimeType = when (format) {
            ExportFormat.CSV -> "text/csv"
            ExportFormat.PDF -> "application/pdf"
        }
        val fileName = getFileName(uri) ?: when (format) {
            ExportFormat.CSV -> "Export.csv"
            ExportFormat.PDF -> "Export.pdf"
        }

        val notifId = BASE_NOTIF_ID + (System.currentTimeMillis() % 1000).toInt()

        val viewIntent = Intent(context, NotificationActionActivity::class.java).apply {
            action = NotificationActionActivity.ACTION_VIEW_EXPORT
            setDataAndType(uri, mimeType)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val viewPendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val shareIntent = Intent(context, NotificationActionActivity::class.java).apply {
            action = NotificationActionActivity.ACTION_SHARE_EXPORT
            setDataAndType(uri, mimeType)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val sharePendingIntent = PendingIntent.getActivity(
            context,
            notifId + 10000,
            shareIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$fileName gespeichert")
            .setContentText("Tippen zum Öffnen")
            .setContentIntent(viewPendingIntent)
            .addAction(0, "Öffnen", viewPendingIntent)
            .addAction(0, "Teilen", sharePendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notif)
        } catch (_: SecurityException) { }
    }

    fun getFileName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
