package com.flex.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

@HiltWorker
class BreakWarningWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val workDayRepository: WorkDayRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationHelper: BreakWarningNotificationHelper
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.getSettings().first()
        if (!settings.breakWarningEnabled) return Result.success()

        val today = LocalDate.now()
        val workDay = workDayRepository.getWorkDay(today).first() ?: return Result.success()
        val blocks = workDay.timeBlocks

        // Skip if automatic break deduction is active
        if (blocks.any { it.isDuration }) return Result.success()

        // Only notify if there is a completed block + a running block that has been
        // going for at least 5.5h (330 min). The 0.5h tolerance accounts for WorkManager
        // scheduling imprecision — the worker is intended to fire after 6h.
        val runningBlock = blocks.find { it.endTime == null } ?: return Result.success()
        val hasCompletedBlock = blocks.any { it.endTime != null }
        val runningMinutes = Duration.between(runningBlock.startTime, LocalTime.now()).toMinutes()
        if (hasCompletedBlock && runningMinutes >= 330) {
            notificationHelper.showBreakWarningNotification()
        }
        return Result.success()
    }
}
