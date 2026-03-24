package com.flex.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.flex.R
import com.flex.domain.model.AppIconVariant
import com.flex.domain.model.ThemeMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
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
    onNavigateToAbout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val quotaRules by viewModel.quotaRules.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val appIconVariant by viewModel.appIconVariant.collectAsState()
    val geofenceStatus by viewModel.geofenceStatus.collectAsState()

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
    var geofenceLat by remember(settings) { mutableStateOf(if (settings.geofenceLat == 0.0) "" else "%.6f".format(java.util.Locale.US, settings.geofenceLat)) }
    var geofenceLon by remember(settings) { mutableStateOf(if (settings.geofenceLon == 0.0) "" else "%.6f".format(java.util.Locale.US, settings.geofenceLon)) }
    var geofenceRadius by remember(settings) { mutableStateOf(settings.geofenceRadiusMeters.toInt().toString()) }
    var geofenceError by remember { mutableStateOf(false) }
    var geofenceSaved by remember { mutableStateOf(false) }
    LaunchedEffect(geofenceSaved) {
        if (geofenceSaved) {
            delay(2000)
            geofenceSaved = false
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) geofenceEnabled = true
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
            ) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                geofenceEnabled = true
            }
        }
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

        // App icon & name
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("App-Icon & Name", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Änderung wird nach kurzer Zeit im Launcher sichtbar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AppIconVariant.entries.forEach { variant ->
                        AppIconVariantItem(
                            variant = variant,
                            isSelected = appIconVariant == variant,
                            onClick = { viewModel.setAppIconVariant(variant) }
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
                                val fineGranted = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                if (fineGranted) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                            != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                    } else {
                                        geofenceEnabled = true
                                    }
                                } else {
                                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
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
                                        geofenceLat = "%.6f".format(java.util.Locale.US, lat)
                                        geofenceLon = "%.6f".format(java.util.Locale.US, lon)
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
                                        geofenceLat = "%.6f".format(java.util.Locale.US, lat)
                                        geofenceLon = "%.6f".format(java.util.Locale.US, lon)
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

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = geofenceLat,
                            onValueChange = { geofenceLat = it; geofenceError = false },
                            label = { Text("Breitengrad") },
                            placeholder = { Text("48.137154") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        OutlinedTextField(
                            value = geofenceLon,
                            onValueChange = { geofenceLon = it; geofenceError = false },
                            label = { Text("Längengrad") },
                            placeholder = { Text("11.576124") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
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
                            geofenceSaved = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = geofenceLat.isNotBlank() && geofenceLon.isNotBlank()
                    ) {
                        Text("Bürostandort speichern")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (geofenceSaved) {
                        Text(
                            "Gespeichert",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        when (geofenceStatus) {
                            GeofenceStatus.REGISTERED -> Text(
                                "Geofence aktiv",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            GeofenceStatus.FAILED -> Text(
                                "Geofence-Registrierung fehlgeschlagen – prüfe Berechtigungen",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            GeofenceStatus.UNKNOWN -> {}
                        }
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
                val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                val isIgnoringBatteryOpt = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                Spacer(modifier = Modifier.height(4.dp))
                if (isIgnoringBatteryOpt) {
                    Text(
                        "Akku-Optimierung ist deaktiviert ✓",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        "Falls Geofencing trotzdem nicht zuverlässig funktioniert: Akku-Optimierung für diese App deaktivieren.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Akku-Optimierung deaktivieren")
                    }
                }
            }
        }

        // WiFi Auto-Stamp
        run {
            var wifiEnabled by remember(settings) { mutableStateOf(settings.wifiAutoStampEnabled) }
            var wifiSsid by remember(settings) { mutableStateOf(settings.wifiSsid) }
            var wifiSaved by remember { mutableStateOf(false) }
            LaunchedEffect(wifiSaved) {
                if (wifiSaved) { delay(2000); wifiSaved = false }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Automatisches Stempeln (WLAN)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Einstempeln beim Verbinden, Ausstempeln beim Trennen vom Büro-WLAN",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("WLAN-Stempeln aktivieren")
                        Switch(
                            checked = wifiEnabled,
                            onCheckedChange = { wifiEnabled = it }
                        )
                    }
                    if (wifiEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = wifiSsid,
                                onValueChange = { wifiSsid = it },
                                label = { Text("WLAN-Name (SSID)") },
                                placeholder = { Text("z.B. Buero-WLAN") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedButton(
                                onClick = {
                                    viewModel.getCurrentWifiSsid()?.let { wifiSsid = it }
                                }
                            ) {
                                Text("Aktuell")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.saveWifiSettings(true, wifiSsid)
                                wifiSaved = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = wifiSsid.isNotBlank()
                        ) {
                            Text("WLAN-Einstellungen speichern")
                        }
                        if (wifiSaved) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Gespeichert",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Benötigt Standortberechtigung für WLAN-Name. Bei \"<unknown ssid>\" Standort aktivieren.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Break Warning
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Arbeitszeit-Warnungen",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pausenzeitverletzungen warnen")
                        Text(
                            "Warnung bei Verstößen gegen §4 ArbZG",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.breakWarningEnabled,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(breakWarningEnabled = it)) }
                    )
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

        // About navigation
        Card(
            onClick = onNavigateToAbout,
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
                    Text("Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Version & Kontakt",
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

@Composable
// Maps variant to its foreground bitmap (plain WebP, not adaptive icon XML)
// R.mipmap.ic_launcher would resolve to an adaptive-icon XML on API 26+,
// which painterResource cannot render. The foreground mipmaps are plain bitmaps.
private fun AppIconVariant.foregroundRes() = when (this) {
    AppIconVariant.CLASSIC -> R.mipmap.ic_launcher_foreground
    AppIconVariant.VREMA   -> R.mipmap.ic_launcher_vrema_foreground
}

private fun AppIconVariant.bgColor() = when (this) {
    AppIconVariant.CLASSIC -> Color(0xFF000000)
    AppIconVariant.VREMA   -> Color(0xFF3DDC84)
}

@Composable
private fun AppIconVariantItem(
    variant: AppIconVariant,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(shape)
                .background(variant.bgColor())
                .then(
                    if (isSelected)
                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
                    else
                        Modifier
                )
        ) {
            Image(
                painter = painterResource(id = variant.foregroundRes()),
                contentDescription = variant.displayName,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = variant.appLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
