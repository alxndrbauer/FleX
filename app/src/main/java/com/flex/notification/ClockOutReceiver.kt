package com.flex.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flex.domain.repository.WorkDayRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import com.flex.data.local.PausePreferences
import javax.inject.Inject

@AndroidEntryPoint
class ClockOutReceiver : BroadcastReceiver() {

    @Inject lateinit var workDayRepository: WorkDayRepository
    @Inject lateinit var pausePreferences: PausePreferences

    companion object {
        const val ACTION_CLOCK_OUT = "com.flex.ACTION_CLOCK_OUT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CLOCK_OUT) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                pausePreferences.clearPause()
                val workDay = workDayRepository.getWorkDay(LocalDate.now()).first()
                val runningBlock = workDay?.timeBlocks?.find { it.endTime == null }
                if (runningBlock != null) {
                    val now = LocalTime.now().withSecond(0).withNano(0)
                    workDayRepository.saveTimeBlock(runningBlock.copy(endTime = now))
                }
                context.stopService(Intent(context, WorkTimerService::class.java))
                android.service.quicksettings.TileService.requestListeningState(
                    context,
                    android.content.ComponentName(context, com.flex.tile.QuickSettingsTileService::class.java)
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
