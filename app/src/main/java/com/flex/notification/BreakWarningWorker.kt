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
import java.time.LocalDate

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

        // Only notify if there is a running block and no prior completed block.
        // A completed block means the user already clocked out (took at least some break).
        val hasRunningBlock = blocks.any { it.endTime == null }
        val hasCompletedBlock = blocks.any { it.endTime != null }
        if (hasRunningBlock && hasCompletedBlock) {
            notificationHelper.showBreakWarningNotification()
        }
        return Result.success()
    }
}
