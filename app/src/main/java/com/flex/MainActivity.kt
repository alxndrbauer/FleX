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
import com.flex.data.local.OnboardingPreferences
import com.flex.data.local.ThemePreferences
import com.flex.data.update.UpdateChecker
import com.flex.data.update.UpdateDownloader
import com.flex.data.update.UpdateInfo
import com.flex.domain.model.ThemeMode
import com.flex.ui.navigation.FlexNavGraph
import com.flex.ui.theme.FlexTheme
import com.flex.ui.update.UpdateDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var themePreferences: ThemePreferences
    @Inject lateinit var onboardingPreferences: OnboardingPreferences

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    private val requestPromotedPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    onOnboardingReset = { onboardingPreferences.reset() }
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

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
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
