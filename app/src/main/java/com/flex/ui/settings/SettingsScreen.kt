package com.flex.ui.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flex.R
import com.flex.domain.model.AppIconVariant
import com.flex.domain.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToBackup: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToGeofence: () -> Unit = {},
    onNavigateToWifi: () -> Unit = {},
    onNavigateToQuotaRules: () -> Unit = {},
    onShowOnboarding: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val quotaRules by viewModel.quotaRules.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val appIconVariant by viewModel.appIconVariant.collectAsState()

    // Dialog visibility state
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAppIconDialog by remember { mutableStateOf(false) }
    var showDailyWorkTimeDialog by remember { mutableStateOf(false) }
    var showMonthlyWorkTimeDialog by remember { mutableStateOf(false) }
    var showQuotaDialog by remember { mutableStateOf(false) }
    var showFlextimeDialog by remember { mutableStateOf(false) }
    var showOvertimeDialog by remember { mutableStateOf(false) }
    var showVacationDialog by remember { mutableStateOf(false) }

    // Format helpers
    fun Int.toHoursMinutes(): String {
        val h = this / 60; val m = this % 60
        return if (m == 0) "${h}h" else "${h}h ${m}min"
    }
    fun Int.toSignedHoursMinutes(): String {
        val abs = kotlin.math.abs(this)
        val h = abs / 60; val m = abs % 60
        val sign = if (this < 0) "-" else "+"
        return "$sign${h}h ${m}min"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {

        // ── Darstellung ──────────────────────────────────────────────
        item { SettingsSectionHeader("Darstellung") }
        item {
            SettingsGroup {
                ListItem(
                    headlineContent = { Text("Design") },
                    supportingContent = {
                        Text(when (themeMode) { ThemeMode.SYSTEM -> "System"; ThemeMode.LIGHT -> "Hell"; ThemeMode.DARK -> "Dunkel" })
                    },
                    leadingContent = { SettingsIcon(Icons.Default.Palette, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) },
                    trailingContent = { ChevronTrailing() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { showThemeDialog = true }
                )
                SettingsGroupDivider()
                ListItem(
                    headlineContent = { Text("App-Symbol & Name") },
                    supportingContent = { Text(appIconVariant.displayName) },
                    leadingContent = { SettingsIcon(Icons.Default.PhoneAndroid, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) },
                    trailingContent = { ChevronTrailing() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { showAppIconDialog = true }
                )
            }
        }

        // ── Arbeitszeit ───────────────────────────────────────────────
        item { SettingsSectionHeader("Arbeitszeit") }
        item {
            SettingsGroup {
                ListItem(
                    headlineContent = { Text("Tagesarbeitszeit") },
                    supportingContent = { Text(settings.dailyWorkMinutes.toHoursMinutes()) },
                    leadingContent = { SettingsIcon(Icons.Default.Schedule, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) },
                    trailingContent = { ChevronTrailing() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { showDailyWorkTimeDialog = true }
                )
                SettingsGroupDivider()
                ListItem(
                    headlineContent = { Text("Monatssoll") },
                    supportingContent = { Text(settings.monthlyWorkMinutes.toHoursMinutes()) },
                    leadingContent = { SettingsIcon(Icons.Default.CalendarMonth, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) },
                    trailingContent = { ChevronTrailing() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { showMonthlyWorkTimeDialog = true }
                )
            }
        }

        // ── Büro-Quote ────────────────────────────────────────────────
        item { SettingsSectionHeader("Büro-Quote") }
        item {
            SettingsGroup {
                ListItem(
                    headlineContent = { Text("Standard-Quote") },
                    supportingContent = { Text("${settings.officeQuotaPercent}% · ${settings.officeQuotaMinDays} Tage min.") },
                    leadingContent = { SettingsIcon(Icons.Default.Business, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) },
                    trailingContent = { ChevronTrailing() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { showQuotaDialog = true }
                )
                SettingsGroupDivider()
                ListItem(
                    headlineContent = { Text("Quoten-Zeiträume") },
                    supportingContent = {
                        Text(if (quotaRules.isEmpty()) "Keine Regeln – Standard gilt" else "${quotaRules.size} ${if (quotaRules.size == 1) "Regel" else "Regeln"} aktiv")
                    },
                    leadingContent = { SettingsIcon(Icons.Default.DateRange, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) },
                    trailingContent = { ChevronTrailing() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { onNavigateToQuotaRules() }
                )
            }
        }

        // ── Salden ────────────────────────────────────────────────────
        item { SettingsSectionHeader("Salden") }
        item {
            SettingsGroup {
                ListItem(
                    headlineContent = { Text("Gleitzeit-Anfangssaldo") },
                    supportingContent = { Text(settings.initialFlextimeMinutes.toSignedHoursMinutes()) },
                    leadingContent = { SettingsIcon(Icons.Default.SwapHoriz, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) },
                    trailingContent = { ChevronTrailing() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { showFlextimeDialog = true }
                )
                SettingsGroupDivider()
                ListItem(
                    headlineContent = { Text("Überstunden-Anfangssaldo") },
                    supportingContent = { Text(settings.initialOvertimeMinutes.toSignedHoursMinutes()) },
                    leadingContent = { SettingsIcon(Icons.Default.Timer, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) },
                    trailingContent = { ChevronTrailing() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { showOvertimeDialog = true }
                )
            }
        }

        // ── Urlaub ────────────────────────────────────────────────────
        item { SettingsSectionHeader("Urlaub") }
        item {
            SettingsGroup {
                ListItem(
                    headlineContent = { Text("Urlaubstage & Überträge") },
                    supportingContent = {
                        Text("${settings.annualVacationDays} Jahrestage · ${settings.carryOverVacationDays} Übertrag · ${settings.specialVacationDays} Sonder")
                    },
                    leadingContent = { SettingsIcon(Icons.Default.BeachAccess, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) },
                    trailingContent = { ChevronTrailing() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { showVacationDialog = true }
                )
            }
        }

        // ── Automatisch stempeln ──────────────────────────────────────
        item { SettingsSectionHeader("Automatisch stempeln") }
        item {
            SettingsGroup {
                ListItem(
                    headlineContent = { Text("Geofencing") },
                    supportingContent = { Text(if (settings.geofenceEnabled) "Aktiviert · ${settings.geofenceAddress.ifBlank { "Kein Standort" }}" else "Deaktiviert") },
                    leadingContent = { SettingsIcon(Icons.Default.LocationOn, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) },
                    trailingContent = { ChevronTrailing() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { onNavigateToGeofence() }
                )
                SettingsGroupDivider()
                ListItem(
                    headlineContent = { Text("WLAN Auto-Stempeln") },
                    supportingContent = { Text(if (settings.wifiAutoStampEnabled) "Aktiviert · ${settings.wifiSsid.ifBlank { "Kein WLAN" }}" else "Deaktiviert") },
                    leadingContent = { SettingsIcon(Icons.Default.Wifi, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) },
                    trailingContent = { ChevronTrailing() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { onNavigateToWifi() }
                )
            }
        }

        // ── Benachrichtigungen ────────────────────────────────────────
        item { SettingsSectionHeader("Benachrichtigungen") }
        item {
            SettingsGroup {
                ListItem(
                    headlineContent = { Text("Pausen-Warnung") },
                    supportingContent = { Text("Warnung bei Verstößen gegen §4 ArbZG") },
                    leadingContent = { SettingsIcon(Icons.Default.Notifications, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) },
                    trailingContent = {
                        Switch(
                            checked = settings.breakWarningEnabled,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(breakWarningEnabled = it)) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                )
            }
        }

        // ── Datenverwaltung ───────────────────────────────────────────
        item { SettingsSectionHeader("Datenverwaltung") }
        item {
            SettingsGroup {
                ListItem(
                    headlineContent = { Text("Backup & Wiederherstellung") },
                    supportingContent = { Text("Daten exportieren & importieren") },
                    leadingContent = { SettingsIcon(Icons.Default.Backup, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) },
                    trailingContent = { ChevronTrailing() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { onNavigateToBackup() }
                )
            }
        }

        // ── App ───────────────────────────────────────────────────────
        item { SettingsSectionHeader("App") }
        item {
            SettingsGroup {
                ListItem(
                    headlineContent = { Text("Einführung wiederholen") },
                    supportingContent = { Text("Onboarding erneut anzeigen") },
                    leadingContent = { SettingsIcon(Icons.Default.Update, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = { ChevronTrailing() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { onShowOnboarding() }
                )
                SettingsGroupDivider()
                ListItem(
                    headlineContent = { Text("Über FleX") },
                    supportingContent = { Text("Version & Kontakt") },
                    leadingContent = { SettingsIcon(Icons.Default.Info, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = { ChevronTrailing() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { onNavigateToAbout() }
                )
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Design") },
            text = {
                val options = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Hell", ThemeMode.DARK to "Dunkel")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            onClick = { viewModel.setThemeMode(mode) },
                            selected = themeMode == mode,
                            label = { Text(label) }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Schließen") } }
        )
    }

    if (showAppIconDialog) {
        AlertDialog(
            onDismissRequest = { showAppIconDialog = false },
            title = { Text("App-Symbol & Name") },
            text = {
                Column {
                    Text("Änderung wird nach kurzer Zeit im Launcher sichtbar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AppIconVariant.entries.forEach { variant ->
                            AppIconVariantItem(variant = variant, isSelected = appIconVariant == variant, onClick = { viewModel.setAppIconVariant(variant) })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAppIconDialog = false }) { Text("Schließen") } }
        )
    }

    if (showDailyWorkTimeDialog) {
        WorkTimeDialog(
            title = "Tagesarbeitszeit",
            initialHours = settings.dailyWorkMinutes / 60,
            initialMinutes = settings.dailyWorkMinutes % 60,
            onDismiss = { showDailyWorkTimeDialog = false },
            onSave = { h, m -> viewModel.updateSettings(settings.copy(dailyWorkMinutes = h * 60 + m)); showDailyWorkTimeDialog = false }
        )
    }

    if (showMonthlyWorkTimeDialog) {
        WorkTimeDialog(
            title = "Monatssoll",
            initialHours = settings.monthlyWorkMinutes / 60,
            initialMinutes = settings.monthlyWorkMinutes % 60,
            onDismiss = { showMonthlyWorkTimeDialog = false },
            onSave = { h, m -> viewModel.updateSettings(settings.copy(monthlyWorkMinutes = h * 60 + m)); showMonthlyWorkTimeDialog = false }
        )
    }

    if (showQuotaDialog) {
        QuotaDialog(
            initialPercent = settings.officeQuotaPercent,
            initialMinDays = settings.officeQuotaMinDays,
            onDismiss = { showQuotaDialog = false },
            onSave = { p, d -> viewModel.updateSettings(settings.copy(officeQuotaPercent = p, officeQuotaMinDays = d)); showQuotaDialog = false }
        )
    }

    if (showFlextimeDialog) {
        val abs = kotlin.math.abs(settings.initialFlextimeMinutes)
        BalanceDialog(
            title = "Gleitzeit-Anfangssaldo",
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
            onSave = { a, c, s -> viewModel.updateSettings(settings.copy(annualVacationDays = a, carryOverVacationDays = c, specialVacationDays = s)); showVacationDialog = false }
        )
    }
}

// ── Helper Composables ──────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
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
private fun SettingsGroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
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
    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ── Dialog Composables ──────────────────────────────────────────────────

@Composable
private fun WorkTimeDialog(title: String, initialHours: Int, initialMinutes: Int, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    var hours by remember { mutableStateOf(initialHours.toString()) }
    var minutes by remember { mutableStateOf(initialMinutes.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = hours, onValueChange = { hours = it }, label = { Text("Std.") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(value = minutes, onValueChange = { minutes = it }, label = { Text("Min.") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(hours.toIntOrNull() ?: initialHours, minutes.toIntOrNull() ?: initialMinutes) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun BalanceDialog(title: String, initialSign: String, initialHours: Int, initialMinutes: Int, onDismiss: () -> Unit, onSave: (String, Int, Int) -> Unit) {
    var sign by remember { mutableStateOf(initialSign) }
    var hours by remember { mutableStateOf(initialHours.toString()) }
    var minutes by remember { mutableStateOf(initialMinutes.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = sign == "+", onClick = { sign = if (sign == "+") "-" else "+" }, label = { Text(sign, fontWeight = FontWeight.Bold) })
                    OutlinedTextField(value = hours, onValueChange = { hours = it }, label = { Text("Std.") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(value = minutes, onValueChange = { minutes = it }, label = { Text("Min.") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
                Text("Vorzeichen antippen zum Wechseln (+/−)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(sign, hours.toIntOrNull() ?: initialHours, minutes.toIntOrNull() ?: initialMinutes) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun QuotaDialog(initialPercent: Int, initialMinDays: Int, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    var percent by remember { mutableStateOf(initialPercent.toString()) }
    var minDays by remember { mutableStateOf(initialMinDays.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Büro-Quote (Standard)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Gilt, wenn keine Zeitregel aktiv ist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = percent, onValueChange = { percent = it }, label = { Text("Prozent (%)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(value = minDays, onValueChange = { minDays = it }, label = { Text("Min. Tage") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(percent.toIntOrNull() ?: initialPercent, minDays.toIntOrNull() ?: initialMinDays) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun VacationDialog(annual: Int, carryOver: Int, special: Int, onDismiss: () -> Unit, onSave: (Int, Int, Int) -> Unit) {
    var annualText by remember { mutableStateOf(annual.toString()) }
    var carryOverText by remember { mutableStateOf(carryOver.toString()) }
    var specialText by remember { mutableStateOf(special.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Urlaub") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = annualText, onValueChange = { annualText = it }, label = { Text("Jahresurlaub (Tage)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(value = carryOverText, onValueChange = { carryOverText = it }, label = { Text("Resturlaub Vorjahr") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(value = specialText, onValueChange = { specialText = it }, label = { Text("Sonderurlaub (verfällt Ende Okt.)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(annualText.toIntOrNull() ?: annual, carryOverText.toIntOrNull() ?: carryOver, specialText.toIntOrNull() ?: special) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
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
            modifier = Modifier.size(56.dp).clip(shape).background(variant.bgColor()).then(
                if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier
            )
        ) {
            Image(painter = painterResource(id = variant.foregroundRes()), contentDescription = variant.displayName, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(4.dp))
        Text(variant.appLabel, style = MaterialTheme.typography.labelSmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}
