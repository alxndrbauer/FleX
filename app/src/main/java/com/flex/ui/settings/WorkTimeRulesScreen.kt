package com.flex.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.flex.domain.model.getRuleForMonth
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<WorkTimeRule?>(null) }

    var pendingRuleToSave by remember { mutableStateOf<WorkTimeRule?>(null) }
    var isEditMode by remember { mutableStateOf(false) }
    var previousDailyMinutesForSync by remember { mutableStateOf<Int?>(null) }
    var affectedDaysCount by remember { mutableIntStateOf(0) }
    var showSyncConfirmDialog by remember { mutableStateOf(false) }

    var ruleToDelete by remember { mutableStateOf<WorkTimeRule?>(null) }
    var deleteAffectedDaysCount by remember { mutableIntStateOf(0) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.syncResultEvent.collect { result ->
            snackbarHostState.showSnackbar(result.formatFeedbackMessage())
        }
    }

    fun onRuleConfirmed(rule: WorkTimeRule, isEdit: Boolean, initialRule: WorkTimeRule?) {
        val oldDailyMinutes = if (isEdit) {
            initialRule?.dailyWorkMinutes?.takeIf { it != rule.dailyWorkMinutes }
        } else {
            workTimeRules.getRuleForMonth(rule.validFrom)?.dailyWorkMinutes?.takeIf { it != rule.dailyWorkMinutes }
        }
        scope.launch {
            val count = viewModel.getAffectedPlannedDaysCount(rule.validFrom)
            if (count > 0) {
                pendingRuleToSave = rule
                isEditMode = isEdit
                previousDailyMinutesForSync = oldDailyMinutes
                affectedDaysCount = count
                showSyncConfirmDialog = true
            } else {
                if (isEdit) {
                    viewModel.updateWorkTimeRule(rule)
                } else {
                    viewModel.addWorkTimeRule(rule)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingRule = rule }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { editingRule = rule }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Bearbeiten",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        deleteAffectedDaysCount = viewModel.getAffectedPlannedDaysCount(rule.validFrom)
                                        ruleToDelete = rule
                                        showDeleteConfirmDialog = true
                                    }
                                }) {
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
        WorkTimeRuleDialog(
            initialRule = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { rule ->
                showAddDialog = false
                onRuleConfirmed(rule, isEdit = false, initialRule = null)
            }
        )
    }

    if (editingRule != null) {
        WorkTimeRuleDialog(
            initialRule = editingRule,
            onDismiss = { editingRule = null },
            onConfirm = { rule ->
                val prev = editingRule
                editingRule = null
                onRuleConfirmed(rule, isEdit = true, initialRule = prev)
            }
        )
    }

    if (showSyncConfirmDialog && pendingRuleToSave != null) {
        val rule = pendingRuleToSave!!
        var removeNonWorkDays by remember { mutableStateOf(false) }
        val monthName = rule.validFrom.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))
        AlertDialog(
            onDismissRequest = {
                showSyncConfirmDialog = false
                pendingRuleToSave = null
            },
            title = { Text("Geplante Tage anpassen?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Es wurden $affectedDaysCount geplante Tage ab $monthName gefunden.\n\nMöchtest du deren Soll-Arbeitszeiten an die neue Regel anpassen?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = removeNonWorkDays,
                            onCheckedChange = { removeNonWorkDays = it }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Tage auf Nicht-Arbeitstagen entfernen",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        "Hinweis: Manuell angepasste Tage mit individuellen Stunden bleiben unverändert.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isEditMode) {
                        viewModel.updateWorkTimeRule(
                            rule = rule,
                            syncPlannedDays = true,
                            removeNonWorkDays = removeNonWorkDays,
                            previousDailyMinutes = previousDailyMinutesForSync
                        )
                    } else {
                        viewModel.addWorkTimeRule(
                            rule = rule,
                            syncPlannedDays = true,
                            removeNonWorkDays = removeNonWorkDays,
                            previousDailyMinutes = previousDailyMinutesForSync
                        )
                    }
                    showSyncConfirmDialog = false
                    pendingRuleToSave = null
                }) {
                    Text("Anpassen")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        if (isEditMode) {
                            viewModel.updateWorkTimeRule(rule = rule, syncPlannedDays = false)
                        } else {
                            viewModel.addWorkTimeRule(rule = rule, syncPlannedDays = false)
                        }
                        showSyncConfirmDialog = false
                        pendingRuleToSave = null
                    }) {
                        Text("Nicht anpassen")
                    }
                    TextButton(onClick = {
                        showSyncConfirmDialog = false
                        pendingRuleToSave = null
                    }) {
                        Text("Abbrechen")
                    }
                }
            }
        )
    }

    if (showDeleteConfirmDialog && ruleToDelete != null) {
        val rule = ruleToDelete!!
        var removeNonWorkDays by remember { mutableStateOf(false) }
        val monthName = rule.validFrom.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                ruleToDelete = null
            },
            title = { Text("Regel löschen?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Möchtest du die Arbeitszeit-Regel ab $monthName wirklich löschen?" +
                            if (deleteAffectedDaysCount > 0) {
                                "\n\nEs wurden $deleteAffectedDaysCount geplante Tage in diesem Zeitraum gefunden. Sollen diese an die vorherige Regel bzw. Basis-Arbeitszeit angepasst werden?"
                            } else "",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (deleteAffectedDaysCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = removeNonWorkDays,
                                onCheckedChange = { removeNonWorkDays = it }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Tage auf Nicht-Arbeitstagen entfernen",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            "Hinweis: Manuell angepasste Tage mit individuellen Stunden bleiben unverändert.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                if (deleteAffectedDaysCount > 0) {
                    TextButton(onClick = {
                        viewModel.deleteWorkTimeRule(
                            rule = rule,
                            syncPlannedDays = true,
                            removeNonWorkDays = removeNonWorkDays
                        )
                        showDeleteConfirmDialog = false
                        ruleToDelete = null
                    }) {
                        Text("Löschen & Anpassen")
                    }
                } else {
                    TextButton(onClick = {
                        viewModel.deleteWorkTimeRule(rule = rule, syncPlannedDays = false)
                        showDeleteConfirmDialog = false
                        ruleToDelete = null
                    }) {
                        Text("Löschen")
                    }
                }
            },
            dismissButton = {
                Row {
                    if (deleteAffectedDaysCount > 0) {
                        TextButton(onClick = {
                            viewModel.deleteWorkTimeRule(rule = rule, syncPlannedDays = false)
                            showDeleteConfirmDialog = false
                            ruleToDelete = null
                        }) {
                            Text("Nur löschen")
                        }
                    }
                    TextButton(onClick = {
                        showDeleteConfirmDialog = false
                        ruleToDelete = null
                    }) {
                        Text("Abbrechen")
                    }
                }
            }
        )
    }
}

