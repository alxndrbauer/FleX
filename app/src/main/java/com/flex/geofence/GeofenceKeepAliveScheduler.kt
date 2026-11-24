package com.flex.geofence

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules periodic keep-alive checks, a targeted morning check before work start,
 * and overnight recovery retries for office geofencing.
 */
@Singleton
class GeofenceKeepAliveScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock
) {
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<GeofenceKeepAliveWorker>(1, TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .setInputData(workDataOf(GeofenceKeepAliveWorker.KEY_SOURCE to "Hintergrund-Check"))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            GeofenceKeepAliveWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleMorningCheck(defaultStartTime: LocalTime) {
        val delayMinutes = computeMorningCheckDelayMinutes(ZonedDateTime.now(clock), defaultStartTime)
        val request = PeriodicWorkRequestBuilder<GeofenceKeepAliveWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setInputData(workDataOf(GeofenceKeepAliveWorker.KEY_SOURCE to "Morgen-Check"))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            GeofenceKeepAliveWorker.MORNING_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleImmediateRetry(delayMinutes: Long = 2) {
        val request = OneTimeWorkRequestBuilder<GeofenceKeepAliveWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .setInputData(workDataOf(GeofenceKeepAliveWorker.KEY_SOURCE to "Fehler-Retry"))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            GeofenceKeepAliveWorker.RETRY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(GeofenceKeepAliveWorker.PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(GeofenceKeepAliveWorker.MORNING_WORK_NAME)
        workManager.cancelUniqueWork(GeofenceKeepAliveWorker.RETRY_WORK_NAME)
    }

    companion object {
        internal const val MORNING_LEAD_MINUTES = 45L

        internal fun computeMorningCheckDelayMinutes(
            now: ZonedDateTime,
            defaultStartTime: LocalTime
        ): Long {
            val targetTime = defaultStartTime.minusMinutes(MORNING_LEAD_MINUTES)
            var nextRun = now.toLocalDate().atTime(targetTime).atZone(now.zone)
            if (!nextRun.isAfter(now)) {
                nextRun = nextRun.plusDays(1)
            }
            return Duration.between(now, nextRun).toMinutes().coerceAtLeast(1L)
        }
    }
}
