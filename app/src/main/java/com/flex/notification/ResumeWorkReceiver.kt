package com.flex.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flex.domain.usecase.ClockInUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ResumeWorkReceiver : BroadcastReceiver() {

    @Inject lateinit var clockInUseCase: ClockInUseCase

    companion object {
        const val ACTION_RESUME_WORK = "com.flex.ACTION_RESUME_WORK"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RESUME_WORK) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val clockedIn = clockInUseCase()
                if (clockedIn) {
                    context.startForegroundService(
                        Intent(context, WorkTimerService::class.java).apply {
                            action = WorkTimerService.ACTION_UPDATE
                        }
                    )
                    android.service.quicksettings.TileService.requestListeningState(
                        context,
                        android.content.ComponentName(context, com.flex.tile.QuickSettingsTileService::class.java)
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
