package com.flex.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.flex.MainActivity
import com.flex.R
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.WorkDayRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class WorkTimerService : Service() {

    @Inject lateinit var workDayRepository: WorkDayRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        const val NOTIF_ID = 2002
        private const val CHANNEL_ID = "work_timer_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification("Arbeitzeit läuft", "Berechne..."))
        serviceScope.launch {
            while (isActive) {
                val workDay = workDayRepository.getWorkDay(LocalDate.now()).first()
                val runningBlock = workDay?.timeBlocks?.find { it.endTime == null }
                if (runningBlock == null) {
                    stopSelf()
                    break
                }
                val elapsed = Duration.between(runningBlock.startTime, LocalTime.now())
                val h = elapsed.toHours()
                val m = elapsed.toMinutesPart()
                val locationLabel = when (runningBlock.location) {
                    WorkLocation.OFFICE -> "Büro"
                    WorkLocation.HOME_OFFICE -> "Home-Office"
                }
                val contentText = if (h == 0L) "${m}min · $locationLabel" else "${h}h ${m}min · $locationLabel"
                updateNotification("Arbeitzeit läuft", contentText)
                delay(60_000)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Laufende Arbeitszeit",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Zeigt die aktive Arbeitszeit in der Statusleiste"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun mainActivityIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun clockOutIntent(): PendingIntent {
        val intent = Intent(this, ClockOutReceiver::class.java).apply {
            action = ClockOutReceiver.ACTION_CLOCK_OUT
        }
        return PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(title: String, content: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(mainActivityIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(0, "Ausstempeln", clockOutIntent())
            .build()

    private fun updateNotification(title: String, content: String) {
        val notif = buildNotification(title, content)
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notif)
    }
}
