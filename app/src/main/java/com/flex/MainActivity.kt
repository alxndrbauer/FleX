package com.flex

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import android.content.Intent
import com.flex.data.local.OnboardingPreferences
import com.flex.data.local.ThemePreferences
import com.flex.data.update.UpdateChecker
import com.flex.data.update.UpdateDownloader
import com.flex.data.update.UpdateInfo
import com.flex.domain.model.ThemeMode
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.usecase.ClockInUseCase
import com.flex.notification.WorkTimerService
import com.flex.ui.navigation.FlexNavGraph
import com.flex.ui.navigation.Screen
import com.flex.ui.theme.FlexTheme
import com.flex.ui.update.UpdateDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var themePreferences: ThemePreferences
    @Inject lateinit var onboardingPreferences: OnboardingPreferences
    @Inject lateinit var clockInUseCase: ClockInUseCase
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var autoBookPlannedDays: com.flex.domain.usecase.AutoBookPlannedDaysUseCase

    private val initialRouteState = mutableStateOf<String?>(null)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    private val requestPromotedPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShortcutIntent(intent)
        requestNotificationPermissionIfNeeded()
        requestPromotedNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            val themeMode by themePreferences.themeModeFlow.collectAsState()
            val onboardingCompleted by onboardingPreferences.completedFlow.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }
            var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
            var isDownloading by remember { mutableStateOf(false) }

            FlexTheme(darkTheme = darkTheme) {
                FlexNavGraph(
                    onboardingCompleted = onboardingCompleted,
                    onOnboardingFinished = { onboardingPreferences.setCompleted() },
                    onOnboardingReset = { onboardingPreferences.reset() },
                    initialRoute = initialRouteState.value
                )
                pendingUpdate?.let { update ->
                    UpdateDialog(
                        updateInfo = update,
                        isDownloading = isDownloading,
                        onDismiss = { pendingUpdate = null },
                        onUpdate = {
                            if (!packageManager.canRequestPackageInstalls()) {
                                UpdateDownloader.openInstallPermissionSettings(this@MainActivity)
                            } else {
                                lifecycleScope.launch {
                                    isDownloading = true
                                    runCatching {
                                        UpdateDownloader.downloadAndInstall(
                                            context = this@MainActivity,
                                            downloadUrl = update.downloadUrl
                                        )
                                    }.onFailure { error ->
                                        android.widget.Toast.makeText(
                                            this@MainActivity,
                                            "Download fehlgeschlagen: ${error.localizedMessage ?: "Unbekannter Fehler"}",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    isDownloading = false
                                    pendingUpdate = null
                                }
                            }
                        }
                    )
                }
            }

            lifecycleScope.launch {
                pendingUpdate = UpdateChecker.checkForUpdate(BuildConfig.VERSION_CODE)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            autoBookPlannedDays()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    private fun handleShortcutIntent(intent: Intent?) {
        when (intent?.getStringExtra("shortcut_action")) {
            "CLOCK_IN_OFFICE" -> {
                lifecycleScope.launch {
                    clockInUseCase(WorkLocation.OFFICE)
                    val settings = settingsRepository.getSettings().first()
                    if (settings.workTimerNotificationEnabled) {
                        startForegroundService(Intent(this@MainActivity, WorkTimerService::class.java))
                    }
                }
            }
            "CLOCK_IN_HOME_OFFICE" -> {
                lifecycleScope.launch {
                    clockInUseCase(WorkLocation.HOME_OFFICE)
                    val settings = settingsRepository.getSettings().first()
                    if (settings.workTimerNotificationEnabled) {
                        startForegroundService(Intent(this@MainActivity, WorkTimerService::class.java))
                    }
                }
            }
            "NAVIGATE_MONTH" -> {
                initialRouteState.value = Screen.Month.route
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestPromotedNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 36 &&
            ContextCompat.checkSelfPermission(this, "android.permission.POST_PROMOTED_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestPromotedPermission.launch("android.permission.POST_PROMOTED_NOTIFICATIONS")
        }
    }
}
