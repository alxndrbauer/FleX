package com.flex.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flex.domain.model.WorkLocation
import com.flex.domain.usecase.SwitchLocationUseCase
import com.flex.wearable.WearSyncHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SwitchLocationReceiver : BroadcastReceiver() {

    @Inject lateinit var switchLocationUseCase: SwitchLocationUseCase
    @Inject lateinit var wearSyncHelper: WearSyncHelper

    companion object {
        const val ACTION_SWITCH_LOCATION = "com.flex.ACTION_SWITCH_LOCATION"
        const val EXTRA_TARGET_LOCATION = "extra_target_location"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SWITCH_LOCATION) return
        val targetLocationName = intent.getStringExtra(EXTRA_TARGET_LOCATION)
        val targetLocation = targetLocationName?.let { runCatching { WorkLocation.valueOf(it) }.getOrNull() }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val switched = switchLocationUseCase(targetLocation)
                if (switched) {
                    wearSyncHelper.push()
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
