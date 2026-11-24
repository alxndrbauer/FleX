package com.flex.geofence

import android.content.Context
import android.location.LocationManager
import androidx.core.location.LocationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.flex.automation.AutomationRestorer
import com.flex.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Periodically re-registers the office geofence and retries after Play Services errors such as
 * GEOFENCE_NOT_AVAILABLE when device location is toggled off and back on.
 */
@HiltWorker
class GeofenceKeepAliveWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val automationRestorer: AutomationRestorer,
    private val keepAliveScheduler: GeofenceKeepAliveScheduler
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.getSettings().first()
        if (!settings.geofenceEnabled || settings.geofenceLat == 0.0 || settings.geofenceLon == 0.0) {
            keepAliveScheduler.cancel()
            return Result.success()
        }

        val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager != null && !LocationManagerCompat.isLocationEnabled(locationManager)) {
            return if (runAttemptCount < MAX_LOCATION_RETRY_ATTEMPTS) Result.retry() else Result.success()
        }

        val source = inputData.getString(KEY_SOURCE) ?: "Hintergrund-Check"
        automationRestorer.restoreGeofenceSmart(source = source)
        return Result.success()
    }

    companion object {
        const val PERIODIC_WORK_NAME = "geofence_keep_alive"
        const val MORNING_WORK_NAME = "geofence_morning_heal"
        const val RETRY_WORK_NAME = "geofence_recovery_retry"
        const val KEY_SOURCE = "source"
        private const val MAX_LOCATION_RETRY_ATTEMPTS = 12
    }
}
