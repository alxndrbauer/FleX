package com.flex.tile

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.AutoClockOutUseCase
import com.flex.domain.usecase.ClockInUseCase
import com.flex.domain.usecase.SwitchLocationUseCase
import com.flex.notification.WorkTimerService
import com.flex.ui.theme.FlexTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class QuickSettingsDialogActivity : ComponentActivity() {

    @Inject lateinit var workDayRepository: WorkDayRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var clockInUseCase: ClockInUseCase
    @Inject lateinit var autoClockOutUseCase: AutoClockOutUseCase
    @Inject lateinit var switchLocationUseCase: SwitchLocationUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        }

        setContent {
            FlexTheme {
                val today = remember { LocalDate.now() }
                val workDay by workDayRepository.getWorkDay(today).collectAsState(initial = null)
                val runningBlock = workDay?.timeBlocks?.find { it.endTime == null }

                AlertDialog(
                    onDismissRequest = { finish() },
                    title = {
                        Text(
                            text = if (runningBlock != null) "FleX – Arbeitszeit" else "FleX – Einstempeln",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (runningBlock != null) {
                                val targetLocation = if (runningBlock.location == WorkLocation.OFFICE) {
                                    WorkLocation.HOME_OFFICE
                                } else {
                                    WorkLocation.OFFICE
                                }
                                val switchTitle = if (targetLocation == WorkLocation.HOME_OFFICE) {
                                    "Zu Home-Office wechseln"
                                } else {
                                    "Zu Büro wechseln"
                                }
                                val switchIcon = if (targetLocation == WorkLocation.HOME_OFFICE) {
                                    Icons.Default.Home
                                } else {
                                    Icons.Default.Business
                                }

                                DialogOptionItem(
                                    icon = switchIcon,
                                    title = switchTitle,
                                    subtitle = "Aktuell: ${if (runningBlock.location == WorkLocation.HOME_OFFICE) "Home-Office" else "Büro"}",
                                    onClick = {
                                        lifecycleScope.launch {
                                            switchLocationUseCase(targetLocation)
                                            startForegroundService(
                                                Intent(this@QuickSettingsDialogActivity, WorkTimerService::class.java).apply {
                                                    action = WorkTimerService.ACTION_UPDATE
                                                }
                                            )
                                            notifyTileUpdate()
                                            finish()
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                DialogOptionItem(
                                    icon = Icons.Default.Stop,
                                    title = "Ausstempeln",
                                    subtitle = "Beendet die aktuelle Arbeitszeiterfassung",
                                    isDestructive = true,
                                    onClick = {
                                        lifecycleScope.launch {
                                            autoClockOutUseCase()
                                            stopService(Intent(this@QuickSettingsDialogActivity, WorkTimerService::class.java))
                                            notifyTileUpdate()
                                            finish()
                                        }
                                    }
                                )
                            } else {
                                DialogOptionItem(
                                    icon = Icons.Default.Business,
                                    title = "Büro",
                                    subtitle = "Zeiterfassung im Büro starten",
                                    onClick = {
                                        lifecycleScope.launch {
                                            clockIn(WorkLocation.OFFICE)
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                DialogOptionItem(
                                    icon = Icons.Default.Home,
                                    title = "Home-Office",
                                    subtitle = "Zeiterfassung im Home-Office starten",
                                    onClick = {
                                        lifecycleScope.launch {
                                            clockIn(WorkLocation.HOME_OFFICE)
                                        }
                                    }
                                )
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { finish() }) {
                            Text("Abbrechen")
                        }
                    }
                )
            }
        }
    }

    private suspend fun clockIn(location: WorkLocation) {
        clockInUseCase(location)
        val settings = settingsRepository.getSettings().first()
        if (settings.workTimerNotificationEnabled) {
            startForegroundService(Intent(this, WorkTimerService::class.java))
        }
        notifyTileUpdate()
        finish()
    }

    private fun notifyTileUpdate() {
        TileService.requestListeningState(
            this,
            ComponentName(this, QuickSettingsTileService::class.java)
        )
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
    }
}

@Composable
private fun DialogOptionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = if (isDestructive) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val contentColor = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.75f)
                )
            }
        }
    }
}
