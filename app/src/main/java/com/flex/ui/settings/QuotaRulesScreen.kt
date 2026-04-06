package com.flex.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun QuotaRulesScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val quotaRules by viewModel.quotaRules.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quoten-Zeiträume") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Regel hinzufügen")
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (quotaRules.isEmpty()) {
                item {
                    Text("Keine Zeiträume definiert – Standard-Quote gilt", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                }
            } else {
                items(quotaRules) { rule ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Ab ${rule.validFrom.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("${rule.officeQuotaPercent}% / ${rule.officeQuotaMinDays} Tage", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { viewModel.deleteQuotaRule(rule) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Regel hinzufügen")
                }
            }
        }
    }

    if (showAddDialog) {
        AddQuotaRuleDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { rule -> viewModel.addQuotaRule(rule); showAddDialog = false }
        )
    }
}

@Composable
private fun AddQuotaRuleDialog(onDismiss: () -> Unit, onConfirm: (QuotaRule) -> Unit) {
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
                    OutlinedTextField(value = monthText, onValueChange = { monthText = it }, label = { Text("Monat") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(value = yearText, onValueChange = { yearText = it }, label = { Text("Jahr") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
                Text("Quote", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = percentText, onValueChange = { percentText = it }, label = { Text("Prozent (%)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(value = daysText, onValueChange = { daysText = it }, label = { Text("Min. Tage") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
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
                onConfirm(QuotaRule(validFrom = YearMonth.of(y, m), officeQuotaPercent = p, officeQuotaMinDays = d))
            }) { Text("Hinzufügen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
