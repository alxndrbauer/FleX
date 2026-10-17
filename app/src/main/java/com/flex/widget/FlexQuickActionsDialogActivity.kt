package com.flex.widget

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.flex.R
import com.flex.data.local.PausePreferences
import com.flex.domain.events.DataChangeEvent
import com.flex.domain.events.DataChangeEventBus
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.usecase.AutoClockOutUseCase
import com.flex.domain.usecase.ClockInUseCase
import com.flex.domain.usecase.PauseWorkUseCase
import com.flex.domain.usecase.SwitchLocationUseCase
import com.flex.notification.WorkTimerService
import com.flex.tile.QuickSettingsTileService
import com.flex.ui.theme.FlexTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FlexQuickActionsDialogActivity : ComponentActivity() {

    @Inject lateinit var clockInUseCase: ClockInUseCase
    @Inject lateinit var autoClockOutUseCase: AutoClockOutUseCase
    @Inject lateinit var pauseWorkUseCase: PauseWorkUseCase
    @Inject lateinit var switchLocationUseCase: SwitchLocationUseCase
    @Inject lateinit var pausePreferences: PausePreferences
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var dataChangeEventBus: DataChangeEventBus
    @Inject lateinit var flexWidgetDataProvider: FlexWidgetDataProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        val initialState = kotlinx.coroutines.runBlocking { flexWidgetDataProvider.getWidgetState() }

        setContent {
            FlexTheme {
                val state by flexWidgetDataProvider.getWidgetStateFlow().collectAsState(initial = initialState)
                val isPaused = state.isPaused

                QuickActionsDialogContent(
                    state = state,
                    isPaused = isPaused,
                    onOfficeClick = { handleOfficeAction(state) },
                    onHomeClick = { handleHomeAction(state) },
                    onPauseResumeClick = { handlePauseResumeAction(isPaused) },
                    onStopClick = { handleStopAction() },
                    onDismiss = { finish() }
                )
            }
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun handleOfficeAction(state: WidgetState) {
        lifecycleScope.launch {
            if (state.isClockRunning) {
                if (state.currentLocation != WorkLocation.OFFICE) {
                    switchLocationUseCase(WorkLocation.OFFICE)
                    notifyServices()
                }
            } else {
                clockInUseCase(WorkLocation.OFFICE)
                notifyServices()
            }
            finish()
        }
    }

    private fun handleHomeAction(state: WidgetState) {
        lifecycleScope.launch {
            if (state.isClockRunning) {
                if (state.currentLocation != WorkLocation.HOME_OFFICE) {
                    switchLocationUseCase(WorkLocation.HOME_OFFICE)
                    notifyServices()
                }
            } else {
                clockInUseCase(WorkLocation.HOME_OFFICE)
                notifyServices()
            }
            finish()
        }
    }

    private fun handlePauseResumeAction(isPaused: Boolean) {
        lifecycleScope.launch {
            if (isPaused) {
                clockInUseCase()
                notifyServices()
            } else {
                pauseWorkUseCase()
                notifyServices()
            }
            finish()
        }
    }

    private fun handleStopAction() {
        lifecycleScope.launch {
            autoClockOutUseCase()
            stopService(Intent(this@FlexQuickActionsDialogActivity, WorkTimerService::class.java))
            notifyTile()
            dataChangeEventBus.emit(DataChangeEvent.WorkDayChanged)
            FlexWidgetUpdater.updateAll(this@FlexQuickActionsDialogActivity)
            finish()
        }
    }

    private suspend fun notifyServices() {
        val settings = settingsRepository.getSettings().first()
        if (settings.workTimerNotificationEnabled) {
            startForegroundService(
                Intent(this, WorkTimerService::class.java).apply {
                    action = WorkTimerService.ACTION_UPDATE
                }
            )
        }
        notifyTile()
        dataChangeEventBus.emit(DataChangeEvent.WorkDayChanged)
        FlexWidgetUpdater.updateAll(this)
    }

    private fun notifyTile() {
        try {
            TileService.requestListeningState(
                this,
                ComponentName(this, QuickSettingsTileService::class.java)
            )
        } catch (e: Exception) {
            Log.e("FlexQuickActionsDialog", "Failed to notify tile", e)
        }
    }
}

@Composable
internal fun QuickActionsDialogContent(
    state: WidgetState,
    isPaused: Boolean,
    onOfficeClick: () -> Unit,
    onHomeClick: () -> Unit,
    onPauseResumeClick: () -> Unit,
    onStopClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* Consume clicks to prevent dismissing dialog */ }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Stempeln",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                val isRunning = state.isClockRunning
                val isOffice = isRunning && state.currentLocation == WorkLocation.OFFICE
                val isHome = isRunning && state.currentLocation == WorkLocation.HOME_OFFICE

                DialogActionItem(
                    iconRes = R.drawable.ic_widget_office,
                    title = "Büro",
                    isActive = isOffice,
                    isEnabled = true,
                    onClick = onOfficeClick
                )

                Spacer(modifier = Modifier.height(6.dp))

                DialogActionItem(
                    iconRes = R.drawable.ic_widget_home,
                    title = "Home-Office",
                    isActive = isHome,
                    isEnabled = true,
                    onClick = onHomeClick
                )

                Spacer(modifier = Modifier.height(6.dp))

                val pauseResumeTitle = if (isPaused) "Weiterarbeiten" else "Pause"
                val pauseResumeIcon = if (isPaused) R.drawable.ic_widget_play else R.drawable.ic_widget_pause
                val pauseResumeEnabled = isRunning || isPaused

                DialogActionItem(
                    iconRes = pauseResumeIcon,
                    title = pauseResumeTitle,
                    isActive = isPaused,
                    isEnabled = pauseResumeEnabled,
                    onClick = onPauseResumeClick
                )

                Spacer(modifier = Modifier.height(6.dp))

                val stopEnabled = isRunning || isPaused
                DialogActionItem(
                    iconRes = R.drawable.ic_widget_stop,
                    title = "Feierabend",
                    isActive = false,
                    isEnabled = stopEnabled,
                    isDestructive = true,
                    onClick = onStopClick
                )
            }
        }
    }
}

@Composable
private fun DialogActionItem(
    iconRes: Int,
    title: String,
    isActive: Boolean = false,
    isEnabled: Boolean = true,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isActive -> MaterialTheme.colorScheme.primary
        isDestructive && isEnabled -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    val backgroundColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        Color.Transparent
    }

    Surface(
        onClick = onClick,
        enabled = isEnabled,
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 16.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )

            if (isActive) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "Aktiv",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
