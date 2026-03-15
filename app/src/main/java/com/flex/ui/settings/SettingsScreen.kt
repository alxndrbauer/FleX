package com.flex.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Switch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.flex.domain.model.ThemeMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flex.domain.model.QuotaRule
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToBackup: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val quotaRules by viewModel.quotaRules.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    var dailyHours by remember(settings) { mutableStateOf((settings.dailyWorkMinutes / 60).toString()) }
    var dailyMinutes by remember(settings) { mutableStateOf((settings.dailyWorkMinutes % 60).toString()) }
    var monthlyHours by remember(settings) { mutableStateOf((settings.monthlyWorkMinutes / 60).toString()) }
    var monthlyMinutes by remember(settings) { mutableStateOf((settings.monthlyWorkMinutes % 60).toString()) }
    var quotaPercent by remember(settings) { mutableStateOf(settings.officeQuotaPercent.toString()) }
    var quotaMinDays by remember(settings) { mutableStateOf(settings.officeQuotaMinDays.toString()) }
    val flextimeIsNegative = settings.initialFlextimeMinutes < 0
    val absFlexMinutes = kotlin.math.abs(settings.initialFlextimeMinutes)
    var flextimeSign by remember(settings) { mutableStateOf(if (flextimeIsNegative) "-" else "+") }
    var flextimeHours by remember(settings) { mutableStateOf((absFlexMinutes / 60).toString()) }
    var flextimeMinutes by remember(settings) { mutableStateOf((absFlexMinutes % 60).toString()) }
    val overtimeIsNegative = settings.initialOvertimeMinutes < 0
    val absOvertimeMinutes = kotlin.math.abs(settings.initialOvertimeMinutes)
    var overtimeSign by remember(settings) { mutableStateOf(if (overtimeIsNegative) "-" else "+") }
    var overtimeHours by remember(settings) { mutableStateOf((absOvertimeMinutes / 60).toString()) }
    var overtimeMinutes by remember(settings) { mutableStateOf((absOvertimeMinutes % 60).toString()) }

    var annualVacation by remember(settings) { mutableStateOf(settings.annualVacationDays.toString()) }
    var carryOverVacation by remember(settings) { mutableStateOf(settings.carryOverVacationDays.toString()) }
    var specialVacation by remember(settings) { mutableStateOf(settings.specialVacationDays.toString()) }

    var showAddRuleDialog by remember { mutableStateOf(false) }

    var geofenceEnabled by remember(settings) { mutableStateOf(settings.geofenceEnabled) }
    var geofenceAddress by remember(settings) { mutableStateOf(settings.geofenceAddress) }
    var geofenceLat by remember(settings) { mutableStateOf(if (settings.geofenceLat == 0.0) "" else "%.6f".format(settings.geofenceLat)) }
    var geofenceLon by remember(settings) { mutableStateOf(if (settings.geofenceLon == 0.0) "" else "%.6f".format(settings.geofenceLon)) }
    var geofenceRadius by remember(settings) { mutableStateOf(settings.geofenceRadiusMeters.toInt().toString()) }
    var geofenceError by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) geofenceEnabled = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Einstellungen",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Theme
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Design", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
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
            }
        }

        // Work time
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Arbeitszeit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Täglich", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = dailyHours,
                        onValueChange = { dailyHours = it },
                        label = { Text("Std.") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dailyMinutes,
                        onValueChange = { dailyMinutes = it },
                        label = { Text("Min.") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Monatssoll", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = monthlyHours,
                        onValueChange = { monthlyHours = it },
                        label = { Text("Std.") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = monthlyMinutes,
                        onValueChange = { monthlyMinutes = it },
                        label = { Text("Min.") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        }

        // Default quota settings
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Büro-Quote (Standard)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Gilt, wenn keine Zeitregel aktiv ist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = quotaPercent,
                        onValueChange = { quotaPercent = it },
                        label = { Text("Prozent (%)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = quotaMinDays,
                        onValueChange = { quotaMinDays = it },
                        label = { Text("Min. Tage") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        }

        // Quota rules with validity periods
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Quoten-Zeiträume", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showAddRuleDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Regel hinzufügen")
                    }
                }
                Text(
                    "Quoten-Regeln mit Gültigkeitszeitraum (ab Monat)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (quotaRules.isEmpty()) {
                    Text(
                        "Keine Zeiträume definiert – Standard-Quote gilt",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    quotaRules.forEach { rule ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "Ab ${rule.validFrom.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${rule.officeQuotaPercent}% / ${rule.officeQuotaMinDays} Tage",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteQuotaRule(rule) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Löschen",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Flextime
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Gleitzeit-Anfangssaldo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = flextimeSign == "+",
                        onClick = { flextimeSign = if (flextimeSign == "+") "-" else "+" },
                        label = { Text(flextimeSign, fontWeight = FontWeight.Bold) }
                    )
                    OutlinedTextField(
                        value = flextimeHours,
                        onValueChange = { flextimeHours = it },
                        label = { Text("Std.") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = flextimeMinutes,
                        onValueChange = { flextimeMinutes = it },
                        label = { Text("Min.") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                Text(
                    "Vorzeichen antippen zum Wechseln (+/−)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Overtime
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Überstunden-Anfangssaldo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = overtimeSign == "+",
                        onClick = { overtimeSign = if (overtimeSign == "+") "-" else "+" },
                        label = { Text(overtimeSign, fontWeight = FontWeight.Bold) }
                    )
                    OutlinedTextField(
                        value = overtimeHours,
                        onValueChange = { overtimeHours = it },
                        label = { Text("Std.") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = overtimeMinutes,
                        onValueChange = { overtimeMinutes = it },
                        label = { Text("Min.") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                Text(
                    "Vorzeichen antippen zum Wechseln (+/−)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Vacation
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Urlaub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = annualVacation,
                    onValueChange = { annualVacation = it },
                    label = { Text("Jahresurlaub (Tage)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = carryOverVacation,
                    onValueChange = { carryOverVacation = it },
                    label = { Text("Resturlaub Vorjahr") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = specialVacation,
                    onValueChange = { specialVacation = it },
                    label = { Text("Sonderurlaub (verfällt Ende Okt.)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        }

        // Geofencing
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Automatisches Stempeln", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Einstempeln beim Betreten, Ausstempeln beim Verlassen des Büros",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Geofencing aktivieren")
                    Switch(
                        checked = geofenceEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            } else {
                                geofenceEnabled = false
                                viewModel.saveGeofenceSettings(false, 0.0, 0.0, 150f)
                            }
                        }
                    )
                }
                if (geofenceEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Address input + geocode button
                    OutlinedTextField(
                        value = geofenceAddress,
                        onValueChange = { geofenceAddress = it; geofenceError = false },
                        label = { Text("Büro-Adresse") },
                        placeholder = { Text("z.B. Musterstraße 1, München") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = geofenceError,
                        supportingText = if (geofenceError) { { Text("Adresse nicht gefunden") } } else null
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.geocodeAddress(
                                    geofenceAddress,
                                    onResult = { lat, lon ->
                                        geofenceLat = "%.6f".format(lat)
                                        geofenceLon = "%.6f".format(lon)
                                        geofenceError = false
                                    },
                                    onError = { geofenceError = true }
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = geofenceAddress.isNotBlank()
                        ) {
                            Text("Koordinaten suchen")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.fetchCurrentLocation(
                                    onResult = { lat, lon ->
                                        geofenceLat = "%.6f".format(lat)
                                        geofenceLon = "%.6f".format(lon)
                                        geofenceError = false
                                    },
                                    onError = { geofenceError = true }
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Standort jetzt")
                        }
                    }

                    // Resolved coordinates (read-only display)
                    if (geofenceLat.isNotBlank() && geofenceLon.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Koordinaten: $geofenceLat, $geofenceLon",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = geofenceRadius,
                        onValueChange = { geofenceRadius = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Radius in Metern") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val lat = geofenceLat.toDoubleOrNull() ?: 0.0
                            val lon = geofenceLon.toDoubleOrNull() ?: 0.0
                            val radius = geofenceRadius.toFloatOrNull() ?: 150f
                            viewModel.saveGeofenceSettings(true, lat, lon, radius, geofenceAddress)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = geofenceLat.isNotBlank() && geofenceLon.isNotBlank()
                    ) {
                        Text("Bürostandort speichern")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Benötigt Standortberechtigung \"Immer erlauben\". Falls die Berechtigung fehlt, in den App-Einstellungen aktivieren.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("App-Einstellungen öffnen")
                }
            }
        }

        // Backup navigation
        Card(
            onClick = onNavigateToBackup,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Datensicherung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Daten exportieren & importieren",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Öffnen",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Save button
        Button(
            onClick = {
                val h = dailyHours.toIntOrNull() ?: 7
                val m = dailyMinutes.toIntOrNull() ?: 6
                val mh = monthlyHours.toIntOrNull() ?: 154
                val mm = monthlyMinutes.toIntOrNull() ?: 12
                val fh = flextimeHours.toIntOrNull() ?: 0
                val fm = flextimeMinutes.toIntOrNull() ?: 0
                val flextimeTotalMinutes = (fh * 60 + fm) * if (flextimeSign == "-") -1 else 1
                val oh = overtimeHours.toIntOrNull() ?: 0
                val om = overtimeMinutes.toIntOrNull() ?: 0
                val overtimeTotalMinutes = (oh * 60 + om) * if (overtimeSign == "-") -1 else 1
                viewModel.updateSettings(
                    settings.copy(
                        dailyWorkMinutes = h * 60 + m,
                        monthlyWorkMinutes = mh * 60 + mm,
                        officeQuotaPercent = quotaPercent.toIntOrNull() ?: 40,
                        officeQuotaMinDays = quotaMinDays.toIntOrNull() ?: 8,
                        initialFlextimeMinutes = flextimeTotalMinutes,
                        initialOvertimeMinutes = overtimeTotalMinutes,
                        annualVacationDays = annualVacation.toIntOrNull() ?: 30,
                        carryOverVacationDays = carryOverVacation.toIntOrNull() ?: 0,
                        specialVacationDays = specialVacation.toIntOrNull() ?: 5
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Speichern")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Add quota rule dialog
    if (showAddRuleDialog) {
        AddQuotaRuleDialog(
            onDismiss = { showAddRuleDialog = false },
            onConfirm = { rule ->
                viewModel.addQuotaRule(rule)
                showAddRuleDialog = false
            }
        )
    }
}

@Composable
fun AddQuotaRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (QuotaRule) -> Unit
) {
    val now = YearMonth.now()
    var yearText by remember { mutableStateOf(now.year.toString()) }
    var monthText by remember { mutableStateOf(now.monthValue.toString()) }
    var percentText by remember { mutableStateOf("40") }
    var daysText by remember { mutableStateOf("8") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quoten-Regel hinzufügen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Gültig ab", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = monthText,
                        onValueChange = { monthText = it },
                        label = { Text("Monat") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = yearText,
                        onValueChange = { yearText = it },
                        label = { Text("Jahr") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Quote", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = percentText,
                        onValueChange = { percentText = it },
                        label = { Text("Prozent (%)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = daysText,
                        onValueChange = { daysText = it },
                        label = { Text("Min. Tage") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val y = yearText.toIntOrNull() ?: return@TextButton
                val m = monthText.toIntOrNull() ?: return@TextButton
                if (m < 1 || m > 12) return@TextButton
                val p = percentText.toIntOrNull() ?: return@TextButton
                val d = daysText.toIntOrNull() ?: return@TextButton
                onConfirm(QuotaRule(
                    validFrom = YearMonth.of(y, m),
                    officeQuotaPercent = p,
                    officeQuotaMinDays = d
                ))
            }) {
                Text("Hinzufügen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
