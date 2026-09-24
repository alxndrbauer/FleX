package com.flex.geofence

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.flex.domain.usecase.AutoClockOutUseCase
import com.flex.notification.BreakWarningScheduler
import com.flex.notification.WorkTimerService
import com.flex.tile.QuickSettingsTileService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ClockOutWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
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
            try {
                appContext.stopService(Intent(appContext, WorkTimerService::class.java))
            } catch (_: Exception) {}
            try {
                TileService.requestListeningState(
                    appContext,
                    ComponentName(appContext, QuickSettingsTileService::class.java)
                )
            } catch (_: Exception) {}
        }
        return Result.success()
    }
}
