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
        val dataProvider = entryPoint.flexWidgetDataProvider()
        val state = dataProvider.getWidgetState()
        if (!state.isClockRunning && !state.isPaused) {
            // Already clocked out, nothing to do
            return
        }

        Log.d("FlexWidget", "ClockOutAction triggered")
        entryPoint.autoClockOutUseCase().invoke()
        context.stopService(Intent(context, WorkTimerService::class.java))
        notifyTile(context)
        entryPoint.dataChangeEventBus().emit(DataChangeEvent.WorkDayChanged)
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
            val clockedIn = entryPoint.clockInUseCase().invoke()
            if (!clockedIn) return
        } else {
            // Enter pause
            val paused = entryPoint.pauseWorkUseCase().invoke()
            if (!paused) return
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
        val switched = entryPoint.switchLocationUseCase().invoke()
        if (!switched) return

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
        FlexWidgetUpdater.updateAll(context)
    }
}

class QuickLocationAction : ActionCallback {
    companion object {
        const val KEY_LOCATION = "action_quick_location"
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
        val targetLocation = locationStr?.let { runCatching { WorkLocation.valueOf(it) }.getOrNull() }
            ?: WorkLocation.OFFICE

        Log.d("FlexWidget", "QuickLocationAction triggered for location: $targetLocation")
        val dataProvider = entryPoint.flexWidgetDataProvider()
        val state = dataProvider.getWidgetState()

        if (state.isClockRunning) {
            if (state.currentLocation == targetLocation) {
                // Already running at target location, nothing to do
                return
            }
            entryPoint.switchLocationUseCase().invoke(targetLocation)
        } else {
            val clockedIn = entryPoint.clockInUseCase().invoke(targetLocation)
            if (!clockedIn) return
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
