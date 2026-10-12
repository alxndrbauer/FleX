package com.flex.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.action.ActionCallback
import com.flex.domain.events.DataChangeEvent
import com.flex.domain.model.WorkLocation
import com.flex.notification.WorkTimerService
import com.flex.tile.QuickSettingsTileService
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

class ClockInAction : ActionCallback {
    companion object {
        const val KEY_LOCATION = "action_clock_in_location"
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            FlexWidgetEntryPoint::class.java
        )
        val locationStr = parameters[androidx.glance.action.ActionParameters.Key<String>(KEY_LOCATION)]
        val location = locationStr?.let { runCatching { WorkLocation.valueOf(it) }.getOrNull() }
            ?: WorkLocation.OFFICE

        Log.d("FlexWidget", "ClockInAction triggered for location: $location")
        val clockedIn = entryPoint.clockInUseCase().invoke(location)
        if (clockedIn) {
            val settings = entryPoint.settingsRepository().getSettings().first()
            if (settings.workTimerNotificationEnabled) {
                context.startForegroundService(
                    Intent(context, WorkTimerService::class.java).apply {
                        action = WorkTimerService.ACTION_UPDATE
                    }
                )
            }
            notifyTile(context)
            entryPoint.dataChangeEventBus().emit(DataChangeEvent.WorkDayChanged)
        }
        FlexWidget().update(context, glanceId)
        FlexWidgetUpdater.updateAll(context)
    }
}

class ClockOutAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            FlexWidgetEntryPoint::class.java
        )
        Log.d("FlexWidget", "ClockOutAction triggered")
        entryPoint.autoClockOutUseCase().invoke()
        context.stopService(Intent(context, WorkTimerService::class.java))
        notifyTile(context)
        entryPoint.dataChangeEventBus().emit(DataChangeEvent.WorkDayChanged)
        FlexWidget().update(context, glanceId)
        FlexWidgetUpdater.updateAll(context)
    }
}

class PauseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            FlexWidgetEntryPoint::class.java
        )
        val isPaused = entryPoint.pausePreferences().isPaused
        Log.d("FlexWidget", "PauseAction triggered (currently paused: $isPaused)")
        if (isPaused) {
            // Resume work
            entryPoint.clockInUseCase().invoke()
        } else {
            // Enter pause
            entryPoint.pauseWorkUseCase().invoke()
        }
        val settings = entryPoint.settingsRepository().getSettings().first()
        if (settings.workTimerNotificationEnabled) {
            context.startForegroundService(
                Intent(context, WorkTimerService::class.java).apply {
                    action = WorkTimerService.ACTION_UPDATE
                }
            )
        }
        notifyTile(context)
        entryPoint.dataChangeEventBus().emit(DataChangeEvent.WorkDayChanged)
        FlexWidget().update(context, glanceId)
        FlexWidgetUpdater.updateAll(context)
    }
}

class SwitchLocationAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            FlexWidgetEntryPoint::class.java
        )
        Log.d("FlexWidget", "SwitchLocationAction triggered")
        entryPoint.switchLocationUseCase().invoke()
        val settings = entryPoint.settingsRepository().getSettings().first()
        if (settings.workTimerNotificationEnabled) {
            context.startForegroundService(
                Intent(context, WorkTimerService::class.java).apply {
                    action = WorkTimerService.ACTION_UPDATE
                }
            )
        }
        notifyTile(context)
        entryPoint.dataChangeEventBus().emit(DataChangeEvent.WorkDayChanged)
        FlexWidget().update(context, glanceId)
        FlexWidgetUpdater.updateAll(context)
    }
}

private fun notifyTile(context: Context) {
    try {
        TileService.requestListeningState(
            context,
            ComponentName(context, QuickSettingsTileService::class.java)
        )
    } catch (_: Exception) {}
}
