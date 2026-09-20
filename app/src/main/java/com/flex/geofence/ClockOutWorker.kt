package com.flex.geofence

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.flex.domain.usecase.AutoClockOutUseCase
import com.flex.notification.BreakWarningScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ClockOutWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val autoClockOut: AutoClockOutUseCase,
    private val notificationHelper: GeofenceNotificationHelper,
    private val breakWarningScheduler: BreakWarningScheduler
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val clocked = autoClockOut()
        if (clocked) {
            notificationHelper.showClockOutNotification()
            breakWarningScheduler.cancelWarning()
        }
        return Result.success()
    }
}
