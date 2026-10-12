package com.flex.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flex.domain.usecase.PauseWorkUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PauseReceiver : BroadcastReceiver() {

    @Inject lateinit var pauseWorkUseCase: PauseWorkUseCase

    companion object {
        const val ACTION_PAUSE = "com.flex.ACTION_PAUSE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("PauseReceiver", "onReceive: action=${intent.action}")
        if (intent.action != ACTION_PAUSE) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val paused = pauseWorkUseCase()
                android.util.Log.d("PauseReceiver", "pauseWorkUseCase result: $paused")
                if (paused) {
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
            } catch (e: Exception) {
                android.util.Log.e("PauseReceiver", "Error pausing work", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
