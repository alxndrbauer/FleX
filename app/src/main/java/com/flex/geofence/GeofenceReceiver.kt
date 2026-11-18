package com.flex.geofence

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.flex.data.local.GeofencePreferences
import com.flex.domain.usecase.AutoClockInUseCase
import com.flex.notification.BreakWarningScheduler
import com.flex.tile.QuickSettingsTileService
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceReceiver : BroadcastReceiver() {

    @Inject lateinit var autoClockIn: AutoClockInUseCase
    @Inject lateinit var notificationHelper: GeofenceNotificationHelper
    @Inject lateinit var breakWarningScheduler: BreakWarningScheduler
    @Inject lateinit var geofencePreferences: GeofencePreferences
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null) {
            Log.e(TAG, "GeofencingEvent.fromIntent returned null")
            geofencePreferences.recordEvent(transition = null, errorCode = -1, atEpochMs = clock.millis())
            return
        }
        if (event.hasError()) {
            Log.e(TAG, "Geofence event error: ${event.errorCode}")
            geofencePreferences.recordEvent(
                transition = null,
                errorCode = event.errorCode,
                atEpochMs = clock.millis()
            )
            return
        }

        geofencePreferences.recordEvent(
            transition = event.geofenceTransition,
            errorCode = null,
            atEpochMs = clock.millis()
        )
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (event.geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER,
                    Geofence.GEOFENCE_TRANSITION_DWELL -> {
                        Log.d(TAG, "Geofence transition=${event.geofenceTransition} → clock in")
                        // ENTER: sofort wenn Geofence betreten oder GPS-Signal zurückkehrt
                        // DWELL: nach 60s kontinuierlichem Aufenthalt (Backup)
                        runCatching {
                            WorkManager.getInstance(context).cancelUniqueWork(CLOCK_OUT_WORK)
                        }.onFailure {
                            Log.e(TAG, "Failed to cancel pending clock-out: ${it.message}", it)
                        }
                        val blockId = autoClockIn()
                        if (blockId != null) {
                            notificationHelper.showClockInNotification()
                            breakWarningScheduler.scheduleWarning(LocalTime.now(clock))
                            try {
                                TileService.requestListeningState(
                                    context,
                                    ComponentName(context, QuickSettingsTileService::class.java)
                                )
                            } catch (_: Exception) {}
                            Log.d(TAG, "Auto clock-in created block $blockId")
                        } else {
                            Log.d(TAG, "Auto clock-in skipped (already clocked in)")
                        }
                    }
                    Geofence.GEOFENCE_TRANSITION_EXIT -> {
                        Log.d(TAG, "Geofence transition=EXIT → schedule clock out")
                        // Schedule delayed clock-out via WorkManager (30s)
                        // BroadcastReceiver goAsync() budget (~10s) is too short for delays
                        val request = OneTimeWorkRequestBuilder<ClockOutWorker>()
                            .setInitialDelay(30, TimeUnit.SECONDS)
                            .build()
                        WorkManager.getInstance(context)
                            .enqueueUniqueWork(CLOCK_OUT_WORK, ExistingWorkPolicy.REPLACE, request)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle geofence transition", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GeofenceReceiver"
        const val CLOCK_OUT_WORK = "geofence_clock_out"
    }
}
