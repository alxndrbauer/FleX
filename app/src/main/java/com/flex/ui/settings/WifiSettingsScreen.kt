package com.flex.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    var wifiEnabled by remember(settings) { mutableStateOf(settings.wifiAutoStampEnabled) }
    var wifiSsid by remember(settings) { mutableStateOf(settings.wifiSsid) }
    var wifiSaved by remember { mutableStateOf(false) }

    LaunchedEffect(wifiSaved) {
        if (wifiSaved) { delay(2000); wifiSaved = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WLAN Auto-Stempeln") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
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
            Text("Einstempeln beim Verbinden, Ausstempeln beim Trennen vom Büro-WLAN", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("WLAN-Stempeln aktivieren")
                Switch(checked = wifiEnabled, onCheckedChange = { wifiEnabled = it })
            }

            if (wifiEnabled) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = wifiSsid, onValueChange = { wifiSsid = it }, label = { Text("WLAN-Name (SSID)") }, placeholder = { Text("z.B. Buero-WLAN") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedButton(onClick = { viewModel.getCurrentWifiSsid()?.let { wifiSsid = it } }) { Text("Aktuell") }
                }
                Button(
                    onClick = { viewModel.saveWifiSettings(true, wifiSsid); wifiSaved = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = wifiSsid.isNotBlank()
                ) { Text("WLAN-Einstellungen speichern") }
                if (wifiSaved) {
                    Text("Gespeichert", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { viewModel.saveWifiSettings(false, wifiSsid) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("WLAN-Stempeln deaktivieren") }
            }

            HorizontalDivider()
            Text("Benötigt Standortberechtigung für WLAN-Name. Bei \"<unknown ssid>\" Standort aktivieren.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
