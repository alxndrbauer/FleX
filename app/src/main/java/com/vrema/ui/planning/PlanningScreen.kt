package com.vrema.ui.planning

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vrema.domain.model.DayType
import com.vrema.domain.model.PublicHolidays
import com.vrema.domain.model.WorkLocation
import com.vrema.ui.theme.FlexDayColor
import com.vrema.ui.theme.HomeOfficeColor
import com.vrema.ui.theme.OfficeColor
import com.vrema.ui.theme.PublicHolidayColor
import com.vrema.ui.theme.SaturdayBonusColor
import com.vrema.ui.theme.SpecialVacationColor
import com.vrema.ui.theme.VacationColor
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun PlanningScreen(viewModel: PlanningViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Month navigation
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.previousMonth() }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Zurück")
                }
                Text(
                    text = state.yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { viewModel.nextMonth() }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Weiter")
                }
            }
        }

        // Plan type selector
        item {
            Text("Planungstyp", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PlanType.entries.forEach { type ->
                    FilterChip(
                        selected = state.selectedPlanType == type,
                        onClick = { viewModel.setSelectedPlanType(type) },
                        label = { Text(type.label) }
                    )
                }
            }
        }

        // Calendar grid
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            val firstDay = state.yearMonth.atDay(1)
            val startOffset = firstDay.dayOfWeek.value - 1
            val daysInMonth = state.yearMonth.lengthOfMonth()
            val workDayMap = state.workDays.associateBy { it.date }

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.height(280.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(startOffset) {
                    Box(modifier = Modifier.aspectRatio(1f))
                }
                items(daysInMonth) { index ->
                    val date = state.yearMonth.atDay(index + 1)
                    val workDay = workDayMap[date]
                    val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
                    val isHoliday = PublicHolidays.isHoliday(date)

                    val bgColor = when {
                        isHoliday -> PublicHolidayColor.copy(alpha = 0.3f)
                        workDay == null -> if (isWeekend) Color.LightGray.copy(alpha = 0.2f) else Color.Transparent
                        workDay.dayType == DayType.VACATION -> VacationColor.copy(alpha = 0.3f)
                        workDay.dayType == DayType.SPECIAL_VACATION -> SpecialVacationColor.copy(alpha = 0.3f)
                        workDay.dayType == DayType.FLEX_DAY -> FlexDayColor.copy(alpha = 0.3f)
                        workDay.dayType == DayType.SATURDAY_BONUS -> SaturdayBonusColor.copy(alpha = 0.3f)
                        workDay.location == WorkLocation.OFFICE -> OfficeColor.copy(alpha = 0.3f)
                        workDay.location == WorkLocation.HOME_OFFICE -> HomeOfficeColor.copy(alpha = 0.3f)
                        else -> Color.Transparent
                    }

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(bgColor)
                            .then(
                                if (workDay?.isPlanned == true)
                                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                else Modifier
                            )
                            .combinedClickable(
                                enabled = !isHoliday,
                                onClick = {
                                    if (workDay?.isPlanned == true) {
                                        viewModel.removePlan(date)
                                    } else {
                                        viewModel.planDay(date)
                                    }
                                },
                                onLongClick = {
                                    viewModel.openDayEditor(date)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isHoliday) PublicHolidayColor
                                else if (isWeekend) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Quick actions
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { viewModel.planRemainingAs(PlanType.HOME_OFFICE) }) {
                    Text("Rest als HO")
                }
                FilledTonalButton(onClick = { viewModel.planRemainingAs(PlanType.OFFICE) }) {
                    Text("Rest als Büro")
                }
            }
        }

        // Quota preview with office hours
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Quoten-Vorschau", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    val quota = state.quotaStatus
                    val oh = state.officeHours

                    // Office hours: planned vs required
                    Text("Büro-Stunden", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Geplant: ${oh.plannedOfficeHours}  |  Benötigt: ${oh.requiredOfficeHours} (${oh.plannedTotalHours} gesamt)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val hoursProgress = if (oh.requiredOfficeMinutes > 0)
                        (oh.plannedOfficeMinutes.toFloat() / oh.requiredOfficeMinutes).coerceIn(0f, 1.5f)
                    else 0f
                    LinearProgressIndicator(
                        progress = hoursProgress.coerceAtMost(1f),
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Days + percentage
                    Text("Büro-Tage: ${quota.officeDays} | HO-Tage: ${quota.homeOfficeDays}",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("Büro-Anteil: ${"%.1f".format(quota.officePercent)}% (Ziel: ${state.settings.officeQuotaPercent}%)",
                        style = MaterialTheme.typography.bodyMedium)

                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = (quota.officePercent / state.settings.officeQuotaPercent.toDouble()).toFloat().coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val statusText = if (quota.quotaMet) "Quote erfüllt" else "Quote nicht erfüllt"
                    val statusColor = if (quota.quotaMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    Text(statusText, color = statusColor, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Flextime preview
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Gleitzeit-Prognose (Soll: ${state.flextimeBalance.formatTarget()})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.flextimeBalance.formatDisplay(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (state.flextimeBalance.isPositive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Multi-month overview
        item {
            Text(
                "Monatsübersicht",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(state.monthSummaries) { summary ->
            MonthSummaryCard(
                summary = summary,
                isSelected = summary.yearMonth == state.yearMonth,
                onClick = {
                    viewModel.navigateToMonth(summary.yearMonth)
                }
            )
        }

        // Bottom spacer
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }

    // Long-press hour editing dialog
    state.editingDate?.let { date ->
        val existingDay = state.workDays.find { it.date == date }
        val existingMinutes = existingDay?.timeBlocks?.firstOrNull()?.let { block ->
            block.endTime?.let { end ->
                java.time.Duration.between(block.startTime, end).toMinutes().toInt()
            }
        } ?: state.settings.dailyWorkMinutes

        PlanHoursDialog(
            date = date,
            initialHours = existingMinutes / 60,
            initialMinutes = existingMinutes % 60,
            onDismiss = { viewModel.closeDayEditor() },
            onConfirm = { totalMinutes -> viewModel.savePlannedHours(date, totalMinutes) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthSummaryCard(
    summary: MonthSummary,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val monthName = summary.yearMonth.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)
    val year = summary.yearMonth.year
    val oh = summary.officeHours

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (isSelected) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$monthName $year",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                // Quota status indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (summary.quotaMet) Color(0xFF4CAF50)
                            else Color(0xFFFF5722)
                        )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Büro: ${summary.officeDays}  HO: ${summary.homeOfficeDays}  Urlaub: ${summary.vacationDays}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (summary.plannedDays > 0) {
                    Text(
                        "${summary.plannedDays} geplant",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Office hours detail
            Text(
                "Büro-Std.: ${oh.plannedOfficeHours} / ${oh.requiredOfficeHours}",
                style = MaterialTheme.typography.bodySmall,
                color = if (oh.isMet) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )

            val hoursProgress = if (oh.requiredOfficeMinutes > 0)
                (oh.plannedOfficeMinutes.toFloat() / oh.requiredOfficeMinutes).coerceIn(0f, 1f)
            else 0f
            LinearProgressIndicator(
                progress = hoursProgress,
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
        }
    }
}

@Composable
fun PlanHoursDialog(
    date: java.time.LocalDate,
    initialHours: Int,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var hours by remember { mutableStateOf(initialHours.toString()) }
    var minutes by remember { mutableStateOf(initialMinutes.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(date.format(DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)))
        },
        text = {
            Column {
                Text("Geplante Arbeitszeit", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it },
                        label = { Text("Std.") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it },
                        label = { Text("Min.") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val h = hours.toIntOrNull() ?: 0
                val m = minutes.toIntOrNull() ?: 0
                val total = h * 60 + m
                if (total > 0) onConfirm(total)
            }) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
