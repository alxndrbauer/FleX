package com.flex.ui.planning

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flex.domain.model.DayType
import com.flex.domain.model.PublicHolidays
import com.flex.domain.model.WorkLocation
import com.flex.ui.components.InfoTooltip
import com.flex.ui.components.TOOLTIP_FLEXTIME_PROGNOSIS
import com.flex.ui.components.TOOLTIP_FLEXTIME_PROGNOSIS_TITLE
import com.flex.ui.components.TOOLTIP_QUOTA_PREVIEW
import com.flex.ui.components.TOOLTIP_QUOTA_PREVIEW_TITLE
import com.flex.ui.components.diagonalHatch
import com.flex.ui.theme.FlexDayColor
import com.flex.ui.theme.HomeOfficeColor
import com.flex.ui.theme.OfficeColor
import com.flex.ui.theme.PublicHolidayColor
import com.flex.ui.theme.SaturdayBonusColor
import com.flex.ui.theme.SickDayColor
import com.flex.ui.theme.SpecialVacationColor
import com.flex.ui.theme.VacationColor
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale


@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun PlanningScreen(viewModel: PlanningViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    var showClearAllDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.undoEvent.collect { event ->
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = event.message,
                    actionLabel = "Rückgängig",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) event.undoAction()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Month navigation — above the tabs, always visible
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.previousMonth() }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Zurück")
            }
            Text(
                text = state.yearMonth.format(
                    DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = { viewModel.nextMonth() }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Weiter")
            }
        }

        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text("Planung") }
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                text = { Text("Monate") }
            )
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            if (page == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

        // Plan type selector with color indicators
        item {
            Text("Planungstyp", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PlanType.entries.forEach { type ->
                    val chipColor = when (type) {
                        PlanType.OFFICE -> OfficeColor
                        PlanType.HOME_OFFICE -> HomeOfficeColor
                        PlanType.VACATION -> VacationColor
                        PlanType.SPECIAL_VACATION -> SpecialVacationColor
                        PlanType.FLEX_DAY -> FlexDayColor
                        PlanType.SATURDAY_BONUS -> SaturdayBonusColor
                    }
                    FilterChip(
                        selected = state.selectedPlanType == type,
                        onClick = { viewModel.setSelectedPlanType(type) },
                        label = { Text(type.label) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(chipColor)
                            )
                        }
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

            val totalCells = startOffset + daysInMonth
            val rowCount = (totalCells + 6) / 7
            val allCells = List(rowCount * 7) { index ->
                when {
                    index < startOffset -> null
                    index < startOffset + daysInMonth -> state.yearMonth.atDay(index - startOffset + 1)
                    else -> null
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                allCells.chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        week.forEach { date ->
                            Box(modifier = Modifier.weight(1f)) {
                                if (date != null) {
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
                                                    Modifier.diagonalHatch(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                                else Modifier
                                            )
                                            .combinedClickable(
                                                enabled = !isHoliday,
                                                onClick = {
                                                    when {
                                                        workDay?.isPlanned == true ->
                                                            viewModel.removePlan(date)
                                                        workDay != null ->
                                                            viewModel.openDayEditor(date)
                                                        else ->
                                                            viewModel.planDay(date)
                                                    }
                                                },
                                                onLongClick = { viewModel.openDayEditor(date) }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = date.dayOfMonth.toString(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isHoliday) PublicHolidayColor
                                                else if (isWeekend) MaterialTheme.colorScheme.onSurfaceVariant
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                            if ((workDay?.timeBlocks?.size ?: 0) > 1) {
                                                Text(
                                                    text = "${workDay!!.timeBlocks.size}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = { viewModel.planRemainingAs(PlanType.HOME_OFFICE) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Rest als HO")
                    }
                    FilledTonalButton(
                        onClick = { viewModel.planRemainingAs(PlanType.OFFICE) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Rest als Büro")
                    }
                }
                FilledTonalButton(
                    onClick = { showClearAllDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Alle geplanten Tage löschen")
                }
            }
        }

        // Quota preview — redesigned with two sections like MonthScreen prognosis card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Quoten-Vorschau",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        InfoTooltip(
                            title = TOOLTIP_QUOTA_PREVIEW_TITLE,
                            text = TOOLTIP_QUOTA_PREVIEW
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    val quota = state.quotaStatus
                    val oh = state.officeHours
                    val statusColor =
                        if (quota.quotaMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

                    // Section A: Office hours + status badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "${oh.plannedOfficeHours} Büro",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "von ${oh.requiredOfficeHours} gefordert",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = statusColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                if (quota.quotaMet) "Quote erfüllt" else "nicht erfüllt",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val hoursProgress = if (oh.requiredOfficeMinutes > 0)
                        (oh.plannedOfficeMinutes.toFloat() / oh.requiredOfficeMinutes).coerceIn(0f, 1f)
                    else 0f
                    LinearProgressIndicator(
                        progress = { hoursProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = OfficeColor,
                        trackColor = ProgressIndicatorDefaults.linearTrackColor,
                        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${quota.officeDays} Büro · ${quota.homeOfficeDays} HO · ${"%.0f".format(quota.officePercent)}% (Ziel ${state.settings.officeQuotaPercent}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Section B: Total hours context
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Gesamt: ${oh.plannedTotalHours}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Büro-Anteil: ${"%.1f".format(quota.officePercent)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Flextime preview — Soll-Wert aus Titel in Untertitel
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Gleitzeit-Prognose",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        InfoTooltip(
                            title = TOOLTIP_FLEXTIME_PROGNOSIS_TITLE,
                            text = TOOLTIP_FLEXTIME_PROGNOSIS
                        )
                    }
                    Text(
                        "Soll: ${state.flextimeBalance.formatTarget()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

        // Bottom spacer
        item { Spacer(modifier = Modifier.height(8.dp)) }
                } // end Tab 0 LazyColumn
            }
            if (page == 1) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.monthSummaries) { summary ->
                        MonthSummaryCard(
                            summary = summary,
                            isSelected = summary.yearMonth == state.yearMonth,
                            onClick = {
                                viewModel.navigateToMonth(summary.yearMonth)
                                scope.launch { pagerState.animateScrollToPage(0) }
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        } // end HorizontalPager
    } // end Column

    // Confirm clear all planned days
    if (showClearAllDialog) {
        val plannedCount = state.workDays.count { it.isPlanned }
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Alle geplanten Tage löschen?") },
            text = {
                Text(
                    if (plannedCount == 0) "Es gibt keine geplanten Tage in diesem Monat."
                    else "$plannedCount ${if (plannedCount == 1) "geplanter Tag" else "geplante Tage"} werden entfernt. " +
                         "Tage mit echten Stempeleinträgen bleiben erhalten."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearAllPlanned(); showClearAllDialog = false },
                    enabled = plannedCount > 0
                ) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    // Long-press hour editing dialog
    state.editingDate?.let { date ->
        val existingDay = state.workDays.find { it.date == date }
        val isRealEntry = existingDay != null && !existingDay.isPlanned
        val existingMinutes = existingDay?.timeBlocks?.firstOrNull()?.let { block ->
            block.endTime?.let { end ->
                java.time.Duration.between(block.startTime, end).toMinutes().toInt()
            }
        } ?: state.settings.dailyWorkMinutes

        PlanHoursDialog(
            date = date,
            initialHours = existingMinutes / 60,
            initialMinutes = existingMinutes % 60,
            isRealEntry = isRealEntry,
            onDismiss = { viewModel.closeDayEditor() },
            onDelete = if (isRealEntry) ({ viewModel.deleteWorkDay(date) }) else null,
            onConfirm = { totalMinutes -> viewModel.savePlannedHours(date, totalMinutes) }
        )
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
    } // end Box
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
    val statusColor =
        if (summary.quotaMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

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
                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        if (summary.quotaMet) "✓" else "✗",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
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
                progress = { hoursProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = OfficeColor,
                trackColor = ProgressIndicatorDefaults.linearTrackColor,
                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
            )
        }
    }
}

@Composable
fun PlanHoursDialog(
    date: java.time.LocalDate,
    initialHours: Int,
    initialMinutes: Int,
    isRealEntry: Boolean = false,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isRealEntry) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Echter Eintrag",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Text("Geplante Arbeitszeit", style = MaterialTheme.typography.labelMedium)
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
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                }
                TextButton(onClick = {
                    val h = hours.toIntOrNull() ?: 0
                    val m = minutes.toIntOrNull() ?: 0
                    val total = h * 60 + m
                    if (total > 0) onConfirm(total)
                }) {
                    Text("Speichern")
                }
            }
        },
        dismissButton = {
            if (onDelete != null) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Löschen") }
            } else {
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        }
    )
}
