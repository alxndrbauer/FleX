package com.flex.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flex.BuildConfig
import com.flex.R
import com.flex.data.update.UpdateChecker
import com.flex.data.update.UpdateDownloader
import com.flex.data.update.UpdateInfo
import com.flex.domain.model.AppIconVariant
import com.flex.domain.model.FederalState
import com.flex.domain.model.ThemeMode
import com.flex.ui.update.UpdateDialog
import com.flex.ui.yearchange.YearChangeDialog
import com.flex.ui.yearchange.YearChangeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToBackup: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToGeofence: () -> Unit = {},
    onNavigateToWifi: () -> Unit = {},
    onNavigateToQuotaRules: () -> Unit = {},
    onNavigateToWorkTimeRules: () -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    onShowOnboarding: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    yearChangeViewModel: YearChangeViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val quotaRules by viewModel.quotaRules.collectAsState()
    val workTimeRules by viewModel.workTimeRules.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val appIconVariant by viewModel.appIconVariant.collectAsState()
    val yearChangeState by yearChangeViewModel.uiState.collectAsState()

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var noUpdateFound by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    // Dialog visibility state
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAppIconDialog by remember { mutableStateOf(false) }
    var showDailyWorkTimeDialog by remember { mutableStateOf(false) }
    var showMonthlyWorkTimeDialog by remember { mutableStateOf(false) }
    var showDefaultStartTimeDialog by remember { mutableStateOf(false) }
    var showQuotaDialog by remember { mutableStateOf(false) }
    var showFlextimeDialog by remember { mutableStateOf(false) }
    var showOvertimeDialog by remember { mutableStateOf(false) }
    var showVacationDialog by remember { mutableStateOf(false) }
    var showFederalStateDialog by remember { mutableStateOf(false) }

    // Format helpers
    fun Int.toHoursMinutes(): String {
        val h = this / 60
        val m = this % 60
        return if (m == 0) "${h}h" else "${h}h ${m}min"
    }

    fun Int.toSignedHoursMinutes(): String {
        val abs = kotlin.math.abs(this)
        val h = abs / 60
        val m = abs % 60
        val sign = if (this < 0) "-" else "+"
        return "$sign${h}h ${m}min"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {
        // ── Modern Header-Pill ───────────────────────────────────────
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Einstellungen",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "FleX Konfiguration & Präferenzen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // ── Darstellung ──────────────────────────────────────────────
        item { SettingsSectionHeader("Darstellung") }
        item {
            SettingsGroup {
                SettingsItem(
                    title = "Design",
                    subtitle = when (themeMode) {
                        ThemeMode.SYSTEM -> "System"
                        ThemeMode.LIGHT -> "Hell"
                        ThemeMode.DARK -> "Dunkel"
                        ThemeMode.AMOLED -> "AMOLED"
                    },
                    icon = Icons.Default.Palette,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { showThemeDialog = true }
                )
                SettingsGroupDivider()
                SettingsItem(
                    title = "App-Symbol & Name",
                    subtitle = appIconVariant.displayName,
                    icon = Icons.Default.PhoneAndroid,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { showAppIconDialog = true }
                )
            }
        }

        // ── Arbeitszeit ───────────────────────────────────────────────
        item { SettingsSectionHeader("Arbeitszeit") }
        item {
            SettingsGroup {
                SettingsItem(
                    title = "Tagesarbeitszeit",
                    subtitle = settings.dailyWorkMinutes.toHoursMinutes(),
                    icon = Icons.Default.Schedule,
                    iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { showDailyWorkTimeDialog = true }
                )
                SettingsGroupDivider()
                SettingsItem(
                    title = "Monatssoll",
                    subtitle = settings.monthlyWorkMinutes.toHoursMinutes(),
                    icon = Icons.Default.CalendarMonth,
                    iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { showMonthlyWorkTimeDialog = true }
                )
                SettingsGroupDivider()
                SettingsItem(
                    title = "Standard-Startzeit",
                    subtitle = settings.defaultStartTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) + " Uhr",
                    icon = Icons.Default.Schedule,
                    iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { showDefaultStartTimeDialog = true }
                )
                SettingsGroupDivider()
                SettingsItem(
                    title = "Arbeitszeit-Zeiträume",
                    subtitle = if (workTimeRules.isEmpty()) "Keine Regeln – Standard gilt" else "${workTimeRules.size} ${if (workTimeRules.size == 1) "Regel" else "Regeln"} aktiv",
                    icon = Icons.Default.DateRange,
                    iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { onNavigateToWorkTimeRules() }
                )
            }
        }

        // ── Büro-Quote ────────────────────────────────────────────────
        item { SettingsSectionHeader("Büro-Quote") }
        item {
            SettingsGroup {
                SettingsItem(
                    title = "Standard-Quote",
                    subtitle = "${settings.officeQuotaPercent}% · ${settings.officeQuotaMinDays} Tage min.",
                    icon = Icons.Default.Business,
                    iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { showQuotaDialog = true }
                )
                SettingsGroupDivider()
                SettingsItem(
                    title = "Quoten-Zeiträume",
                    subtitle = if (quotaRules.isEmpty()) "Keine Regeln – Standard gilt" else "${quotaRules.size} ${if (quotaRules.size == 1) "Regel" else "Regeln"} aktiv",
                    icon = Icons.Default.DateRange,
                    iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { onNavigateToQuotaRules() }
                )
            }
        }

        // ── Urlaub & Gleitzeit ────────────────────────────────────────
        item { SettingsSectionHeader("Urlaub & Gleitzeit") }
        item {
            SettingsGroup {
                SettingsItem(
                    title = "Gleitzeit-Anfangssaldo",
                    subtitle = settings.initialFlextimeMinutes.toSignedHoursMinutes(),
                    icon = Icons.Default.SwapHoriz,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { showFlextimeDialog = true }
                )
                SettingsGroupDivider()
                SettingsItem(
                    title = "Überstunden-Anfangssaldo",
                    subtitle = settings.initialOvertimeMinutes.toSignedHoursMinutes(),
                    icon = Icons.Default.Timer,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { showOvertimeDialog = true }
                )
                SettingsGroupDivider()
                SettingsItem(
                    title = "Urlaubstage & Überträge",
                    subtitle = "${settings.annualVacationDays} Jahrestage · ${settings.carryOverVacationDays} Übertrag · ${settings.specialVacationDays} Sonder",
                    icon = Icons.Default.BeachAccess,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { showVacationDialog = true }
                )
            }
        }

        // ── Automatisierung ───────────────────────────────────────────
        item { SettingsSectionHeader("Automatisierung") }
        item {
            SettingsGroup {
                SettingsItem(
                    title = "Geofencing",
                    subtitle = if (settings.geofenceEnabled) "Aktiviert · ${settings.geofenceAddress.ifBlank { "Kein Standort" }}" else "Deaktiviert",
                    icon = Icons.Default.LocationOn,
                    iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { onNavigateToGeofence() }
                )
                SettingsGroupDivider()
                SettingsItem(
                    title = "WLAN Auto-Stempeln",
                    subtitle = if (settings.wifiAutoStampEnabled) "Aktiviert · ${settings.wifiSsid.ifBlank { "Kein WLAN" }}" else "Deaktiviert",
                    icon = Icons.Default.Wifi,
                    iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { onNavigateToWifi() }
                )
            }
        }

        // ── Benachrichtigungen ────────────────────────────────────────
        item { SettingsSectionHeader("Benachrichtigungen") }
        item {
            SettingsGroup {
                SettingsItem(
                    title = "Pausen-Warnung",
                    subtitle = "Warnung bei Verstößen gegen §4 ArbZG",
                    icon = Icons.Default.Notifications,
                    iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = {
                        viewModel.updateSettings(settings.copy(breakWarningEnabled = !settings.breakWarningEnabled))
                    },
                    trailing = {
                        Switch(
                            checked = settings.breakWarningEnabled,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.updateSettings(settings.copy(breakWarningEnabled = it))
                            }
                        )
                    }
                )
                SettingsGroupDivider()
                SettingsItem(
                    title = "Laufzeit-Anzeige",
                    subtitle = "Zeigt die aktive Arbeitszeit in der Statusleiste",
                    icon = Icons.Default.Timer,
                    iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = {
                        viewModel.updateSettings(settings.copy(workTimerNotificationEnabled = !settings.workTimerNotificationEnabled))
                    },
                    trailing = {
                        Switch(
                            checked = settings.workTimerNotificationEnabled,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.updateSettings(settings.copy(workTimerNotificationEnabled = it))
                            }
                        )
                    }
                )
                SettingsGroupDivider()
                SettingsItem(
                    title = "Schnelleinstellungen",
                    subtitle = "Kachel zum Kontrollzentrum hinzufügen",
                    icon = Icons.Default.Widgets,
                    iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    trailing = {
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val statusBarManager = context.getSystemService(android.app.StatusBarManager::class.java)
                                statusBarManager?.requestAddTileService(
                                    android.content.ComponentName(context, com.flex.tile.QuickSettingsTileService::class.java),
                                    context.getString(com.flex.R.string.app_name),
                                    android.graphics.drawable.Icon.createWithResource(context, com.flex.R.drawable.ic_notification),
                                    context.mainExecutor
                                ) { /* callback */ }
                            }
                        ) {
                            Text("Hinzufügen")
                        }
                    }
                )
            }
        }

        // ── Kalender ──────────────────────────────────────────────────
        item { SettingsSectionHeader("Kalender") }
        item {
            SettingsGroup {
                SettingsItem(
                    title = "Kalender-Synchronisation & Export",
                    subtitle = if (settings.calendarSyncEnabled) "Synchronisation aktiv" else "Deaktiviert",
                    icon = Icons.Default.CalendarToday,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { onNavigateToCalendar() }
                )
            }
        }

        // ── Regional ─────────────────────────────────────────────────
        item { SettingsSectionHeader("Regional") }
        item {
            SettingsGroup {
                SettingsItem(
                    title = "Bundesland",
                    subtitle = settings.federalState.displayName,
                    icon = Icons.Default.LocationOn,
                    iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { showFederalStateDialog = true }
                )
            }
        }

        // ── Daten & Backup ───────────────────────────────────────────
        item { SettingsSectionHeader("Daten & Backup") }
        item {
            SettingsGroup {
                SettingsItem(
                    title = "Backup & Wiederherstellung",
                    subtitle = "Daten exportieren & importieren",
                    icon = Icons.Default.Backup,
                    iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { onNavigateToBackup() }
                )
            }
        }

        // ── Info ──────────────────────────────────────────────────────
        item { SettingsSectionHeader("Info") }
        item {
            SettingsGroup {
                SettingsItem(
                    title = "Neues Jahr beginnen",
                    subtitle = "Jahr ${settings.settingsYear + 1} vorbereiten",
                    icon = Icons.Default.CalendarMonth,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { yearChangeViewModel.openDialog() }
                )
                SettingsGroupDivider()
                SettingsItem(
                    title = "Einführung wiederholen",
                    subtitle = "Onboarding erneut anzeigen",
                    icon = Icons.Default.Update,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { onShowOnboarding() }
                )
                SettingsGroupDivider()
                SettingsItem(
                    title = "Nach Update suchen",
                    subtitle = "Auf neue Version prüfen",
                    icon = Icons.Default.Update,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailing = {
                        if (isCheckingUpdate) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        else ChevronTrailing()
                    },
                    onClick = {
                        if (!isCheckingUpdate) {
                            scope.launch {
                                isCheckingUpdate = true
                                val update = UpdateChecker.checkForUpdate(BuildConfig.VERSION_CODE)
                                isCheckingUpdate = false
                                if (update != null) pendingUpdate = update else noUpdateFound = true
                            }
                        }
                    }
                )
                SettingsGroupDivider()
                SettingsItem(
                    title = "Über FleX",
                    subtitle = "Version & Kontakt",
                    icon = Icons.Default.Info,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { onNavigateToAbout() }
                )
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────

    pendingUpdate?.let { update ->
        UpdateDialog(
            updateInfo = update,
            isDownloading = isDownloading,
            onDismiss = { pendingUpdate = null },
            onUpdate = {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    UpdateDownloader.openInstallPermissionSettings(context)
                } else {
                    scope.launch {
                        isDownloading = true
                        runCatching {
                            UpdateDownloader.downloadAndInstall(context, update.downloadUrl)
                        }.onFailure { error ->
                            android.widget.Toast.makeText(
                                context,
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

    if (noUpdateFound) {
        AlertDialog(
            onDismissRequest = { noUpdateFound = false },
            title = { Text("Kein Update verfügbar") },
            text = { Text("Du verwendest bereits die neueste Version.") },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    noUpdateFound = false
                }) {
                    Text("OK")
                }
            }
        )
    }

    if (yearChangeState.showDialog) {
        YearChangeDialog(
            state = yearChangeState,
            onDismiss = { yearChangeViewModel.dismiss() },
            onConfirm = { carryOver, annual ->
                yearChangeViewModel.applyYearChange(carryOver, annual)
            }
        )
    }

    if (showFederalStateDialog) {
        AlertDialog(
            onDismissRequest = { showFederalStateDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text("Bundesland")
                }
            },
            text = {
                LazyColumn {
                    items(FederalState.entries) { state ->
                        ListItem(
                            headlineContent = { Text(state.displayName) },
                            modifier = Modifier.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.updateFederalState(state)
                                showFederalStateDialog = false
                            },
                            trailingContent = {
                                if (settings.federalState == state) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Ausgewählt",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showFederalStateDialog = false
                }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Design")
                }
            },
            text = {
                val options = listOf(
                    ThemeMode.SYSTEM to "System",
                    ThemeMode.LIGHT to "Hell",
                    ThemeMode.DARK to "Dunkel",
                    ThemeMode.AMOLED to "AMOLED"
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setThemeMode(mode)
                            },
                            selected = themeMode == mode,
                            label = { Text(label) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showThemeDialog = false
                }) {
                    Text("Schließen")
                }
            }
        )
    }

    if (showAppIconDialog) {
        AlertDialog(
            onDismissRequest = { showAppIconDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("App-Symbol & Name")
                }
            },
            text = {
                Column {
                    Text(
                        "Änderung wird nach kurzer Zeit im Launcher sichtbar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AppIconVariant.entries.forEach { variant ->
                            AppIconVariantItem(
                                variant = variant,
                                isSelected = appIconVariant == variant,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.setAppIconVariant(variant)
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showAppIconDialog = false
                }) {
                    Text("Schließen")
                }
            }
        )
    }

    if (showDailyWorkTimeDialog) {
        WorkTimeDialog(
            title = "Tagesarbeitszeit",
            initialHours = settings.dailyWorkMinutes / 60,
            initialMinutes = settings.dailyWorkMinutes % 60,
            onDismiss = { showDailyWorkTimeDialog = false },
            onSave = { h, m ->
                viewModel.updateSettings(settings.copy(dailyWorkMinutes = h * 60 + m))
                showDailyWorkTimeDialog = false
            }
        )
    }

    if (showMonthlyWorkTimeDialog) {
        WorkTimeDialog(
            title = "Monatssoll",
            initialHours = settings.monthlyWorkMinutes / 60,
            initialMinutes = settings.monthlyWorkMinutes % 60,
            onDismiss = { showMonthlyWorkTimeDialog = false },
            onSave = { h, m ->
                viewModel.updateSettings(settings.copy(monthlyWorkMinutes = h * 60 + m))
                showMonthlyWorkTimeDialog = false
            }
        )
    }

    if (showDefaultStartTimeDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = settings.defaultStartTime.hour,
            initialMinute = settings.defaultStartTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showDefaultStartTimeDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text("Standard-Startzeit")
                }
            },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val newTime = java.time.LocalTime.of(timePickerState.hour, timePickerState.minute)
                    viewModel.updateSettings(settings.copy(defaultStartTime = newTime))
                    showDefaultStartTimeDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showDefaultStartTimeDialog = false
                }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    if (showQuotaDialog) {
        QuotaDialog(
            initialPercent = settings.officeQuotaPercent,
            initialMinDays = settings.officeQuotaMinDays,
            onDismiss = { showQuotaDialog = false },
            onSave = { p, d ->
                viewModel.updateSettings(settings.copy(officeQuotaPercent = p, officeQuotaMinDays = d))
                showQuotaDialog = false
            }
        )
    }

    if (showFlextimeDialog) {
        val abs = kotlin.math.abs(settings.initialFlextimeMinutes)
        BalanceDialog(
            title = "Gleitzeit-Anfangssaldo",
            icon = Icons.Default.SwapHoriz,
            initialSign = if (settings.initialFlextimeMinutes < 0) "-" else "+",
            initialHours = abs / 60,
            initialMinutes = abs % 60,
            onDismiss = { showFlextimeDialog = false },
            onSave = { sign, h, m ->
                val total = (h * 60 + m) * if (sign == "-") -1 else 1
                viewModel.updateSettings(settings.copy(initialFlextimeMinutes = total))
                showFlextimeDialog = false
            }
        )
    }

    if (showOvertimeDialog) {
        val abs = kotlin.math.abs(settings.initialOvertimeMinutes)
        BalanceDialog(
            title = "Überstunden-Anfangssaldo",
            icon = Icons.Default.Timer,
            initialSign = if (settings.initialOvertimeMinutes < 0) "-" else "+",
            initialHours = abs / 60,
            initialMinutes = abs % 60,
            onDismiss = { showOvertimeDialog = false },
            onSave = { sign, h, m ->
                val total = (h * 60 + m) * if (sign == "-") -1 else 1
                viewModel.updateSettings(settings.copy(initialOvertimeMinutes = total))
                showOvertimeDialog = false
            }
        )
    }

    if (showVacationDialog) {
        VacationDialog(
            annual = settings.annualVacationDays,
            carryOver = settings.carryOverVacationDays,
            special = settings.specialVacationDays,
            onDismiss = { showVacationDialog = false },
            onSave = { a, c, s ->
                viewModel.updateSettings(settings.copy(annualVacationDays = a, carryOverVacationDays = c, specialVacationDays = s))
                showVacationDialog = false
            }
        )
    }
}

// ── Helper Composables ──────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(
    title: String,
    icon: ImageVector? = null,
    accentColor: Color? = null
) {
    val categoryIcon = icon ?: when (title) {
        "Darstellung" -> Icons.Default.Palette
        "Arbeitszeit" -> Icons.Default.Schedule
        "Büro-Quote" -> Icons.Default.Business
        "Urlaub & Gleitzeit", "Salden", "Urlaub" -> Icons.Default.BeachAccess
        "Automatisierung", "Automatisch stempeln" -> Icons.Default.Sync
        "Benachrichtigungen" -> Icons.Default.Notifications
        "Kalender" -> Icons.Default.CalendarToday
        "Regional" -> Icons.Default.LocationOn
        "Daten & Backup", "Datenverwaltung" -> Icons.Default.Backup
        "Info", "App" -> Icons.Default.Info
        else -> null
    }

    val color = accentColor ?: when (title) {
        "Darstellung" -> MaterialTheme.colorScheme.primary
        "Arbeitszeit" -> MaterialTheme.colorScheme.tertiary
        "Büro-Quote" -> MaterialTheme.colorScheme.secondary
        "Urlaub & Gleitzeit", "Salden", "Urlaub" -> MaterialTheme.colorScheme.primary
        "Automatisierung", "Automatisch stempeln" -> MaterialTheme.colorScheme.tertiary
        "Benachrichtigungen" -> MaterialTheme.colorScheme.secondary
        "Kalender" -> MaterialTheme.colorScheme.primary
        "Regional" -> MaterialTheme.colorScheme.secondary
        "Daten & Backup", "Datenverwaltung" -> MaterialTheme.colorScheme.tertiary
        "Info", "App" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (categoryIcon != null) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                letterSpacing = 1.2.sp
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsGroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    trailing: @Composable (() -> Unit)? = { ChevronTrailing() },
    onClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val clickableModifier = if (onClick != null) {
        Modifier.clickable {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    } else {
        Modifier
    }

    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = if (subtitle != null) {
            {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else null,
        leadingContent = {
            SettingsIcon(
                icon = icon,
                containerColor = iconContainerColor,
                contentColor = iconContentColor
            )
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = clickableModifier
    )
}

@Composable
private fun SettingsIcon(icon: ImageVector, containerColor: Color, contentColor: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(containerColor, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ChevronTrailing() {
    Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ── Dialog Composables ──────────────────────────────────────────────────

@Composable
private fun DialogStepperButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun WorkTimeDialog(
    title: String,
    initialHours: Int,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit
) {
    var hours by remember { mutableStateOf(initialHours.toString()) }
    var minutes by remember { mutableStateOf(initialMinutes.toString()) }
    val haptic = LocalHapticFeedback.current

    val parsedHours = hours.toIntOrNull() ?: 0
    val parsedMinutes = minutes.toIntOrNull() ?: 0
    val totalMinutes = (parsedHours * 60 + parsedMinutes).coerceAtLeast(0)

    val isHoursValid = hours.isNotBlank() && (hours.toIntOrNull() != null) && (hours.toIntOrNull() ?: -1) in 0..744
    val isMinutesValid = minutes.isNotBlank() && (minutes.toIntOrNull() != null) && (minutes.toIntOrNull() ?: -1) in 0..59
    val isValid = isHoursValid && isMinutesValid

    fun adjustMinutes(delta: Int) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val cur = ((hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)) + delta
        val safe = cur.coerceAtLeast(0)
        hours = (safe / 60).toString()
        minutes = (safe % 60).toString()
    }

    val quickOptions = if (initialHours >= 20) {
        listOf(-60 to "-1h", -15 to "-15m", 15 to "+15m", 60 to "+1h")
    } else {
        listOf(-30 to "-30m", -15 to "-15m", 15 to "+15m", 30 to "+30m")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(title)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Total duration banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Gesamtdauer:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val displayH = totalMinutes / 60
                        val displayM = totalMinutes % 60
                        Text(
                            text = "${displayH}h ${displayM.toString().padStart(2, '0')}min",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                // Hours and Minutes input with stepper buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Std.") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = !isHoursValid,
                        supportingText = if (!isHoursValid) {
                            { Text("0–744 Std.") }
                        } else null,
                        trailingIcon = {
                            Row(
                                modifier = Modifier.padding(end = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DialogStepperButton(
                                    icon = Icons.Default.Remove,
                                    contentDescription = "Stunde abziehen",
                                    enabled = parsedHours > 0,
                                    onClick = {
                                        hours = (parsedHours - 1).coerceAtLeast(0).toString()
                                    }
                                )
                                DialogStepperButton(
                                    icon = Icons.Default.Add,
                                    contentDescription = "Stunde hinzufügen",
                                    enabled = parsedHours < 744,
                                    onClick = {
                                        hours = (parsedHours + 1).toString()
                                    }
                                )
                            }
                        }
                    )
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Min.") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = !isMinutesValid,
                        supportingText = if (!isMinutesValid) {
                            { Text("0–59 Min.") }
                        } else null,
                        trailingIcon = {
                            Row(
                                modifier = Modifier.padding(end = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DialogStepperButton(
                                    icon = Icons.Default.Remove,
                                    contentDescription = "15 Minuten abziehen",
                                    enabled = parsedMinutes >= 15 || parsedHours > 0,
                                    onClick = {
                                        adjustMinutes(-15)
                                    }
                                )
                                DialogStepperButton(
                                    icon = Icons.Default.Add,
                                    contentDescription = "15 Minuten hinzufügen",
                                    enabled = totalMinutes < 744 * 60,
                                    onClick = {
                                        adjustMinutes(15)
                                    }
                                )
                            }
                        }
                    )
                }

                // Quick delta chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Schnellanpassung",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickOptions.forEach { (delta, label) ->
                            val canApply = totalMinutes + delta >= 0
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (canApply) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = canApply) { adjustMinutes(delta) }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (canApply) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSave(parsedHours, parsedMinutes)
                }
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                }
            ) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
private fun BalanceDialog(
    title: String,
    initialSign: String,
    initialHours: Int,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int) -> Unit,
    icon: ImageVector = Icons.Default.SwapHoriz
) {
    var sign by remember { mutableStateOf(initialSign) }
    var hours by remember { mutableStateOf(initialHours.toString()) }
    var minutes by remember { mutableStateOf(initialMinutes.toString()) }
    val haptic = LocalHapticFeedback.current

    val parsedHours = hours.toIntOrNull() ?: 0
    val parsedMinutes = minutes.toIntOrNull() ?: 0

    val isHoursValid = hours.isNotBlank() && (hours.toIntOrNull() != null) && (hours.toIntOrNull() ?: -1) in 0..999
    val isMinutesValid = minutes.isNotBlank() && (minutes.toIntOrNull() != null) && (minutes.toIntOrNull() ?: -1) in 0..59
    val isValid = isHoursValid && isMinutesValid

    val isPlus = sign == "+"
    val bannerContainerColor = if (isPlus) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                               else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
    val bannerTextColor = if (isPlus) MaterialTheme.colorScheme.onTertiaryContainer
                          else MaterialTheme.colorScheme.onErrorContainer

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isPlus) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )
                Text(title)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Live preview badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = bannerContainerColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Saldo-Vorschau:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$sign${parsedHours}h ${parsedMinutes.toString().padStart(2, '0')}min",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = bannerTextColor
                        )
                    }
                }

                // Vorzeichen Toggle: + / -
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Plus Button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isPlus) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = if (isPlus) BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                sign = "+"
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = if (isPlus) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Plus (+)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isPlus) FontWeight.Bold else FontWeight.Medium,
                                color = if (isPlus) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Minus Button
                    val isMinus = sign == "-"
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isMinus) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = if (isMinus) BorderStroke(1.5.dp, MaterialTheme.colorScheme.error) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                sign = "-"
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = null,
                                tint = if (isMinus) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Minus (−)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isMinus) FontWeight.Bold else FontWeight.Medium,
                                color = if (isMinus) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Hours and Minutes fields with Steppers
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Stunden") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = !isHoursValid,
                        supportingText = if (!isHoursValid) {
                            { Text("0–999 Std.") }
                        } else null,
                        trailingIcon = {
                            Row(
                                modifier = Modifier.padding(end = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DialogStepperButton(
                                    icon = Icons.Default.Remove,
                                    contentDescription = "Stunde abziehen",
                                    enabled = parsedHours > 0,
                                    onClick = {
                                        hours = (parsedHours - 1).coerceAtLeast(0).toString()
                                    }
                                )
                                DialogStepperButton(
                                    icon = Icons.Default.Add,
                                    contentDescription = "Stunde hinzufügen",
                                    enabled = parsedHours < 999,
                                    onClick = {
                                        hours = (parsedHours + 1).toString()
                                    }
                                )
                            }
                        }
                    )
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Minuten") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = !isMinutesValid,
                        supportingText = if (!isMinutesValid) {
                            { Text("0–59 Min.") }
                        } else null,
                        trailingIcon = {
                            Row(
                                modifier = Modifier.padding(end = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DialogStepperButton(
                                    icon = Icons.Default.Remove,
                                    contentDescription = "15 Minuten abziehen",
                                    enabled = parsedMinutes >= 15 || parsedHours > 0,
                                    onClick = {
                                        val total = (parsedHours * 60 + parsedMinutes) - 15
                                        val safe = total.coerceAtLeast(0)
                                        hours = (safe / 60).toString()
                                        minutes = (safe % 60).toString()
                                    }
                                )
                                DialogStepperButton(
                                    icon = Icons.Default.Add,
                                    contentDescription = "15 Minuten hinzufügen",
                                    enabled = (parsedHours * 60 + parsedMinutes) < 999 * 60,
                                    onClick = {
                                        val total = (parsedHours * 60 + parsedMinutes) + 15
                                        hours = (total / 60).toString()
                                        minutes = (total % 60).toString()
                                    }
                                )
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSave(sign, parsedHours, parsedMinutes)
                }
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                }
            ) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
