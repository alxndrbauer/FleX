package com.flex.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.flex.domain.usecase.AutoClockInUseCase
import com.flex.domain.usecase.AutoClockOutUseCase
import com.flex.notification.BreakWarningScheduler
import com.flex.wearable.WearSyncHelper
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceReceiver : BroadcastReceiver() {

    companion object {
        private const val CLOCK_OUT_WORK = "geofence_clock_out"
    }

    @Inject lateinit var autoClockIn: AutoClockInUseCase
    @Inject lateinit var autoClockOut: AutoClockOutUseCase
    @Inject lateinit var notificationHelper: GeofenceNotificationHelper
    @Inject lateinit var wearSyncHelper: WearSyncHelper
    @Inject lateinit var breakWarningScheduler: BreakWarningScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (event.geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_DWELL -> {
                        // User lingered 1 minute inside geofence → clock in
                        // Cancel any pending delayed clock-out (e.g. quick exit + re-entry)
                        WorkManager.getInstance(context).cancelUniqueWork(CLOCK_OUT_WORK)
                        val blockId = autoClockIn()
                        if (blockId != null) {
                            notificationHelper.showClockInNotification()
                            breakWarningScheduler.scheduleWarning(java.time.LocalTime.now())
                            wearSyncHelper.push()
                        }
                    }
                    Geofence.GEOFENCE_TRANSITION_EXIT -> {
                        // Schedule delayed clock-out via WorkManager (30s)
                        // BroadcastReceiver goAsync() budget (~10s) is too short for delays
                        val request = OneTimeWorkRequestBuilder<ClockOutWorker>()
                            .setInitialDelay(30, TimeUnit.SECONDS)
                            .build()
                        WorkManager.getInstance(context)
                            .enqueueUniqueWork(CLOCK_OUT_WORK, ExistingWorkPolicy.REPLACE, request)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
