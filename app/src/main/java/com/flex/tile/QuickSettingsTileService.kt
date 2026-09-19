package com.flex.tile

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.flex.R
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.AutoClockOutUseCase
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.ClockInUseCase
import com.flex.notification.WorkTimerService
import com.flex.wearable.WearSyncHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import com.flex.data.local.PausePreferences
import javax.inject.Inject

@AndroidEntryPoint
class QuickSettingsTileService : TileService() {

    @Inject lateinit var workDayRepository: WorkDayRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var clockInUseCase: ClockInUseCase
    @Inject lateinit var autoClockOutUseCase: AutoClockOutUseCase
    @Inject lateinit var calculateDayWorkTime: CalculateDayWorkTimeUseCase
    @Inject lateinit var wearSyncHelper: WearSyncHelper
    @Inject lateinit var pausePreferences: PausePreferences

    private var serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onStartListening() {
        super.onStartListening()
        serviceScope.cancel()
        serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        serviceScope.launch {
            workDayRepository.getWorkDay(LocalDate.now()).collect { workDay ->
                updateTileWithWorkDay(workDay)
            }
        }
        serviceScope.launch {
            while (isActive) {
                delay(30_000)
                val today = LocalDate.now()
                val workDay = workDayRepository.getWorkDay(today).first()
                if (workDay?.timeBlocks?.any { it.endTime == null } == true || pausePreferences.isPaused) {
                    updateTileWithWorkDay(workDay)
                }
            }
        }
    }

    override fun onStopListening() {
        serviceScope.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val action = {
            serviceScope.launch {
                val today = LocalDate.now()
                val workDay = workDayRepository.getWorkDay(today).first()
                val isClockRunning = workDay?.timeBlocks?.any { it.endTime == null } == true

                if (isClockRunning) {
                    autoClockOutUseCase()
                    stopService(Intent(this@QuickSettingsTileService, WorkTimerService::class.java))
                } else {
                    clockInUseCase()
                    val settings = settingsRepository.getSettings().first()
                    if (settings.workTimerNotificationEnabled) {
                        startForegroundService(Intent(this@QuickSettingsTileService, WorkTimerService::class.java))
                    }
                }
                wearSyncHelper.push()
                TileService.requestListeningState(
                    this@QuickSettingsTileService,
                    ComponentName(this@QuickSettingsTileService, QuickSettingsTileService::class.java)
                )
            }
        }

        if (isLocked) {
            unlockAndRun { action() }
        } else {
            action()
        }
    }

    private fun updateTileWithWorkDay(workDay: com.flex.domain.model.WorkDay?) {
        val tile = qsTile ?: return
        val runningBlock = workDay?.timeBlocks?.find { it.endTime == null }

        tile.label = getString(R.string.app_name)

        if (runningBlock != null) {
            tile.state = Tile.STATE_ACTIVE
            tile.icon = Icon.createWithResource(this, R.drawable.ic_notification)
            val blocks = workDay.timeBlocks.map { if (it.endTime == null) it.copy(endTime = LocalTime.now()) else it }
            val netMinutes = calculateDayWorkTime(blocks).netMinutes
            val loc = if (runningBlock.location == WorkLocation.HOME_OFFICE) "Home-Office" else "Büro"
            val h = netMinutes / 60
            val m = netMinutes % 60
            val dur = String.format(Locale.getDefault(), "%d:%02d h", h, m)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "$dur · $loc"
            }
        } else if (pausePreferences.isPaused) {
            tile.state = Tile.STATE_INACTIVE
            tile.icon = Icon.createWithResource(this, R.drawable.ic_notification)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "In Pause"
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.icon = Icon.createWithResource(this, R.drawable.ic_notification)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Ausgestempelt"
            }
        }
        tile.updateTile()
    }
}