private fun QuotaDialog(
    initialPercent: Int,
    initialMinDays: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit
) {
    var percent by remember { mutableStateOf(initialPercent.toString()) }
    var minDays by remember { mutableStateOf(initialMinDays.toString()) }
    val haptic = LocalHapticFeedback.current

    val parsedPercent = percent.toIntOrNull()
    val parsedMinDays = minDays.toIntOrNull()
    val isPercentValid = parsedPercent != null && parsedPercent in 0..100
    val isMinDaysValid = parsedMinDays != null && parsedMinDays in 0..7
    val isValid = isPercentValid && isMinDaysValid

    val presetPercentages = listOf(20, 40, 50, 60, 80)
    val presetDays = listOf(1, 2, 3, 4, 5)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Business,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Büro-Quote (Standard)")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Summary preview banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ziel-Quote:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${parsedPercent ?: 0}% · ${parsedMinDays ?: 0} Tage min.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Percentage input + suggestions
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = percent,
                        onValueChange = { percent = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Quote in Prozent (%)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = !isPercentValid,
                        supportingText = if (!isPercentValid) {
                            { Text("0 bis 100 % eingeben") }
                        } else null
                    )
                    Text(
                        text = "Vorschläge für Quote",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presetPercentages.forEach { preset ->
                            val selected = parsedPercent == preset
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        percent = preset.toString()
                                    }
                            ) {
                                Text(
                                    text = "$preset%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Min days input + quick chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = minDays,
                        onValueChange = { minDays = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Mindesttage (z. B. pro Woche)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = !isMinDaysValid,
                        supportingText = if (!isMinDaysValid) {
                            { Text("0 bis 7 Tage eingeben") }
                        } else null,
                        trailingIcon = {
                            Row(
                                modifier = Modifier.padding(end = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DialogStepperButton(
                                    icon = Icons.Default.Remove,
                                    contentDescription = "Tag verringern",
                                    enabled = (parsedMinDays ?: 0) > 0,
                                    onClick = {
                                        minDays = ((parsedMinDays ?: 0) - 1).coerceAtLeast(0).toString()
                                    }
                                )
                                DialogStepperButton(
                                    icon = Icons.Default.Add,
                                    contentDescription = "Tag erhöhen",
                                    enabled = (parsedMinDays ?: 0) < 7,
                                    onClick = {
                                        minDays = ((parsedMinDays ?: 0) + 1).coerceAtMost(7).toString()
                                    }
                                )
                            }
                        }
                    )
                    Text(
                        text = "Schnellauswahl Tage",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presetDays.forEach { days ->
                            val selected = parsedMinDays == days
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        minDays = days.toString()
                                    }
                            ) {
                                Text(
                                    text = if (days == 1) "1 Tag" else "$days T.",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSave(parsedPercent ?: initialPercent, parsedMinDays ?: initialMinDays)
                }
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                }
            ) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
private fun VacationDialog(
    annual: Int,
    carryOver: Int,
    special: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Int) -> Unit
) {
    var annualText by remember { mutableStateOf(annual.toString()) }
    var carryOverText by remember { mutableStateOf(carryOver.toString()) }
    var specialText by remember { mutableStateOf(special.toString()) }
    val haptic = LocalHapticFeedback.current

    val parsedAnnual = annualText.toIntOrNull() ?: 0
    val parsedCarryOver = carryOverText.toIntOrNull() ?: 0
    val parsedSpecial = specialText.toIntOrNull() ?: 0
    val totalDays = (parsedAnnual + parsedCarryOver + parsedSpecial).coerceAtLeast(0)

    val isAnnualValid = annualText.isNotBlank() && (annualText.toIntOrNull() != null) && (annualText.toIntOrNull() ?: -1) in 0..365
    val isCarryOverValid = carryOverText.isNotBlank() && (carryOverText.toIntOrNull() != null) && (carryOverText.toIntOrNull() ?: -1) in 0..365
    val isSpecialValid = specialText.isNotBlank() && (specialText.toIntOrNull() != null) && (specialText.toIntOrNull() ?: -1) in 0..365
    val isValid = isAnnualValid && isCarryOverValid && isSpecialValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BeachAccess,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Urlaubsanspruch")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Summen-Vorschau
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Gesamter Anspruch",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "$parsedAnnual reg. + $parsedCarryOver Vorjahr + $parsedSpecial Sonder",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "$totalDays Tage",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Jahresurlaub
                OutlinedTextField(
                    value = annualText,
                    onValueChange = { annualText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Jahresurlaub") },
                    supportingText = { Text("Regulärer Urlaubsanspruch lt. Arbeitsvertrag") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = !isAnnualValid,
                    trailingIcon = {
                        Row(
                            modifier = Modifier.padding(end = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DialogStepperButton(
                                icon = Icons.Default.Remove,
                                contentDescription = "Urlaubstag abziehen",
                                enabled = parsedAnnual > 0,
                                onClick = {
                                    annualText = (parsedAnnual - 1).coerceAtLeast(0).toString()
                                }
                            )
                            DialogStepperButton(
                                icon = Icons.Default.Add,
                                contentDescription = "Urlaubstag hinzufügen",
                                enabled = parsedAnnual < 365,
                                onClick = {
                                    annualText = (parsedAnnual + 1).toString()
                                }
                            )
                        }
                    }
                )

                // Resturlaub Vorjahr
                OutlinedTextField(
                    value = carryOverText,
                    onValueChange = { carryOverText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Resturlaub Vorjahr") },
                    supportingText = { Text("Übertrag aus dem vergangenen Jahr") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = !isCarryOverValid,
                    trailingIcon = {
                        Row(
                            modifier = Modifier.padding(end = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DialogStepperButton(
                                icon = Icons.Default.Remove,
                                contentDescription = "Übertragstag abziehen",
                                enabled = parsedCarryOver > 0,
                                onClick = {
                                    carryOverText = (parsedCarryOver - 1).coerceAtLeast(0).toString()
                                }
                            )
                            DialogStepperButton(
                                icon = Icons.Default.Add,
                                contentDescription = "Übertragstag hinzufügen",
                                enabled = parsedCarryOver < 365,
                                onClick = {
                                    carryOverText = (parsedCarryOver + 1).toString()
                                }
                            )
                        }
                    }
                )

                // Sonderurlaub
                OutlinedTextField(
                    value = specialText,
                    onValueChange = { specialText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Sonderurlaub") },
                    supportingText = { Text("Z. B. Umzug/Jubiläum (verfällt am 31. Okt.)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = !isSpecialValid,
                    trailingIcon = {
                        Row(
                            modifier = Modifier.padding(end = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DialogStepperButton(
                                icon = Icons.Default.Remove,
                                contentDescription = "Sondertag abziehen",
                                enabled = parsedSpecial > 0,
                                onClick = {
                                    specialText = (parsedSpecial - 1).coerceAtLeast(0).toString()
                                }
                            )
                            DialogStepperButton(
                                icon = Icons.Default.Add,
                                contentDescription = "Sondertag hinzufügen",
                                enabled = parsedSpecial < 365,
                                onClick = {
                                    specialText = (parsedSpecial + 1).toString()
                                }
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSave(parsedAnnual, parsedCarryOver, parsedSpecial)
                }
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                }
            ) {
                Text("Abbrechen")
            }
        }
    )
}

// ── App Icon helpers ─────────────────────────────────────────────────────

@Composable
private fun AppIconVariant.foregroundRes() = when (this) {
    AppIconVariant.CLASSIC -> R.mipmap.ic_launcher_foreground
    AppIconVariant.VREMA   -> R.mipmap.ic_launcher_vrema_foreground
}

private fun AppIconVariant.bgColor() = when (this) {
    AppIconVariant.CLASSIC -> Color(0xFF000000)
    AppIconVariant.VREMA   -> Color(0xFF3DDC84)
}

@Composable
private fun AppIconVariantItem(variant: AppIconVariant, isSelected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier = Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(shape)
                .background(variant.bgColor())
                .then(
                    if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier
                )
        ) {
            Image(
                painter = painterResource(id = variant.foregroundRes()),
                contentDescription = variant.displayName,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            variant.appLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
