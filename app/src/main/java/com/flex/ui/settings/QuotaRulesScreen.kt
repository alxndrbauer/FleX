package com.flex.ui.settings

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
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flex.domain.model.QuotaRule
import com.flex.ui.components.EmptyStateView
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
    val haptic = LocalHapticFeedback.current

    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<QuotaRule?>(null) }
    var ruleToDelete by remember { mutableStateOf<QuotaRule?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quoten-Zeiträume") },
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
        if (quotaRules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateView(
                    icon = Icons.Default.PieChart,
                    title = "Keine Quoten-Zeiträume",
                    description = "Die Standard-Büroquote aus den Einstellungen gilt dauerhaft.",
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
                items(quotaRules, key = { it.id }) { rule ->
                    QuotaRuleCard(
                        rule = rule,
                        onEdit = {
                            editingRule = rule
                        },
                        onDelete = {
                            ruleToDelete = rule
                            showDeleteConfirmDialog = true
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
        QuotaRuleDialog(
            initialRule = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { rule ->
                viewModel.addQuotaRule(rule)
                showAddDialog = false
            }
        )
    }

    if (editingRule != null) {
        QuotaRuleDialog(
            initialRule = editingRule,
            onDismiss = { editingRule = null },
            onConfirm = { rule ->
                viewModel.addQuotaRule(rule)
                editingRule = null
            }
        )
    }

    if (showDeleteConfirmDialog && ruleToDelete != null) {
        val rule = ruleToDelete!!
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
            title = { Text("Quoten-Regel löschen?") },
            text = {
                Text(
                    "Möchtest du die Quoten-Regel ab $monthName wirklich löschen?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.deleteQuotaRule(rule)
                        showDeleteConfirmDialog = false
                        ruleToDelete = null
                    }
                ) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showDeleteConfirmDialog = false
                        ruleToDelete = null
                    }
                ) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
private fun QuotaRuleCard(
    rule: QuotaRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

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
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PieChart,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
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

            // Badges Row: Prominent Quota Badge & Min Days Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prominent percentage badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "${rule.officeQuotaPercent}% Büro-Quote",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Tagessoll-Pill
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .weight(0.85f)
                        .fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "min. ${rule.officeQuotaMinDays} Tage",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuotaRuleDialog(
    initialRule: QuotaRule? = null,
    onDismiss: () -> Unit,
    onConfirm: (QuotaRule) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val now = YearMonth.now()
    val initialYm = initialRule?.validFrom ?: now
    var yearText by remember { mutableStateOf(initialYm.year.toString()) }
    var monthText by remember { mutableStateOf(initialYm.monthValue.toString()) }
    var percentText by remember { mutableStateOf(initialRule?.officeQuotaPercent?.toString() ?: "50") }
    var daysText by remember { mutableStateOf(initialRule?.officeQuotaMinDays?.toString() ?: "8") }

    val parsedMonth = monthText.toIntOrNull()
    val parsedYear = yearText.toIntOrNull()
    val parsedPercent = percentText.toIntOrNull()
    val parsedDays = daysText.toIntOrNull()

    val isDateValid = parsedMonth != null && parsedMonth in 1..12 && parsedYear != null && parsedYear in 2000..2100
    val isFormValid = isDateValid && parsedPercent != null && parsedPercent in 0..100 && parsedDays != null && parsedDays in 0..31

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
                    imageVector = Icons.Default.PieChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (initialRule != null) "Quoten-Regel bearbeiten" else "Quoten-Regel hinzufügen",
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

                // Section: Büro-Quote (%)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Büro-Quote",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Percentage preset chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(30, 40, 50, 60, 70).forEach { preset ->
                            val isSelected = parsedPercent == preset
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    percentText = preset.toString()
                                },
                                label = {
                                    Text(
                                        text = "$preset%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Percentage Stepper
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val current = percentText.toIntOrNull() ?: 50
                                percentText = (current - 5).coerceAtLeast(0).toString()
                            },
                            enabled = (percentText.toIntOrNull() ?: 50) > 0,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Quote um 5% verringern")
                        }

                        OutlinedTextField(
                            value = percentText,
                            onValueChange = { percentText = it.filter { c -> c.isDigit() }.take(3) },
                            label = { Text("Prozent (%)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
                            isError = percentText.isNotEmpty() && (parsedPercent == null || parsedPercent < 0 || parsedPercent > 100)
                        )

                        FilledTonalIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val current = percentText.toIntOrNull() ?: 50
                                percentText = (current + 5).coerceAtMost(100).toString()
                            },
                            enabled = (percentText.toIntOrNull() ?: 50) < 100,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Quote um 5% erhöhen")
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Section: Mindest-Tage
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Mindest-Bürotage",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Min days Stepper
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val current = daysText.toIntOrNull() ?: 8
                                daysText = (current - 1).coerceAtLeast(0).toString()
                            },
                            enabled = (daysText.toIntOrNull() ?: 8) > 0,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Mindesttage um 1 verringern")
                        }

                        OutlinedTextField(
                            value = daysText,
                            onValueChange = { daysText = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("Min. Tage") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
                            isError = daysText.isNotEmpty() && (parsedDays == null || parsedDays < 0 || parsedDays > 31)
                        )

                        FilledTonalIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val current = daysText.toIntOrNull() ?: 8
                                daysText = (current + 1).coerceAtMost(31).toString()
                            },
                            enabled = (daysText.toIntOrNull() ?: 8) < 31,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Mindesttage um 1 erhöhen")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val y = parsedYear ?: return@Button
                    val m = parsedMonth ?: return@Button
                    val p = parsedPercent ?: return@Button
                    val d = parsedDays ?: return@Button

                    val validYm = try {
                        YearMonth.of(y, m)
                    } catch (_: Exception) {
                        return@Button
                    }

                    onConfirm(
                        QuotaRule(
                            id = initialRule?.id ?: 0L,
                            validFrom = validYm,
                            officeQuotaPercent = p,
                            officeQuotaMinDays = d
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