@Composable
private fun WorkTimeRuleDialog(
    initialRule: WorkTimeRule? = null,
    onDismiss: () -> Unit,
    onConfirm: (WorkTimeRule) -> Unit
) {
    val now = java.time.YearMonth.now()
    val initialYm = initialRule?.validFrom ?: now
    var monthText by remember { mutableStateOf(initialYm.monthValue.toString()) }
    var yearText by remember { mutableStateOf(initialYm.year.toString()) }

    val initDailyH = initialRule?.let { it.dailyWorkMinutes / 60 } ?: 7
    val initDailyM = initialRule?.let { it.dailyWorkMinutes % 60 } ?: 6
    var dailyHoursText by remember { mutableStateOf(initDailyH.toString()) }
    var dailyMinutesText by remember { mutableStateOf(initDailyM.toString()) }

    val initMonthlyH = initialRule?.let { it.monthlyWorkMinutes / 60 } ?: 154
    val initMonthlyM = initialRule?.let { it.monthlyWorkMinutes % 60 } ?: 26
    var monthlyHoursText by remember { mutableStateOf(initMonthlyH.toString()) }
    var monthlyMinutesText by remember { mutableStateOf(initMonthlyM.toString()) }

    var selectedDays by remember { mutableStateOf(initialRule?.workDays ?: DEFAULT_WORK_DAYS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialRule != null) "Arbeitszeit-Regel bearbeiten" else "Arbeitszeit-Regel hinzufügen") },
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
                        id = initialRule?.id ?: 0L,
                        validFrom = validYm,
                        dailyWorkMinutes = dailyTotal,
                        monthlyWorkMinutes = monthlyTotal,
                        workDays = selectedDays.ifEmpty { DEFAULT_WORK_DAYS }
                    )
                )
            }) {
                Text(if (initialRule != null) "Speichern" else "Hinzufügen")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
