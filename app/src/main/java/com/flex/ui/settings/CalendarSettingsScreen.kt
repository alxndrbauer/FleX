package com.flex.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flex.calendar.CalendarInfo
import com.flex.domain.model.FederalState
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    var showCalendarPickerDialog by remember { mutableStateOf(false) }
    var showCalendarTypesDialog by remember { mutableStateOf(false) }
    var showSyncRangeDialog by remember { mutableStateOf(false) }
    var showIcsRangeDialog by remember { mutableStateOf(false) }
    var showFederalStateDialog by remember { mutableStateOf(false) }
    var pendingEnableCalendarSync by remember { mutableStateOf(false) }
    var availableCalendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var pendingIcsContent by remember { mutableStateOf<String?>(null) }
    var pendingIcsCount by remember { mutableStateOf(0) }

    fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(hasCalendarPermission()) {
        if (hasCalendarPermission()) {
            availableCalendars = viewModel.getAvailableCalendars()
        }
    }

    val icsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        val count = pendingIcsCount
        if (uri != null) {
            val content = pendingIcsContent ?: return@rememberLauncherForActivityResult
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
            }
            scope.launch {
                snackbarHostState.showSnackbar("$count Einträge exportiert")
            }
        }
        pendingIcsContent = null
    }

    LaunchedEffect(pendingIcsContent) {
        if (pendingIcsContent != null) {
            icsLauncher.launch("flex_export.ics")
        }
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        if (granted && pendingEnableCalendarSync) {
            availableCalendars = viewModel.getAvailableCalendars()
            if (settings.calendarId != -1L) {
                viewModel.saveCalendarSettings(
                    enabled = true,
                    calendarId = settings.calendarId,
                    syncTypes = settings.calendarSyncTypes,
                    syncOffice = settings.calendarSyncOffice,
                    syncHomeOffice = settings.calendarSyncHomeOffice,
                    eventPrefix = settings.calendarEventPrefix,
                    noAlarm = settings.calendarEventNoAlarm
                )
            } else {
                showCalendarPickerDialog = true
            }
        }
        pendingEnableCalendarSync = false
    }

    fun requestCalendarPermissionOrPick() {
        pendingEnableCalendarSync = true
        if (hasCalendarPermission()) {
            availableCalendars = viewModel.getAvailableCalendars()
            if (settings.calendarId != -1L) {
                viewModel.saveCalendarSettings(
                    enabled = true,
                    calendarId = settings.calendarId,
                    syncTypes = settings.calendarSyncTypes,
                    syncOffice = settings.calendarSyncOffice,
                    syncHomeOffice = settings.calendarSyncHomeOffice,
                    eventPrefix = settings.calendarEventPrefix,
                    noAlarm = settings.calendarEventNoAlarm
                )
            } else {
                showCalendarPickerDialog = true
            }
            pendingEnableCalendarSync = false
        } else {
            calendarPermissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kalender & Feiertage") },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── 1. Google / Gerätekalender Synchronisation ────────────────
            CalendarSettingsSectionHeader("Gerätekalender & Sync")
            CalendarSettingsGroup {
                ListItem(
                    headlineContent = { Text("Mit Gerätekalender synchronisieren") },
                    supportingContent = {
                        if (settings.calendarSyncEnabled && settings.calendarId != -1L) {
                            val cal = availableCalendars.find { it.id == settings.calendarId }
                            Text(cal?.let { "${it.displayName} (${it.accountName})" } ?: "Aktiv – Kalender ausgewählt")
                        } else {
                            Text("Deaktiviert")
                        }
                    },
                    leadingContent = {
                        CalendarSettingsIcon(
                            Icons.Default.CalendarToday,
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settings.calendarSyncEnabled,
                            onCheckedChange = { enabled ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (enabled) {
                                    if (settings.calendarId != -1L && hasCalendarPermission()) {
                                        viewModel.saveCalendarSettings(
                                            enabled = true,
                                            calendarId = settings.calendarId,
                                            syncTypes = settings.calendarSyncTypes,
                                            syncOffice = settings.calendarSyncOffice,
                                            syncHomeOffice = settings.calendarSyncHomeOffice,
                                            eventPrefix = settings.calendarEventPrefix,
                                            noAlarm = settings.calendarEventNoAlarm
                                        )
                                    } else {
                                        requestCalendarPermissionOrPick()
                                    }
                                } else {
                                    viewModel.saveCalendarSettings(
                                        enabled = false,
                                        calendarId = settings.calendarId,
                                        syncTypes = settings.calendarSyncTypes,
                                        syncOffice = settings.calendarSyncOffice,
                                        syncHomeOffice = settings.calendarSyncHomeOffice,
                                        eventPrefix = settings.calendarEventPrefix,
                                        noAlarm = settings.calendarEventNoAlarm
                                    )
                                }
                            }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                )

                if (settings.calendarSyncEnabled) {
                    CalendarSettingsGroupDivider()
                    ListItem(
                        headlineContent = { Text("Kalender wechseln") },
                        supportingContent = {
                            val activeCal = availableCalendars.find { it.id == settings.calendarId }
                            Text(activeCal?.displayName ?: "Anderen Kalender auswählen")
                        },
                        leadingContent = {
                            CalendarSettingsIcon(
                                Icons.Default.DateRange,
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        },
                        trailingContent = { CalendarChevronTrailing() },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            availableCalendars = viewModel.getAvailableCalendars()
                            showCalendarPickerDialog = true
                        }
                    )
                    CalendarSettingsGroupDivider()
                    ListItem(
                        headlineContent = { Text("Synchronisierte Typen") },
                        supportingContent = { Text(formatSyncTypesSummary(settings.calendarSyncTypes)) },
                        leadingContent = {
                            CalendarSettingsIcon(
                                Icons.Default.Sync,
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        },
                        trailingContent = { CalendarChevronTrailing() },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showCalendarTypesDialog = true
                        }
                    )
                    CalendarSettingsGroupDivider()
                    var prefixText by remember(settings.calendarEventPrefix) { mutableStateOf(settings.calendarEventPrefix) }

                    DisposableEffect(prefixText) {
                        onDispose {
                            if (prefixText != settings.calendarEventPrefix) {
                                viewModel.saveCalendarSettings(
                                    enabled = settings.calendarSyncEnabled,
                                    calendarId = settings.calendarId,
                                    syncTypes = settings.calendarSyncTypes,
                                    syncOffice = settings.calendarSyncOffice,
                                    syncHomeOffice = settings.calendarSyncHomeOffice,
                                    eventPrefix = prefixText,
                                    noAlarm = settings.calendarEventNoAlarm
                                )
                            }
                        }
                    }

                    ListItem(
                        headlineContent = { Text("Präfix für Kalendertitel") },
                        supportingContent = {
                            OutlinedTextField(
                                value = prefixText,
                                onValueChange = { prefixText = it },
                                placeholder = { Text("Kein Präfix (z.B. FleX)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                        if (prefixText != settings.calendarEventPrefix) {
                                            viewModel.saveCalendarSettings(
                                                enabled = settings.calendarSyncEnabled,
                                                calendarId = settings.calendarId,
                                                syncTypes = settings.calendarSyncTypes,
                                                syncOffice = settings.calendarSyncOffice,
                                                syncHomeOffice = settings.calendarSyncHomeOffice,
                                                eventPrefix = prefixText,
                                                noAlarm = settings.calendarEventNoAlarm
                                            )
                                        }
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .onFocusChanged { focusState ->
                                        if (!focusState.isFocused && prefixText != settings.calendarEventPrefix) {
                                            viewModel.saveCalendarSettings(
                                                enabled = settings.calendarSyncEnabled,
                                                calendarId = settings.calendarId,
                                                syncTypes = settings.calendarSyncTypes,
                                                syncOffice = settings.calendarSyncOffice,
                                                syncHomeOffice = settings.calendarSyncHomeOffice,
                                                eventPrefix = prefixText,
                                                noAlarm = settings.calendarEventNoAlarm
                                            )
                                        }
                                    }
                            )
                        },
                        leadingContent = {
                            CalendarSettingsIcon(
                                Icons.Default.Title,
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    )
                    CalendarSettingsGroupDivider()
                    ListItem(
                        headlineContent = { Text("Keine Erinnerungen") },
                        supportingContent = {
                            Text("Entfernt automatische Alarme aus synchronisierten Terminen")
                        },
                        leadingContent = {
                            CalendarSettingsIcon(
                                Icons.Default.NotificationsOff,
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = settings.calendarEventNoAlarm,
                                onCheckedChange = { noAlarm ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.saveCalendarSettings(
                                        enabled = settings.calendarSyncEnabled,
                                        calendarId = settings.calendarId,
                                        syncTypes = settings.calendarSyncTypes,
                                        syncOffice = settings.calendarSyncOffice,
                                        syncHomeOffice = settings.calendarSyncHomeOffice,
                                        eventPrefix = settings.calendarEventPrefix,
                                        noAlarm = noAlarm
                                    )
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    )
                }
            }

            // ── 2. Manuelle Synchronisation & Wartung ─────────────────────
            if (settings.calendarSyncEnabled) {
                CalendarSettingsSectionHeader("Wartung & Aktionen")
                CalendarSettingsGroup {
                    ListItem(
                        headlineContent = { Text("Alle synchronisieren") },
                        supportingContent = { Text("Vorhandene Einträge jetzt einmalig übertragen") },
                        leadingContent = {
                            CalendarSettingsIcon(
                                Icons.Default.Sync,
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        },
                        trailingContent = { CalendarChevronTrailing() },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showSyncRangeDialog = true
                        }
                    )
                    CalendarSettingsGroupDivider()
                    ListItem(
                        headlineContent = { Text("Verwaiste Mappings bereinigen") },
                        supportingContent = { Text("Verknüpfungen zu gelöschten Einträgen entfernen") },
                        leadingContent = {
                            CalendarSettingsIcon(
                                Icons.Default.DeleteSweep,
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.onErrorContainer
                            )
                        },
                        trailingContent = { CalendarChevronTrailing() },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            viewModel.cleanupOrphanedMappings { removed, total ->
                                scope.launch {
                                    val msg = if (total == 0) {
                                        "Keine Verknüpfungen vorhanden"
                                    } else {
                                        "$removed von $total Verknüpfungen bereinigt"
                                    }
                                    snackbarHostState.showSnackbar(msg)
                                }
                            }
                        }
                    )
                }
            }

            // ── 3. Export & Teilen ─────────────────────────────────────────
            CalendarSettingsSectionHeader("Export & Teilen")
            CalendarSettingsGroup {
                ListItem(
                    headlineContent = { Text("Als .ics exportieren") },
                    supportingContent = { Text("Kalendereinträge als universelle iCalendar-Datei teilen") },
                    leadingContent = {
                        CalendarSettingsIcon(
                            Icons.Default.IosShare,
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    },
                    trailingContent = { CalendarChevronTrailing() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showIcsRangeDialog = true
                    }
                )
            }

            // ── 4. Feiertage & Region ─────────────────────────────────────
            CalendarSettingsSectionHeader("Feiertage")
            CalendarSettingsGroup {
                ListItem(
                    headlineContent = { Text("Bundesland für gesetzliche Feiertage") },
                    supportingContent = {
                        Text("${settings.federalState.displayName} · Feiertage werden automatisch berücksichtigt")
                    },
                    leadingContent = {
                        CalendarSettingsIcon(
                            Icons.Default.EventAvailable,
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    },
                    trailingContent = { CalendarChevronTrailing() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showFederalStateDialog = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ── Dialoge ────────────────────────────────────────────────────────────

    if (showCalendarPickerDialog) {
        AlertDialog(
            shape = RoundedCornerShape(24.dp),
            onDismissRequest = { showCalendarPickerDialog = false },
            title = { Text("Kalender auswählen") },
            text = {
                if (availableCalendars.isEmpty()) {
                    Text(
                        "Keine Kalender gefunden. Bitte stelle sicher, dass auf diesem Gerät ein Kalenderkonto eingerichtet ist.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn {
                        items(availableCalendars) { cal: CalendarInfo ->
                            val isSelected = cal.id == settings.calendarId
                            ListItem(
                                headlineContent = {
                                    Text(
                                        cal.displayName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                supportingContent = { Text(cal.accountName) },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(
                                                color = Color(cal.color),
                                                shape = CircleShape
                                            )
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                                CircleShape
                                            )
                                    )
                                },
                                trailingContent = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Ausgewählt",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent
                                ),
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                        viewModel.saveCalendarSettings(
                                            enabled = true,
                                            calendarId = cal.id,
                                            syncTypes = settings.calendarSyncTypes,
                                            syncOffice = settings.calendarSyncOffice,
                                            syncHomeOffice = settings.calendarSyncHomeOffice,
                                            eventPrefix = settings.calendarEventPrefix,
                                            noAlarm = settings.calendarEventNoAlarm
                                        )
                                        showCalendarPickerDialog = false
                                    }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCalendarPickerDialog = false }) { Text("Schließen") }
            }
        )
    }

    if (showFederalStateDialog) {
        AlertDialog(
            shape = RoundedCornerShape(24.dp),
            onDismissRequest = { showFederalStateDialog = false },
            title = { Text("Bundesland wählen") },
            text = {
                LazyColumn {
                    items(FederalState.entries) { state ->
                        val isSelected = settings.federalState == state
                        ListItem(
                            headlineContent = {
                                Text(
                                    state.displayName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            trailingContent = {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent
                            ),
                            modifier = Modifier
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    viewModel.updateFederalState(state)
                                    showFederalStateDialog = false
                                }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFederalStateDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    if (showSyncRangeDialog) {
        val today = LocalDate.now()
        val ranges = listOf(
            "Aktueller Monat" to (today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())),
            "Aktuelles Jahr" to (today.withDayOfYear(1) to today.withDayOfYear(today.lengthOfYear())),
            "Gesamter Zeitraum" to (LocalDate.of(today.year - 5, 1, 1) to LocalDate.of(today.year + 1, 12, 31))
        )
        var selectedRangeIndex by remember { mutableStateOf(1) }
        AlertDialog(
            shape = RoundedCornerShape(24.dp),
            onDismissRequest = { showSyncRangeDialog = false },
            title = { Text("Kalender-Synchronisation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Synchronisations-Zeitraum",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    ranges.forEachIndexed { i, (label, _) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedRangeIndex = i
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedRangeIndex == i,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    showSyncRangeDialog = false
                    val (start, end) = ranges[selectedRangeIndex].second
                    viewModel.syncAllToCalendar(start, end) { synced, total ->
                        scope.launch {
                            snackbarHostState.showSnackbar("$synced von $total Einträgen synchronisiert")
                        }
                    }
                }) { Text("Jetzt synchronisieren") }
            },
            dismissButton = {
                TextButton(onClick = { showSyncRangeDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    if (showIcsRangeDialog) {
        val today = LocalDate.now()
        val ranges = listOf(
            "Aktueller Monat" to (today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())),
            "Aktuelles Jahr" to (today.withDayOfYear(1) to today.withDayOfYear(today.lengthOfYear())),
            "Gesamter Zeitraum" to (LocalDate.of(today.year - 5, 1, 1) to LocalDate.of(today.year + 1, 12, 31))
        )
        val allTypes = listOf(
            "WORK" to "Arbeit",
            "VACATION" to "Urlaub",
            "SICK_DAY" to "Krank",
            "FLEX_DAY" to "Flextag",
            "SPECIAL_VACATION" to "Sonderurlaub",
            "OVERTIME_DAY" to "Überstunden-Tag",
            "SATURDAY_BONUS" to "Samstag-Bonus",
            "BUSINESS_TRIP" to "Dienstgang / Dienstreise"
        )
        var selectedRangeIndex by remember { mutableStateOf(1) }
        var icsExportTypes by remember(settings.calendarSyncTypes) {
            mutableStateOf(settings.calendarSyncTypes.split(",").map { it.trim() }.toSet())
        }
        var icsExportOffice by remember { mutableStateOf(settings.calendarSyncOffice) }
        var icsExportHomeOffice by remember { mutableStateOf(settings.calendarSyncHomeOffice) }

        AlertDialog(
            shape = RoundedCornerShape(24.dp),
            onDismissRequest = { showIcsRangeDialog = false },
            title = { Text("ICS-Kalender exportieren") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item {
                        Text(
                            "Zeitraum",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(ranges.size) { i ->
                        val (label, _) = ranges[i]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedRangeIndex = i
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedRangeIndex == i,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            "Eintragstypen",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(allTypes.size) { i ->
                        val (key, label) = allTypes[i]
                        val checked = key in icsExportTypes
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    icsExportTypes = if (checked) icsExportTypes - key else icsExportTypes + key
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if ("WORK" in icsExportTypes) {
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                "Arbeitsort (bei Arbeitstagen)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        icsExportOffice = !icsExportOffice
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = icsExportOffice,
                                    onCheckedChange = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Büro", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        icsExportHomeOffice = !icsExportHomeOffice
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = icsExportHomeOffice,
                                    onCheckedChange = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Homeoffice", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    showIcsRangeDialog = false
                    val (start, end) = ranges[selectedRangeIndex].second
                    viewModel.exportToIcs(
                        start, end,
                        icsExportTypes.joinToString(","),
                        icsExportOffice,
                        icsExportHomeOffice
                    ) { icsContent, count ->
                        pendingIcsCount = count
                        pendingIcsContent = icsContent
                    }
                }) { Text("Exportieren") }
            },
            dismissButton = {
                TextButton(onClick = { showIcsRangeDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    if (showCalendarTypesDialog) {
        val allTypes = listOf(
            "WORK" to "Arbeit",
            "VACATION" to "Urlaub",
            "SICK_DAY" to "Krank",
            "FLEX_DAY" to "Flextag",
            "SPECIAL_VACATION" to "Sonderurlaub",
            "OVERTIME_DAY" to "Überstunden-Tag",
            "SATURDAY_BONUS" to "Samstag-Bonus",
            "BUSINESS_TRIP" to "Dienstgang / Dienstreise"
        )
        val enabledTypes = remember(settings.calendarSyncTypes) {
            settings.calendarSyncTypes.split(",").map { it.trim() }.toMutableSet()
        }
        var syncOffice by remember { mutableStateOf(settings.calendarSyncOffice) }
        var syncHomeOffice by remember { mutableStateOf(settings.calendarSyncHomeOffice) }
        var currentTypes by remember { mutableStateOf(enabledTypes.toSet()) }

        AlertDialog(
            shape = RoundedCornerShape(24.dp),
            onDismissRequest = { showCalendarTypesDialog = false },
            title = { Text("Synchronisierte Typen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    allTypes.forEach { (key, label) ->
                        val checked = key in currentTypes
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentTypes = if (checked) currentTypes - key else currentTypes + key
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if ("WORK" in currentTypes) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            "Arbeitsort (bei Arbeit)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    syncOffice = !syncOffice
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = syncOffice,
                                onCheckedChange = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Büro", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    syncHomeOffice = !syncHomeOffice
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = syncHomeOffice,
                                onCheckedChange = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Homeoffice", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    viewModel.saveCalendarSettings(
                        enabled = settings.calendarSyncEnabled,
                        calendarId = settings.calendarId,
                        syncTypes = currentTypes.joinToString(","),
                        syncOffice = syncOffice,
                        syncHomeOffice = syncHomeOffice,
                        eventPrefix = settings.calendarEventPrefix,
                        noAlarm = settings.calendarEventNoAlarm
                    )
                    showCalendarTypesDialog = false
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { showCalendarTypesDialog = false }) { Text("Abbrechen") }
            }
        )
    }
}

private fun formatSyncTypesSummary(syncTypesString: String): String {
    val labels = mapOf(
        "WORK" to "Arbeit",
        "VACATION" to "Urlaub",
        "SICK_DAY" to "Krank",
        "FLEX_DAY" to "Flextag",
        "SPECIAL_VACATION" to "Sonderurlaub",
        "OVERTIME_DAY" to "Überstunden",
        "SATURDAY_BONUS" to "Samstag",
        "BUSINESS_TRIP" to "Dienstreise"
    )
    val list = syncTypesString.split(",").mapNotNull { labels[it.trim()] }
    return if (list.isEmpty()) "Keine Typen ausgewählt" else list.joinToString(", ")
}

// ── Private Helper Composables ─────────────────────────────────────────────

@Composable
private fun CalendarSettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun CalendarSettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun CalendarSettingsGroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun CalendarSettingsIcon(icon: ImageVector, containerColor: Color, contentColor: Color) {
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
private fun CalendarChevronTrailing() {
    Icon(
        Icons.Default.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
