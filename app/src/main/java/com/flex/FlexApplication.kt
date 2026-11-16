package com.flex

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.flex.automation.AutomationRestorer
import com.flex.data.holidays.HolidaySyncService
import com.flex.data.local.AppIconPreferences
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FlexApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var automationRestorer: AutomationRestorer

    @Inject
    lateinit var holidaySyncService: HolidaySyncService

    @Inject
    lateinit var autoBookPlannedDays: com.flex.domain.usecase.AutoBookPlannedDaysUseCase

    @Inject
    lateinit var dataChangeEventBus: com.flex.domain.events.DataChangeEventBus

    override fun onCreate() {
        // Fix PackageManager alias state before Hilt init — prevents stuck launcher icon
        AppIconPreferences.fixOnStartup(this)
        super.onCreate()
        observeDataChanges()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                automationRestorer.restore()
            } catch (e: Exception) {
                Log.e("FlexApplication", "Failed to restore automation: ${e.message}")
            }
            try {
                holidaySyncService.syncForCurrentAndNextYear()
            } catch (e: Exception) {
                Log.e("FlexApplication", "Failed to sync holidays: ${e.message}")
            }
            try {
                autoBookPlannedDays()
            } catch (e: Exception) {
                Log.e("FlexApplication", "Failed to auto-book planned days: ${e.message}")
            }
        }
    }

    private fun observeDataChanges() {
        CoroutineScope(Dispatchers.Default).launch {
            dataChangeEventBus.events.collect {
                com.flex.widget.FlexWidgetUpdater.updateAll(this@FlexApplication)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
