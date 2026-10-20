package com.flex.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flex.domain.model.DEFAULT_WORK_DAYS
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.model.getRuleForMonth
import com.flex.ui.components.EmptyStateView
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
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
    val haptic = LocalHapticFeedback.current

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
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateBack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showAddDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Regel hinzufügen")
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { innerPadding ->
        if (workTimeRules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateView(
                    icon = Icons.Default.DateRange,
                    title = "Keine Zeiträume definiert",
                    description = "Die Basis-Arbeitszeit aus den Einstellungen gilt für alle Tage.",
                    actionLabel = "Regel hinzufügen",
                    onAction = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showAddDialog = true
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(workTimeRules, key = { it.id }) { rule ->
                    WorkTimeRuleCard(
                        rule = rule,
                        onEdit = { editingRule = rule },
                        onDelete = {
                            scope.launch {
                                deleteAffectedDaysCount = viewModel.getAffectedPlannedDaysCount(rule.validFrom)
                                ruleToDelete = rule
                                showDeleteConfirmDialog = true
                            }
                        }
                    )
                }

                item {
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showAddDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Regel hinzufügen", fontWeight = FontWeight.SemiBold)
                    }
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
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
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
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.deleteWorkTimeRule(rule = rule, syncPlannedDays = false)
                        showDeleteConfirmDialog = false
                        ruleToDelete = null
                    }) {
                        Text("Löschen", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                Row {
                    if (deleteAffectedDaysCount > 0) {
                        TextButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.deleteWorkTimeRule(rule = rule, syncPlannedDays = false)
                            showDeleteConfirmDialog = false
                            ruleToDelete = null
                        }) {
                            Text("Nur löschen")
                        }
                    }
                    TextButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
private fun WorkTimeRuleCard(
    rule: WorkTimeRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val dailyH = rule.dailyWorkMinutes / 60
    val dailyM = rule.dailyWorkMinutes % 60
    val dailyFormatted = if (dailyM == 0) "${dailyH}h" else "${dailyH}h ${dailyM}m"
    val monthlyH = rule.monthlyWorkMinutes / 60
    val monthlyM = rule.monthlyWorkMinutes % 60
    val monthlyFormatted = if (monthlyM == 0) "${monthlyH}h" else "${monthlyH}h ${monthlyM}m"

    val displayDays = if (rule.workDays.any { it == DayOfWeek.SATURDAY || it == DayOfWeek.SUNDAY }) {
        listOf(
            DayOfWeek.MONDAY to "Mo",
            DayOfWeek.TUESDAY to "Di",
            DayOfWeek.WEDNESDAY to "Mi",
            DayOfWeek.THURSDAY to "Do",
            DayOfWeek.FRIDAY to "Fr",
            DayOfWeek.SATURDAY to "Sa",
            DayOfWeek.SUNDAY to "So"
        )
    } else {
        listOf(
            DayOfWeek.MONDAY to "Mo",
            DayOfWeek.TUESDAY to "Di",
            DayOfWeek.WEDNESDAY to "Mi",
            DayOfWeek.THURSDAY to "Do",
            DayOfWeek.FRIDAY to "Fr"
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onEdit()
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Icon, Date, Actions
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
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Column {
                        Text(
                            text = "Gültig ab",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = rule.validFrom.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onEdit()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Bearbeiten",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDelete()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Löschen",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Working times Badges: Daily & Monthly
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Täglich",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = dailyFormatted,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Column {
                            Text(
                                text = "Monatlich",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = monthlyFormatted,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Compact Weekday Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Arbeitstage:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
                displayDays.forEach { (day, label) ->
                    val isActive = day in rule.workDays
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isActive) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = if (!isActive) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        } else null,
                        modifier = Modifier.size(width = 34.dp, height = 26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkTimeRuleDialog(
    initialRule: WorkTimeRule? = null,
    onDismiss: () -> Unit,
    onConfirm: (WorkTimeRule) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val now = YearMonth.now()
    val initialYm = initialRule?.validFrom ?: now
    var monthText by remember { mutableStateOf(initialYm.monthValue.toString()) }
    var yearText by remember { mutableStateOf(initialYm.year.toString()) }

    val initDailyH = initialRule?.let { it.dailyWorkMinutes / 60 } ?: 7
    val initDailyM = initialRule?.let { it.dailyWorkMinutes % 60 } ?: 48
    var dailyHoursText by remember { mutableStateOf(initDailyH.toString()) }
    var dailyMinutesText by remember { mutableStateOf(initDailyM.toString()) }

    val initMonthlyH = initialRule?.let { it.monthlyWorkMinutes / 60 } ?: 160
    val initMonthlyM = initialRule?.let { it.monthlyWorkMinutes % 60 } ?: 0
    var monthlyHoursText by remember { mutableStateOf(initMonthlyH.toString()) }
    var monthlyMinutesText by remember { mutableStateOf(initMonthlyM.toString()) }

    var selectedDays by remember { mutableStateOf(initialRule?.workDays ?: DEFAULT_WORK_DAYS) }

    val parsedMonth = monthText.toIntOrNull()
    val parsedYear = yearText.toIntOrNull()
    val isDateValid = parsedMonth != null && parsedMonth in 1..12 && parsedYear != null && parsedYear in 2000..2100

    val parsedDailyH = dailyHoursText.toIntOrNull()
    val parsedDailyM = dailyMinutesText.toIntOrNull()
    val isDailyValid = parsedDailyH != null && parsedDailyH in 0..24 &&
        parsedDailyM != null && parsedDailyM in 0..59 &&
        (parsedDailyH * 60 + parsedDailyM) > 0

    val parsedMonthlyH = monthlyHoursText.toIntOrNull()
    val parsedMonthlyM = monthlyMinutesText.toIntOrNull()
    val isMonthlyValid = parsedMonthlyH != null && parsedMonthlyH in 0..744 &&
        parsedMonthlyM != null && parsedMonthlyM in 0..59 &&
        (parsedMonthlyH * 60 + parsedMonthlyM) > 0

    val isWorkDaysValid = selectedDays.isNotEmpty()
    val isFormValid = isDateValid && isDailyValid && isMonthlyValid && isWorkDaysValid

    val previewDateText = remember(parsedMonth, parsedYear) {
        if (isDateValid) {
            try {
                YearMonth.of(parsedYear, parsedMonth).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))
            } catch (_: Exception) { null }
        } else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (initialRule != null) "Arbeitszeit-Regel bearbeiten" else "Arbeitszeit-Regel hinzufügen",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section: Gültig ab
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Gültig ab (Monat & Jahr)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Month & Year quick presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuggestionChip(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                monthText = now.monthValue.toString()
                                yearText = now.year.toString()
                            },
                            label = { Text("Aktueller Monat") },
                            modifier = Modifier.weight(1f)
                        )
                        val nextMonth = now.plusMonths(1)
                        SuggestionChip(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                monthText = nextMonth.monthValue.toString()
                                yearText = nextMonth.year.toString()
                            },
                            label = { Text("Nächster Monat") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = monthText,
                            onValueChange = { monthText = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("Monat (1–12)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = monthText.isNotEmpty() && (parsedMonth == null || parsedMonth < 1 || parsedMonth > 12)
                        )
                        OutlinedTextField(
                            value = yearText,
                            onValueChange = { yearText = it.filter { c -> c.isDigit() }.take(4) },
                            label = { Text("Jahr") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = yearText.length == 4 && (parsedYear == null || parsedYear < 2000 || parsedYear > 2100)
                        )
                    }

                    if (previewDateText != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Gültig ab $previewDateText",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Section: Tägliche Soll-Arbeitszeit
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Tägliche Soll-Arbeitszeit",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Daily presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple("7h 48m", 7, 48),
                            Triple("8h 00m", 8, 0),
                            Triple("7h 00m", 7, 0)
                        ).forEach { (label, h, m) ->
                            val isSelected = parsedDailyH == h && parsedDailyM == m
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    dailyHoursText = h.toString()
                                    dailyMinutesText = m.toString()
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dailyHoursText,
                            onValueChange = { dailyHoursText = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("Stunden") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = dailyHoursText.isNotEmpty() && (parsedDailyH == null || parsedDailyH < 0 || parsedDailyH > 24)
                        )
                        OutlinedTextField(
                            value = dailyMinutesText,
                            onValueChange = { dailyMinutesText = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("Minuten (0–59)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = dailyMinutesText.isNotEmpty() && (parsedDailyM == null || parsedDailyM < 0 || parsedDailyM > 59)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Section: Monatliche Soll-Arbeitszeit
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Monatliche Soll-Arbeitszeit",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Monthly presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple("160h", 160, 0),
                            Triple("154h 26m", 154, 26),
                            Triple("140h", 140, 0)
                        ).forEach { (label, h, m) ->
                            val isSelected = parsedMonthlyH == h && parsedMonthlyM == m
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    monthlyHoursText = h.toString()
                                    monthlyMinutesText = m.toString()
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = monthlyHoursText,
                            onValueChange = { monthlyHoursText = it.filter { c -> c.isDigit() }.take(3) },
                            label = { Text("Stunden") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = monthlyHoursText.isNotEmpty() && (parsedMonthlyH == null || parsedMonthlyH < 0 || parsedMonthlyH > 744)
                        )
                        OutlinedTextField(
                            value = monthlyMinutesText,
                            onValueChange = { monthlyMinutesText = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("Minuten (0–59)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = monthlyMinutesText.isNotEmpty() && (parsedMonthlyM == null || parsedMonthlyM < 0 || parsedMonthlyM > 59)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Section: Arbeitstage
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Arbeitstage",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Weekday quick presets using FilterChip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isMoFr = selectedDays.size == 5 && DEFAULT_WORK_DAYS.all { it in selectedDays }
                        val isMoDo = selectedDays.size == 4 && listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY).all { it in selectedDays }

                        FilterChip(
                            selected = isMoFr,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedDays = DEFAULT_WORK_DAYS
                            },
                            label = { Text("Mo–Fr (5 Tage)", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = isMoDo,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)
                            },
                            label = { Text("Mo–Do (4 Tage)", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Interactive FilterChips for weekdays (clean compact layout without clipping)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        WEEKDAY_OPTIONS.forEach { (day, label) ->
                            val isSelected = day in selectedDays
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedDays = if (isSelected) {
                                        if (selectedDays.size > 1) selectedDays - day else selectedDays
                                    } else {
                                        selectedDays + day
                                    }
                                },
                                label = {
                                    Text(
                                        text = label.take(2),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val m = parsedMonth ?: return@Button
                    val y = parsedYear ?: return@Button
                    val dH = parsedDailyH ?: return@Button
                    val dM = parsedDailyM ?: return@Button
                    val mH = parsedMonthlyH ?: return@Button
                    val mM = parsedMonthlyM ?: return@Button

                    val dailyTotal = dH * 60 + dM
                    val monthlyTotal = mH * 60 + mM
                    if (dailyTotal <= 0 || monthlyTotal <= 0) return@Button

                    val validYm = try {
                        YearMonth.of(y, m)
                    } catch (_: Exception) {
                        return@Button
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
                },
                enabled = isFormValid
            ) {
                Text(if (initialRule != null) "Speichern" else "Hinzufügen")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                }
            ) {
                Text("Abbrechen")
            }
        }
    )
}
