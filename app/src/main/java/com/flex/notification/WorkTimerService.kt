package com.flex.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.flex.MainActivity
import com.flex.R
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

@AndroidEntryPoint
class WorkTimerService : Service() {

    @Inject lateinit var workDayRepository: WorkDayRepository
    @Inject lateinit var calculateDayWorkTime: CalculateDayWorkTimeUseCase
    @Inject lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        const val NOTIF_ID = 2002
        private const val CHANNEL_ID = "work_timer_channel"
        private const val BREAK_THRESHOLD_MINUTES = 360 // 6h
        const val ACTION_UPDATE = "com.flex.ACTION_UPDATE_WORK_TIMER"
    }

    private var timerJob: kotlinx.coroutines.Job? = null
    private val updateTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_UPDATE) {
            updateTrigger.tryEmit(Unit)
            return START_STICKY
        }

        // Placeholder — immediately replaced by the coroutine below
        if (Build.VERSION.SDK_INT >= 36) {
            startForeground(NOTIF_ID, Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Arbeitszeit läuft")
                .setColor(getColor(android.R.color.system_accent1_500))
                .setShortCriticalText("0m")
                .setWhen(System.currentTimeMillis())
                .setUsesChronometer(true)
                .setShowWhen(true)
                .setOngoing(true)
                .addExtras(android.os.Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .build())
        } else {
            startForeground(NOTIF_ID, buildLegacyNotification("Arbeitszeit läuft", "Starte...", null, 0, 0, null, null))
        }

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                val now = LocalTime.now()
                val workDay = workDayRepository.getWorkDay(LocalDate.now()).first()
                val allBlocks = workDay?.timeBlocks ?: emptyList()
                val runningBlock = allBlocks.find { it.endTime == null }

                if (runningBlock == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    updateQuickSettingsTile()
                    break
                }

                // Net work time across ALL blocks for today (completed + running)
                val blocksForCalc = allBlocks.map { if (it.endTime == null) it.copy(endTime = now) else it }
                val workTimeResult = calculateDayWorkTime(blocksForCalc)
                val netMinutes = workTimeResult.netMinutes

                val settings = settingsRepository.getSettings().first()
                val workTimeRules = settingsRepository.getWorkTimeRules().first()
                val targetMinutes = (settingsRepository.getWorkTimeRuleForDate(LocalDate.now(), workTimeRules)?.dailyWorkMinutes
                    ?: settings.dailyWorkMinutes).toLong()
                val flexDelta = netMinutes - targetMinutes

                val locationLabel = when (runningBlock.location) {
                    WorkLocation.OFFICE -> "Büro"
                    WorkLocation.HOME_OFFICE -> "Home-Office"
                }

                val breakMinutes = workTimeResult.breakMinutes
                val hasRealBreak = allBlocks.count { it.endTime != null } >= 1
                val breakPart = if (breakMinutes > 0 && hasRealBreak)
                    "Pause: ${formatDuration(breakMinutes)}"
                else null

                val isOvertime = flexDelta > 0
                val title: String
                val content: String
                val subText: String
                val progress: Int
                val progressMax: Int

                if (isOvertime) {
                    title = "Ziel erreicht"
                    content = "${formatDuration(netMinutes)} · $locationLabel"
                    val overtimePart = "Überstunden: ${formatFlexDelta(flexDelta)}"
                    subText = listOfNotNull(overtimePart, breakPart).joinToString("  ·  ")
                    progress = netMinutes.toInt()
                    progressMax = netMinutes.toInt() // full bar effectively
                } else {
                    title = "Arbeitszeit läuft"
                    content = "${formatDuration(netMinutes)} / ${formatDuration(targetMinutes)} · $locationLabel"
                    val flexPart = "Flexzeit: ${formatFlexDelta(flexDelta)}"
                    subText = listOfNotNull(breakPart, flexPart).joinToString("  ·  ")
                    progress = netMinutes.toInt()
                    progressMax = targetMinutes.toInt()
                }

                val startEpoch = LocalDate.now()
                    .atTime(runningBlock.startTime)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli() + workTimeResult.breakMinutes * 60_000L

                val notification = if (Build.VERSION.SDK_INT >= 36) {
                    buildProgressStyleNotification(title, content, subText, netMinutes, targetMinutes, isOvertime, runningBlock.startTime, workTimeResult.breakMinutes, runningBlock.location)
                } else {
                    buildLegacyNotification(title, content, subText, progress, progressMax, runningBlock.location, startEpoch)
                }
                getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification)
                kotlinx.coroutines.withTimeoutOrNull(60_000L) {
                    updateTrigger.first()
                }
            }
        }
        updateQuickSettingsTile()
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        updateQuickSettingsTile()
        super.onDestroy()
    }

    private fun updateQuickSettingsTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            android.service.quicksettings.TileService.requestListeningState(
                this,
                android.content.ComponentName(this, com.flex.tile.QuickSettingsTileService::class.java)
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── API 36+: ProgressStyle mit Etappen ──────────────────────────────────

    @RequiresApi(36)
    private fun buildProgressStyleNotification(
        title: String,
        content: String,
        subText: String,
        netMinutes: Long,
        targetMinutes: Long,
        isOvertime: Boolean,
        runningBlockStart: LocalTime,
        breakMinutes: Long,
        location: WorkLocation
    ): Notification {
        // Offset setWhen by the deducted break so the chronometer shows net time, not gross time.
        val startEpoch = LocalDate.now()
            .atTime(runningBlockStart)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli() + breakMinutes * 60_000L
        val primaryColor = getColor(android.R.color.system_accent1_500)
        val trackerIcon = Icon.createWithResource(this, R.drawable.ic_notification)
            .setTint(primaryColor)

        val progressStyle = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(netMinutes.toInt())
            .setProgressTrackerIcon(trackerIcon)

        // Segment 1: 0 → 6h (vor Pausenpflicht)
        val seg1 = BREAK_THRESHOLD_MINUTES.coerceAtMost(targetMinutes.toInt())
        progressStyle.addProgressSegment(Notification.ProgressStyle.Segment(seg1))

        // Segment 2: 6h → Tagessoll
        if (targetMinutes > BREAK_THRESHOLD_MINUTES) {
            progressStyle.addProgressSegment(
                Notification.ProgressStyle.Segment((targetMinutes - BREAK_THRESHOLD_MINUTES).toInt())
            )
        }

        // Segment 3: Überstunden (wächst dynamisch mit)
        if (isOvertime) {
            progressStyle.addProgressSegment(
                Notification.ProgressStyle.Segment((netMinutes - targetMinutes).toInt())
            )
        }

        // Etappen-Markierung bei 6h
        if (targetMinutes > BREAK_THRESHOLD_MINUTES) {
            progressStyle.addProgressPoint(Notification.ProgressStyle.Point(BREAK_THRESHOLD_MINUTES))
        }

        // Etappen-Markierung bei 9h (zweite Pausenschwelle)
        if (netMinutes > 540) {
            progressStyle.addProgressPoint(Notification.ProgressStyle.Point(540))
        }

        // Etappen-Markierung bei Tagessoll
        progressStyle.addProgressPoint(Notification.ProgressStyle.Point(targetMinutes.toInt()))

        val targetLocation = if (location == WorkLocation.OFFICE) WorkLocation.HOME_OFFICE else WorkLocation.OFFICE
        val switchLabel = if (targetLocation == WorkLocation.HOME_OFFICE) "Zu Home-Office" else "Zu Büro"

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(primaryColor)
            .setShortCriticalText(formatShortDuration(netMinutes))
            .setContentTitle(title)
            .setContentText(content)
            .setSubText(subText)
            .setWhen(startEpoch)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setContentIntent(mainActivityIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addExtras(android.os.Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                Notification.Action.Builder(null, switchLabel, switchLocationIntent(targetLocation)).build()
            )
            .addAction(
                Notification.Action.Builder(null, "Ausstempeln", clockOutIntent()).build()
            )
            .setStyle(progressStyle)
            .build()
    }

    // ── API < 36: Standard NotificationCompat ───────────────────────────────

    private fun buildLegacyNotification(
        title: String,
        content: String,
        subText: String?,
        progress: Int,
        progressMax: Int,
        location: WorkLocation?,
        startEpoch: Long? = null
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(content)
        .apply { if (subText != null) setSubText(subText) }
        .setContentIntent(mainActivityIntent())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .apply {
            if (startEpoch != null) {
                setWhen(startEpoch)
                setUsesChronometer(true)
                setShowWhen(true)
            }
            if (progressMax > 0) setProgress(progressMax, progress.coerceAtMost(progressMax), false)
            if (location != null) {
                val targetLocation = if (location == WorkLocation.OFFICE) WorkLocation.HOME_OFFICE else WorkLocation.OFFICE
                val switchLabel = if (targetLocation == WorkLocation.HOME_OFFICE) "Zu Home-Office" else "Zu Büro"
                addAction(0, switchLabel, switchLocationIntent(targetLocation))
            }
        }
        .addAction(0, "Ausstempeln", clockOutIntent())
        .build()

    // ── Intents ─────────────────────────────────────────────────────────────

    private fun switchLocationIntent(targetLocation: WorkLocation): PendingIntent {
        val intent = Intent(this, SwitchLocationReceiver::class.java).apply {
            action = SwitchLocationReceiver.ACTION_SWITCH_LOCATION
            putExtra(SwitchLocationReceiver.EXTRA_TARGET_LOCATION, targetLocation.name)
        }
        return PendingIntent.getBroadcast(
            this,
            targetLocation.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

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

    // ── Formatierung ─────────────────────────────────────────────────────────

    private fun formatDuration(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h == 0L) "${m}min" else "${h}h ${m}min"
    }

    private fun formatShortDuration(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h == 0L) "${m}m" else "${h}h ${m}m"
    }

    private fun formatFlexDelta(deltaMinutes: Long): String {
        val sign = if (deltaMinutes >= 0) "+" else "−"
        val abs = kotlin.math.abs(deltaMinutes)
        val h = abs / 60
        val m = abs % 60
        return if (h == 0L) "$sign${m}min" else "$sign${h}h ${m}min"
    }
}
