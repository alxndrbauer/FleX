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
import com.flex.domain.model.DEFAULT_WORK_DAYS
import com.flex.domain.model.WorkTimeRule
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val WEEKDAY_OPTIONS = listOf(
    DayOfWeek.MONDAY to "Montag",
    DayOfWeek.TUESDAY to "Dienstag",
    DayOfWeek.WEDNESDAY to "Mittwoch",
    DayOfWeek.THURSDAY to "Donnerstag",
    DayOfWeek.FRIDAY to "Freitag"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkTimeRulesScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val workTimeRules by viewModel.workTimeRules.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Arbeitszeit-Zeiträume") },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (workTimeRules.isEmpty()) {
                item {
                    Text(
                        "Keine Zeiträume definiert – Basis-Arbeitszeit gilt",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(workTimeRules) { rule ->
                    Card(modifier = Modifier.fillMaxWidth()) {
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
                                val dailyH = rule.dailyWorkMinutes / 60
                                val dailyM = rule.dailyWorkMinutes % 60
                                val monthlyH = rule.monthlyWorkMinutes / 60
                                val monthlyM = rule.monthlyWorkMinutes % 60
                                Text(
                                    "Täglich: ${dailyH}h ${dailyM}m | Monatlich: ${monthlyH}h ${monthlyM}m",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                val dayLabels = rule.workDays
                                    .sortedBy { it.value }
                                    .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.GERMAN) }
                                Text(
                                    "Arbeitstage: $dayLabels",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.deleteWorkTimeRule(rule) }) {
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
        AddWorkTimeRuleDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { rule ->
                viewModel.addWorkTimeRule(rule)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddWorkTimeRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (WorkTimeRule) -> Unit
) {
    val now = java.time.YearMonth.now()
    var monthText by remember { mutableStateOf(now.monthValue.toString()) }
    var yearText by remember { mutableStateOf(now.year.toString()) }
    var dailyHoursText by remember { mutableStateOf("7") }
    var dailyMinutesText by remember { mutableStateOf("6") }
    var monthlyHoursText by remember { mutableStateOf("154") }
    var monthlyMinutesText by remember { mutableStateOf("26") }
    var selectedDays by remember { mutableStateOf(DEFAULT_WORK_DAYS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Arbeitszeit-Regel hinzufügen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Gültig ab (Monat & Jahr)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = monthText,
                        onValueChange = { monthText = it },
                        label = { Text("Monat (1-12)") },
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
                Text("Tägliche Soll-Arbeitszeit", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dailyHoursText,
                        onValueChange = { dailyHoursText = it },
                        label = { Text("Stunden") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dailyMinutesText,
                        onValueChange = { dailyMinutesText = it },
                        label = { Text("Minuten") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                Text("Monatliche Soll-Arbeitszeit", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = monthlyHoursText,
                        onValueChange = { monthlyHoursText = it },
                        label = { Text("Stunden") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = monthlyMinutesText,
                        onValueChange = { monthlyMinutesText = it },
                        label = { Text("Minuten") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                Text("Arbeitstage", style = MaterialTheme.typography.labelMedium)
                WEEKDAY_OPTIONS.forEach { (day, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = day in selectedDays,
                            onCheckedChange = { checked ->
                                selectedDays = if (checked) selectedDays + day else selectedDays - day
                            }
                        )
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = monthText.toIntOrNull() ?: return@TextButton
                val y = yearText.toIntOrNull() ?: return@TextButton
                if (m < 1 || m > 12) return@TextButton

                val dH = dailyHoursText.toIntOrNull() ?: 0
                val dM = dailyMinutesText.toIntOrNull() ?: 0
                val dailyTotal = dH * 60 + dM

                val mH = monthlyHoursText.toIntOrNull() ?: 0
                val mM = monthlyMinutesText.toIntOrNull() ?: 0
                val monthlyTotal = mH * 60 + mM

                val validYm = try {
                    java.time.YearMonth.of(y, m)
                } catch (_: Exception) {
                    return@TextButton
                }

                onConfirm(
                    WorkTimeRule(
                        validFrom = validYm,
                        dailyWorkMinutes = dailyTotal,
                        monthlyWorkMinutes = monthlyTotal,
                        workDays = selectedDays.ifEmpty { DEFAULT_WORK_DAYS }
                    )
                )
            }) { Text("Hinzufügen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
