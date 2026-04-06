package com.flex.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flex.calendar.CalendarInfo
import kotlinx.coroutines.launch

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

    var showCalendarPickerDialog by remember { mutableStateOf(false) }
    var showCalendarTypesDialog by remember { mutableStateOf(false) }
    var showSyncRangeDialog by remember { mutableStateOf(false) }
    var showIcsRangeDialog by remember { mutableStateOf(false) }
    var pendingEnableCalendarSync by remember { mutableStateOf(false) }
    var availableCalendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var pendingIcsContent by remember { mutableStateOf<String?>(null) }
    var pendingIcsCount by remember { mutableStateOf(0) }

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
            showCalendarPickerDialog = true
        }
        pendingEnableCalendarSync = false
    }

    fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    fun requestCalendarPermissionOrPick() {
        pendingEnableCalendarSync = true
        if (hasCalendarPermission()) {
            availableCalendars = viewModel.getAvailableCalendars()
            showCalendarPickerDialog = true
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
                title = { Text("Kalender") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
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
            CalendarSettingsSectionHeader("Gerätekalender")
            CalendarSettingsGroup {
                // Sync Toggle
                ListItem(
                    headlineContent = { Text("Mit Gerätekalender synchronisieren") },
                    supportingContent = {
                        if (settings.calendarSyncEnabled && settings.calendarId != -1L) {
                            val cal = availableCalendars.find { it.id == settings.calendarId }
                            Text(cal?.let { "${it.displayName} (${it.accountName})" } ?: "Kalender ausgewählt")
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
                                if (enabled) {
                                    requestCalendarPermissionOrPick()
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
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                )

                if (settings.calendarSyncEnabled) {
                    CalendarSettingsGroupDivider()
                    ListItem(
                        headlineContent = { Text("Kalender wechseln") },
                        supportingContent = { Text("Anderen Kalender auswählen") },
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
                            availableCalendars = viewModel.getAvailableCalendars()
                            showCalendarPickerDialog = true
                        }
                    )
                    CalendarSettingsGroupDivider()
                    ListItem(
                        headlineContent = { Text("Synchronisierte Typen") },
                        supportingContent = { Text("Auswählen welche Eintragstypen synchronisiert werden") },
                        leadingContent = {
                            CalendarSettingsIcon(
                                Icons.Default.Sync,
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        },
                        trailingContent = { CalendarChevronTrailing() },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clickable { showCalendarTypesDialog = true }
                    )
                    CalendarSettingsGroupDivider()
                    var prefixText by remember(settings.calendarEventPrefix) { mutableStateOf(settings.calendarEventPrefix) }
                    ListItem(
                        headlineContent = { Text("Präfix für Kalendertitel") },
                        supportingContent = {
                            OutlinedTextField(
                                value = prefixText,
                                onValueChange = { prefixText = it },
                                placeholder = { Text("Kein Präfix") },
                                singleLine = true,
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
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                    CalendarSettingsGroupDivider()
                    ListItem(
                        headlineContent = { Text("Keine Erinnerung") },
                        supportingContent = { Text("Verhindert automatische Erinnerungen. Funktioniert nicht bei allen Kalender-Apps.") },
                        trailingContent = {
                            Switch(
                                checked = settings.calendarEventNoAlarm,
                                onCheckedChange = { noAlarm ->
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
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                    CalendarSettingsGroupDivider()
                    ListItem(
                        headlineContent = { Text("Alle synchronisieren") },
                        supportingContent = { Text("Vorhandene Einträge jetzt einmalig synchronisieren") },
                        leadingContent = {
                            CalendarSettingsIcon(
                                Icons.Default.Sync,
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        },
                        trailingContent = { CalendarChevronTrailing() },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clickable { showSyncRangeDialog = true }
                    )
                    CalendarSettingsGroupDivider()
                    ListItem(
                        headlineContent = { Text("Verwaiste Mappings bereinigen") },
                        supportingContent = { Text("Verknüpfungen zu gelöschten Einträgen entfernen") },
                        leadingContent = {
                            CalendarSettingsIcon(
                                Icons.Default.Delete,
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.onErrorContainer
                            )
                        },
                        trailingContent = { CalendarChevronTrailing() },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clickable {
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

                CalendarSettingsGroupDivider()
                ListItem(
                    headlineContent = { Text("Als .ics exportieren") },
                    supportingContent = { Text("Kalendereinträge als iCalendar-Datei teilen") },
                    leadingContent = {
                        CalendarSettingsIcon(
                            Icons.Default.IosShare,
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    },
                    trailingContent = { CalendarChevronTrailing() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { showIcsRangeDialog = true }
                )
            }
        }
    }

    // ── Dialoge ────────────────────────────────────────────────────────────

    if (showCalendarPickerDialog) {
        AlertDialog(
            onDismissRequest = { showCalendarPickerDialog = false },
            title = { Text("Kalender auswählen") },
            text = {
                if (availableCalendars.isEmpty()) {
                    Text("Keine Kalender gefunden. Bitte zuerst einen Kalender auf dem Gerät einrichten.")
                } else {
                    androidx.compose.foundation.lazy.LazyColumn {
                        items(availableCalendars) { cal: CalendarInfo ->
                            ListItem(
                                headlineContent = { Text(cal.displayName) },
                                supportingContent = { Text(cal.accountName) },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(
                                                color = androidx.compose.ui.graphics.Color(cal.color),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                    )
                                },
                                modifier = Modifier.clickable {
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
                TextButton(onClick = { showCalendarPickerDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    if (showSyncRangeDialog) {
        val today = java.time.LocalDate.now()
        val ranges = listOf(
            "Aktueller Monat" to (today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())),
            "Aktuelles Jahr" to (today.withDayOfYear(1) to today.withDayOfYear(today.lengthOfYear())),
            "Alles" to (java.time.LocalDate.of(today.year - 5, 1, 1) to java.time.LocalDate.of(today.year + 1, 12, 31))
        )
        var selectedRangeIndex by remember { mutableStateOf(1) }
        AlertDialog(
            onDismissRequest = { showSyncRangeDialog = false },
            title = { Text("Synchronisieren") },
            text = {
                Column {
                    Text(
                        "Zeitraum",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    ranges.forEachIndexed { i, (label, _) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedRangeIndex = i }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selectedRangeIndex == i,
                                onClick = { selectedRangeIndex = i }
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSyncRangeDialog = false
                    val (start, end) = ranges[selectedRangeIndex].second
                    viewModel.syncAllToCalendar(start, end) { synced, total ->
                        scope.launch {
                            snackbarHostState.showSnackbar("$synced von $total Einträgen synchronisiert")
                        }
                    }
                }) { Text("Synchronisieren") }
            },
            dismissButton = {
                TextButton(onClick = { showSyncRangeDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    if (showIcsRangeDialog) {
        val today = java.time.LocalDate.now()
        val ranges = listOf(
            "Aktueller Monat" to (today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())),
            "Aktuelles Jahr" to (today.withDayOfYear(1) to today.withDayOfYear(today.lengthOfYear())),
            "Alles" to (java.time.LocalDate.of(today.year - 5, 1, 1) to java.time.LocalDate.of(today.year + 1, 12, 31))
        )
        val allTypes = listOf(
            "WORK" to "Arbeit",
            "VACATION" to "Urlaub",
            "SICK_DAY" to "Krank",
            "FLEX_DAY" to "Flextag",
            "SPECIAL_VACATION" to "Sonderurlaub",
            "OVERTIME_DAY" to "Überstunden-Tag",
            "SATURDAY_BONUS" to "Samstag-Bonus"
        )
        var selectedRangeIndex by remember { mutableStateOf(1) }
        var icsExportTypes by remember(settings.calendarSyncTypes) {
            mutableStateOf(settings.calendarSyncTypes.split(",").map { it.trim() }.toSet())
        }
        var icsExportOffice by remember { mutableStateOf(settings.calendarSyncOffice) }
        var icsExportHomeOffice by remember { mutableStateOf(settings.calendarSyncHomeOffice) }

        AlertDialog(
            onDismissRequest = { showIcsRangeDialog = false },
            title = { Text("ICS exportieren") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
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
                                .clickable { selectedRangeIndex = i }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selectedRangeIndex == i,
                                onClick = { selectedRangeIndex = i }
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
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
                                .clickable {
                                    icsExportTypes = if (checked) icsExportTypes - key else icsExportTypes + key
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    icsExportTypes = if (it) icsExportTypes + key else icsExportTypes - key
                                }
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if ("WORK" in icsExportTypes) {
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                "Arbeitsort",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { icsExportOffice = !icsExportOffice }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Checkbox(
                                    checked = icsExportOffice,
                                    onCheckedChange = { icsExportOffice = it }
                                )
                                Text("Büro", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { icsExportHomeOffice = !icsExportHomeOffice }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Checkbox(
                                    checked = icsExportHomeOffice,
                                    onCheckedChange = { icsExportHomeOffice = it }
                                )
                                Text("Homeoffice", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
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
            "SATURDAY_BONUS" to "Samstag-Bonus"
        )
        val enabledTypes = remember(settings.calendarSyncTypes) {
            settings.calendarSyncTypes.split(",").map { it.trim() }.toMutableSet()
        }
        var syncOffice by remember { mutableStateOf(settings.calendarSyncOffice) }
        var syncHomeOffice by remember { mutableStateOf(settings.calendarSyncHomeOffice) }
        var currentTypes by remember { mutableStateOf(enabledTypes.toSet()) }

        AlertDialog(
            onDismissRequest = { showCalendarTypesDialog = false },
            title = { Text("Synchronisierte Typen") },
            text = {
                Column {
                    allTypes.forEach { (key, label) ->
                        val checked = key in currentTypes
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentTypes = if (checked) currentTypes - key else currentTypes + key
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    currentTypes = if (it) currentTypes + key else currentTypes - key
                                }
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if ("WORK" in currentTypes) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            "Arbeitsort",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { syncOffice = !syncOffice }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = syncOffice,
                                onCheckedChange = { syncOffice = it }
                            )
                            Text("Büro", modifier = Modifier.padding(start = 8.dp))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { syncHomeOffice = !syncHomeOffice }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = syncHomeOffice,
                                onCheckedChange = { syncHomeOffice = it }
                            )
                            Text("Homeoffice", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
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

// ── Private Helper Composables ─────────────────────────────────────────────

@Composable
private fun CalendarSettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)
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
