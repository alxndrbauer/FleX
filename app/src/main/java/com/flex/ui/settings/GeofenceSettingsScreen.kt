package com.flex.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val geofenceStatus by viewModel.geofenceStatus.collectAsState()
    val context = LocalContext.current

    var geofenceEnabled by remember(settings) { mutableStateOf(settings.geofenceEnabled) }
    var geofenceAddress by remember(settings) { mutableStateOf(settings.geofenceAddress) }
    var geofenceLat by remember(settings) { mutableStateOf(if (settings.geofenceLat == 0.0) "" else "%.6f".format(java.util.Locale.US, settings.geofenceLat)) }
    var geofenceLon by remember(settings) { mutableStateOf(if (settings.geofenceLon == 0.0) "" else "%.6f".format(java.util.Locale.US, settings.geofenceLon)) }
    var geofenceRadius by remember(settings) { mutableStateOf(settings.geofenceRadiusMeters.toInt().toString()) }
    var geofenceError by remember { mutableStateOf(false) }
    var geofenceSaved by remember { mutableStateOf(false) }

    LaunchedEffect(geofenceSaved) {
        if (geofenceSaved) { delay(2000); geofenceSaved = false }
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) geofenceEnabled = true }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                geofenceEnabled = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Geofencing") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Einstempeln beim Betreten, Ausstempeln beim Verlassen des Büros",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
                            val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (fineGranted) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.geocodeAddress(geofenceAddress,
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
                    ) { Text("Koordinaten suchen") }
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
                    ) { Text("Standort jetzt") }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = geofenceLat, onValueChange = { geofenceLat = it; geofenceError = false }, label = { Text("Breitengrad") }, placeholder = { Text("48.137154") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = geofenceLon, onValueChange = { geofenceLon = it; geofenceError = false }, label = { Text("Längengrad") }, placeholder = { Text("11.576124") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                OutlinedTextField(value = geofenceRadius, onValueChange = { geofenceRadius = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Radius in Metern") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
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
                ) { Text("Bürostandort speichern") }
                if (geofenceSaved) {
                    Text("Gespeichert", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                } else {
                    when (geofenceStatus) {
                        GeofenceStatus.REGISTERED -> Text("Geofence aktiv", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        GeofenceStatus.FAILED -> Text("Geofence-Registrierung fehlgeschlagen – prüfe Berechtigungen", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        GeofenceStatus.UNKNOWN -> {}
                    }
                }

                val savedLat = settings.geofenceLat
                val savedLon = settings.geofenceLon
                val savedRadius = settings.geofenceRadiusMeters
                if (savedLat != 0.0 && savedLon != 0.0) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Abgedeckter Bereich",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Radius: ${savedRadius.toInt()} m",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Surface(
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        GeofenceMapPreview(
                            lat = savedLat,
                            lon = savedLon,
                            radiusMeters = savedRadius
                        )
                    }
                }
            }

            HorizontalDivider()

            Text("Benötigt Standortberechtigung \"Immer erlauben\". Falls die Berechtigung fehlt, in den App-Einstellungen aktivieren.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("App-Einstellungen öffnen") }

            val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            val isIgnoringBatteryOpt = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            if (isIgnoringBatteryOpt) {
                Text("Akku-Optimierung ist deaktiviert ✓", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            } else {
                Text("Falls Geofencing trotzdem nicht zuverlässig funktioniert: Akku-Optimierung für diese App deaktivieren.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Akku-Optimierung deaktivieren") }
            }
        }
    }
}
