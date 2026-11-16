package com.flex.wifi

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.flex.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Safety net for WiFi auto-stamping.
 *
 * The in-process [android.net.ConnectivityManager.NetworkCallback] dies with the app process
 * (app update, reboot, system kill). This worker survives those events and reconciles the
 * expected stamping state with the actual WiFi connection.
 */
@HiltWorker
class WifiAutoStampWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val wifiMonitor: WifiMonitor
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.getSettings().first()
        if (!settings.wifiAutoStampEnabled || settings.wifiSsid.isBlank()) {
            return Result.success()
        }
        wifiMonitor.reconcile(settings.wifiSsid)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "wifi_auto_stamp_reconciliation"
    }
}
