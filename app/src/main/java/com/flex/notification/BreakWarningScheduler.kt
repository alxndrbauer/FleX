package com.flex.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BreakWarningScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val WORK_TAG = "break_warning"
        private const val SIX_HOURS_MINUTES = 360L
    }

    /**
     * Schedules a notification for 6 hours after [clockInTime].
     * If the 6h mark has already passed, schedules immediately.
     * Cancels any previously scheduled warning first.
     */
    fun scheduleWarning(clockInTime: LocalTime) {
        val now = LocalTime.now()
        val targetTime = clockInTime.plusHours(6)
        val delayMinutes = Duration.between(now, targetTime).toMinutes().coerceAtLeast(0L)

        val request = OneTimeWorkRequestBuilder<BreakWarningWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_TAG, ExistingWorkPolicy.KEEP, request)
    }

    fun cancelWarning() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_TAG)
    }
}
