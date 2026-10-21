package com.flex.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GeofenceSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val geofenceStatus by viewModel.geofenceStatus.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var geofenceEnabled by remember(settings.geofenceEnabled) { mutableStateOf(settings.geofenceEnabled) }
    var geofenceAddress by remember(settings.geofenceAddress) { mutableStateOf(settings.geofenceAddress) }
    var geofenceLat by remember(settings.geofenceLat) {
        mutableStateOf(if (settings.geofenceLat == 0.0) "" else "%.6f".format(Locale.US, settings.geofenceLat))
    }
    var geofenceLon by remember(settings.geofenceLon) {
        mutableStateOf(if (settings.geofenceLon == 0.0) "" else "%.6f".format(Locale.US, settings.geofenceLon))
    }
    var geofenceRadius by remember(settings.geofenceRadiusMeters) {
        mutableStateOf(settings.geofenceRadiusMeters.toInt().toString())
    }
    var geofenceError by remember { mutableStateOf(false) }
    var geofenceSaved by remember { mutableStateOf(false) }

    val currentRadiusFloat = (geofenceRadius.replace(',', '.').toFloatOrNull() ?: 150f).coerceIn(50f, 1000f)

    LaunchedEffect(geofenceSaved) {
        if (geofenceSaved) {
            delay(2500)
            geofenceSaved = false
        }
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            geofenceEnabled = true
            val lat = geofenceLat.replace(',', '.').toDoubleOrNull() ?: settings.geofenceLat
            val lon = geofenceLon.replace(',', '.').toDoubleOrNull() ?: settings.geofenceLon
            val radius = geofenceRadius.replace(',', '.').toFloatOrNull() ?: settings.geofenceRadiusMeters
            if (lat != 0.0 && lon != 0.0) {
                viewModel.saveGeofenceSettings(true, lat, lon, radius, geofenceAddress)
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                geofenceEnabled = true
                val lat = geofenceLat.replace(',', '.').toDoubleOrNull() ?: settings.geofenceLat
                val lon = geofenceLon.replace(',', '.').toDoubleOrNull() ?: settings.geofenceLon
                val radius = geofenceRadius.replace(',', '.').toFloatOrNull() ?: settings.geofenceRadiusMeters
                if (lat != 0.0 && lon != 0.0) {
                    viewModel.saveGeofenceSettings(true, lat, lon, radius, geofenceAddress)
                }
            }
        }
    }

    val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasBackgroundLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val isIgnoringBatteryOpt = powerManager.isIgnoringBatteryOptimizations(context.packageName)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Geofencing") },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Card 1: Geofence-Aktivierung & Status ────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (geofenceEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (geofenceEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Büro-Geofencing",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Automatisches Stempeln am Standort",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        GeofenceStatusPill(
                            isEnabled = geofenceEnabled,
                            status = geofenceStatus
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Geofencing aktivieren",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Stempelt ein beim Betreten und aus beim Verlassen des Büros",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = geofenceEnabled,
                            onCheckedChange = { enabled ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (enabled) {
                                    if (hasFineLocation) {
                                        if (!hasBackgroundLocation) {
                                            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                        } else {
                                            geofenceEnabled = true
                                            val lat = geofenceLat.replace(',', '.').toDoubleOrNull() ?: settings.geofenceLat
                                            val lon = geofenceLon.replace(',', '.').toDoubleOrNull() ?: settings.geofenceLon
                                            val radius = geofenceRadius.replace(',', '.').toFloatOrNull() ?: settings.geofenceRadiusMeters
                                            if (lat != 0.0 && lon != 0.0) {
                                                viewModel.saveGeofenceSettings(true, lat, lon, radius, geofenceAddress)
                                            }
                                        }
                                    } else {
                                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    }
                                } else {
                                    geofenceEnabled = false
                                    val lat = geofenceLat.replace(',', '.').toDoubleOrNull() ?: settings.geofenceLat
                                    val lon = geofenceLon.replace(',', '.').toDoubleOrNull() ?: settings.geofenceLon
                                    val radius = geofenceRadius.replace(',', '.').toFloatOrNull() ?: settings.geofenceRadiusMeters
                                    viewModel.saveGeofenceSettings(false, lat, lon, radius, geofenceAddress)
                                }
                            }
                        )
                    }

                    // GPS- und Status-Badges
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text(
                        text = "System- und Berechtigungsstatus",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // GPS & Hintergrund-Berechtigung
                        when {
                            hasFineLocation && hasBackgroundLocation -> {
                                GeofenceBadge(
                                    icon = Icons.Default.GpsFixed,
                                    text = "GPS: Immer erlaubt",
                                    badgeType = BadgeType.SUCCESS
                                )
                            }
                            hasFineLocation -> {
                                GeofenceBadge(
                                    icon = Icons.Default.GpsNotFixed,
                                    text = "Hintergrund-GPS fehlt",
                                    badgeType = BadgeType.WARNING,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                    }
                                )
                            }
                            else -> {
                                GeofenceBadge(
                                    icon = Icons.Default.LocationOff,
                                    text = "GPS fehlt",
                                    badgeType = BadgeType.ERROR,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    }
                                )
                            }
                        }

                        // Registrierungsstatus
                        when (geofenceStatus) {
                            GeofenceStatus.REGISTERED -> {
                                GeofenceBadge(
                                    icon = Icons.Default.CheckCircle,
                                    text = "Geofence registriert",
                                    badgeType = BadgeType.SUCCESS
                                )
                            }
                            GeofenceStatus.FAILED -> {
                                GeofenceBadge(
                                    icon = Icons.Default.Error,
                                    text = "Registrierung fehlgeschlagen",
                                    badgeType = BadgeType.ERROR
                                )
                            }
                            GeofenceStatus.UNKNOWN -> {
                                GeofenceBadge(
                                    icon = Icons.Default.LocationOn,
                                    text = if (geofenceEnabled) "Bereit zum Speichern" else "Dienst inaktiv",
                                    badgeType = BadgeType.NEUTRAL
                                )
                            }
                        }

                        // Akku-Optimierung
                        if (isIgnoringBatteryOpt) {
                            GeofenceBadge(
                                icon = Icons.Default.BatteryChargingFull,
                                text = "Akku unbeschränkt",
                                badgeType = BadgeType.SUCCESS
                            )
                        } else {
                            GeofenceBadge(
                                icon = Icons.Default.BatterySaver,
                                text = "Akku-Sparmodus aktiv",
                                badgeType = BadgeType.WARNING,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
            }

            // ── Card 2: Büro-Standort ─────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Büro-Standort",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Gib die Büro-Adresse ein oder erfasse die Koordinaten direkt vor Ort.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = geofenceAddress,
                        onValueChange = {
                            geofenceAddress = it
                            geofenceError = false
                        },
                        label = { Text("Büro-Adresse") },
                        placeholder = { Text("z.B. Musterstraße 1, München") },
                        leadingIcon = {
                            Icon(Icons.Default.Business, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = geofenceError,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (geofenceAddress.isNotBlank()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.geocodeAddress(
                                        geofenceAddress,
                                        onResult = { lat, lon ->
                                            geofenceLat = "%.6f".format(Locale.US, lat)
                                            geofenceLon = "%.6f".format(Locale.US, lon)
                                            geofenceError = false
                                        },
                                        onError = { geofenceError = true }
                                    )
                                }
                            }
                        ),
                        supportingText = if (geofenceError) {
                            { Text("Adresse konnte nicht aufgelöst werden") }
                        } else null,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.geocodeAddress(
                                    geofenceAddress,
                                    onResult = { lat, lon ->
                                        geofenceLat = "%.6f".format(Locale.US, lat)
                                        geofenceLon = "%.6f".format(Locale.US, lon)
                                        geofenceError = false
                                    },
                                    onError = { geofenceError = true }
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = geofenceAddress.isNotBlank()
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Suchen")
                        }

                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.fetchCurrentLocation(
                                    onResult = { lat, lon ->
                                        geofenceLat = "%.6f".format(Locale.US, lat)
                                        geofenceLon = "%.6f".format(Locale.US, lon)
                                        geofenceError = false
                                    },
                                    onError = { geofenceError = true }
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Standort jetzt")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = geofenceLat,
                            onValueChange = {
                                geofenceLat = it.replace(',', '.')
                                geofenceError = false
                            },
                            label = { Text("Breitengrad (Lat)") },
                            placeholder = { Text("48.137154") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = geofenceLon,
                            onValueChange = {
                                geofenceLon = it.replace(',', '.')
                                geofenceError = false
                            },
                            label = { Text("Längengrad (Lon)") },
                            placeholder = { Text("11.576124") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // ── Card 3: Radius-Einstellung & Speichern ────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Erkennungs-Radius",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Bereich um das Büro",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Live-Distanz-Badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text(
                                text = "${currentRadiusFloat.toInt()} m",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Slider mit Live-Distanz
                    Slider(
                        value = currentRadiusFloat,
                        onValueChange = { newRadius ->
                            geofenceRadius = newRadius.toInt().toString()
                        },
                        onValueChangeFinished = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        valueRange = 50f..1000f,
                        steps = 18,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Preset-Chips mit barrierefreiem FlowRow
                    Text(
                        text = "Schnellauswahl:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presets = listOf(100, 150, 250, 500)
                        presets.forEach { preset ->
                            val isSelected = currentRadiusFloat.toInt() == preset
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    geofenceRadius = preset.toString()
                                },
                                label = { Text("$preset m") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            val lat = geofenceLat.replace(',', '.').toDoubleOrNull() ?: 0.0
                            val lon = geofenceLon.replace(',', '.').toDoubleOrNull() ?: 0.0
                            val radius = currentRadiusFloat
                            viewModel.saveGeofenceSettings(geofenceEnabled, lat, lon, radius, geofenceAddress)
                            geofenceSaved = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = geofenceLat.isNotBlank() && geofenceLon.isNotBlank()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bürostandort speichern")
                    }

                    AnimatedVisibility(
                        visible = geofenceSaved,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Bürostandort erfolgreich gespeichert",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // ── Card 4: Map-Vorschau ──────────────────────────────────────
            val currentLat = geofenceLat.replace(',', '.').toDoubleOrNull() ?: settings.geofenceLat
            val currentLon = geofenceLon.replace(',', '.').toDoubleOrNull() ?: settings.geofenceLon
            val currentRadius = currentRadiusFloat
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Karten-Vorschau",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (currentLat != 0.0 && currentLon != 0.0) {
                            Text(
                                text = "Radius: ${currentRadius.toInt()} m",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (currentLat != 0.0 && currentLon != 0.0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            GeofenceMapPreview(
                                lat = currentLat,
                                lon = currentLon,
                                radiusMeters = currentRadius
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(vertical = 32.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Adresse eingeben oder Standort erfassen, um die Karten-Vorschau anzuzeigen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // ── Card 5: Berechtigungen & Akku ─────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Hintergrundberechtigung & Akku-Optimierung",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Damit das Ein- und Ausstempeln auch bei gesperrtem Bildschirm zuverlässig funktioniert, muss der Standort auf \"Immer erlauben\" stehen und die Akku-Optimierung für FleX deaktiviert sein.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.SettingsIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("App-Einstellungen öffnen")
                    }

                    if (!isIgnoringBatteryOpt) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.BatterySaver, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Akku-Optimierung deaktivieren")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private enum class BadgeType {
    SUCCESS, WARNING, ERROR, NEUTRAL
}

@Composable
private fun GeofenceBadge(
    icon: ImageVector,
    text: String,
    badgeType: BadgeType,
    onClick: (() -> Unit)? = null
) {
    val (containerColor, contentColor) = when (badgeType) {
        BadgeType.SUCCESS -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        BadgeType.WARNING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        BadgeType.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        BadgeType.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = if (onClick != null) {
            Modifier.clip(RoundedCornerShape(8.dp)).clickable { onClick() }
        } else {
            Modifier
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun GeofenceStatusPill(
    isEnabled: Boolean,
    status: GeofenceStatus
) {
    val (bgColor, textColor, dotColor, label) = when {
        !isEnabled -> Quadruple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.outline,
            "Inaktiv"
        )
        status == GeofenceStatus.REGISTERED -> Quadruple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Color(0xFF34A853),
            "Aktiv"
        )
        status == GeofenceStatus.FAILED -> Quadruple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.error,
            "Fehler"
        )
        else -> Quadruple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.primary,
            "Aktiviert"
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        contentColor = textColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color = dotColor, shape = CircleShape)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
