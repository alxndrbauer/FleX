package com.flex.ui.month

import com.flex.domain.model.DEFAULT_WORK_DAYS
import com.flex.domain.model.getRuleForDate

import android.widget.Toast
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import com.flex.ui.components.EmptyStateView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flex.domain.model.DayType
import com.flex.domain.model.PublicHolidays
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.usecase.CheckTimeBlockOverlapUseCase
import com.flex.ui.components.InfoTooltip
import com.flex.ui.components.OfficeQuotaCard
import com.flex.ui.components.TOOLTIP_OFFICE_QUOTA
import com.flex.ui.components.TOOLTIP_OFFICE_QUOTA_TITLE
import com.flex.ui.components.TOOLTIP_PROGNOSIS
import com.flex.ui.components.TOOLTIP_PROGNOSIS_TITLE
import com.flex.ui.components.diagonalHatch
import com.flex.ui.components.formatTimeInput
import com.flex.ui.components.getDayCellBackgroundColor
import com.flex.ui.theme.BusinessTripColor
import com.flex.ui.theme.FlexDayColor
import com.flex.ui.theme.HomeOfficeColor
import com.flex.ui.theme.OfficeColor
import com.flex.ui.theme.PublicHolidayColor
import com.flex.ui.theme.SaturdayBonusColor
import com.flex.ui.theme.SickDayColor
import com.flex.ui.theme.SpecialVacationColor
import com.flex.ui.theme.VacationColor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Mutable holder for one block's editor state. Properties are snapshot-state so Compose
 * can observe fine-grained changes within each block.
 */
class MutableBlockState(
    startText: TextFieldValue,
    endText: TextFieldValue,
    durationHours: String,
    durationMinutes: String,
    location: WorkLocation
) {
    var startText by mutableStateOf(startText)
    var endText by mutableStateOf(endText)
    var durationHours by mutableStateOf(durationHours)
    var durationMinutes by mutableStateOf(durationMinutes)
    var location by mutableStateOf(location)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthScreen(viewModel: MonthViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { viewModel.exportToUri(it, ExportFormat.CSV, context.contentResolver) }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { viewModel.exportToUri(it, ExportFormat.PDF, context.contentResolver) }
    }

    LaunchedEffect(state.exportMessage) {
        state.exportMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearExportMessage()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            // Month navigation header — export action on the left, navigation symmetric on the right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.onExportClick()
                }) {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = "Exportieren",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = { viewModel.previousMonth() }) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "Vorheriger Monat",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = state.yearMonth.format(
                        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { viewModel.nextMonth() }) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Nächster Monat",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            val pagerState = rememberPagerState { 2 }
            val scope = rememberCoroutineScope()

            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Kalender") }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Einträge") }
                )
            }

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.weight(1f)
            ) { page ->

                if (page == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

            val q = state.prognosisQuota

            // 1. Büro-Quote Card (New Modern Design)
            OfficeQuotaCard(
                officePercent = q.officePercent,
                targetPercent = state.effectiveQuotaPercent,
                isMet = q.quotaMet,
                officeDays = q.officeDays,
                requiredDays = state.effectiveQuotaMinDays,
                officeMinutes = state.officeMinutes,
                requiredOfficeMinutes = state.requiredOfficeMinutes,
                title = "Büro-Präsenz",
                subtitle = "Monatsquote",
                tooltipTitle = TOOLTIP_OFFICE_QUOTA_TITLE,
                tooltipText = TOOLTIP_OFFICE_QUOTA
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Gleitzeit & Arbeitszeit Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Arbeitszeit & Gleitzeit",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        InfoTooltip(title = TOOLTIP_PROGNOSIS_TITLE, text = TOOLTIP_PROGNOSIS)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                val actualHours = state.actualWorkedMinutesMonth / 60
                val actualMinutes = state.actualWorkedMinutesMonth % 60
                val workedHours = state.workedMinutesMonth / 60
                val workedMinutes = state.workedMinutesMonth % 60
                val targetHours = state.targetMinutesMonth / 60
                val targetMinutes = state.targetMinutesMonth % 60
                val sign = if (state.differenceMinutesMonth >= 0) "+" else ""
                val diffHours = kotlin.math.abs(state.differenceMinutesMonth) / 60
                val diffMinutes = kotlin.math.abs(state.differenceMinutesMonth) % 60
                val isPositive = state.differenceMinutesMonth >= 0
                val diffColor = if (isPositive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error

                // Prominent Monthly Flextime Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = diffColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, diffColor.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(diffColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = null,
                                    tint = diffColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    "Gleitzeit Monat",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val totalFlexMin = state.prognosisFlextime.totalMinutes
                                val totalSign = if (totalFlexMin >= 0) "+" else "-"
                                val totH = kotlin.math.abs(totalFlexMin) / 60
                                val totM = kotlin.math.abs(totalFlexMin) % 60
                                Text(
                                    "Gesamt-Saldo: $totalSign${totH}h ${totM}m",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = "$sign${diffHours}h ${diffMinutes}m",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = diffColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Detailed metrics: Geleistet, Angerechnet, Soll
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricMiniTile(
                        title = "Geleistet",
                        value = "${actualHours}h ${actualMinutes}m",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    MetricMiniTile(
                        title = "Angerechnet",
                        value = "${workedHours}h ${workedMinutes}m",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    MetricMiniTile(
                        title = "Soll (Monat)",
                        value = "${targetHours}h ${targetMinutes}m",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // "Planung übernehmen" button
        if (state.hasPlannedDays) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    viewModel.confirmPlannedDays()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text("Planung übernehmen")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val density = LocalDensity.current
        val swipeThreshold = with(density) { 48.dp.toPx() }
        var totalDragOffset by remember { mutableFloatStateOf(0f) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(state.yearMonth) {
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragOffset = 0f },
                        onDragEnd = {
                            if (totalDragOffset > swipeThreshold) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.previousMonth()
                            } else if (totalDragOffset < -swipeThreshold) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.nextMonth()
                            }
                            totalDragOffset = 0f
                        },
                        onDragCancel = {
                            totalDragOffset = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            totalDragOffset += dragAmount
                        }
                    )
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                AnimatedContent(
                    targetState = state.yearMonth,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { width -> width / 2 } + fadeIn(tween(220)))
                                .togetherWith(slideOutHorizontally(tween(200)) { width -> -width / 2 } + fadeOut(tween(160)))
                        } else {
                            (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { width -> -width / 2 } + fadeIn(tween(220)))
                                .togetherWith(slideOutHorizontally(tween(200)) { width -> width / 2 } + fadeOut(tween(160)))
                        }
                    },
                    label = "month_calendar_transition"
                ) { currentMonth ->
                    Column {
                        // Day of week headers
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach { day ->
                                Text(
                                    text = day,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Calendar grid — non-lazy grid (safe inside verticalScroll), max 42 cells
                        val firstDay = currentMonth.atDay(1)
                        val startOffset = (firstDay.dayOfWeek.value - 1)
                        val daysInMonth = currentMonth.lengthOfMonth()
                        val workDayMap = state.workDays.associateBy { it.date }

                        val totalCells = startOffset + daysInMonth
                        val rowCount = (totalCells + 6) / 7
                        val allCells = List(rowCount * 7) { index ->
                            when {
                                index < startOffset -> null
                                index < startOffset + daysInMonth -> currentMonth.atDay(index - startOffset + 1)
                                else -> null
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            allCells.chunked(7).forEach { week ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    week.forEach { date ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            if (date != null) {
                                                val dayWorkDay = workDayMap[date]
                                                val showDailyFlex = dayWorkDay != null && (
                                                    dayWorkDay.dayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS, DayType.BUSINESS_TRIP, DayType.FLEX_DAY)
                                                )
                                                val rule = state.workTimeRules.getRuleForDate(date)
                                                val activeWorkDays = rule?.workDays ?: DEFAULT_WORK_DAYS
                                                val isOffDay = date.dayOfWeek !in activeWorkDays

                                                DayCell(
                                                    date = date,
                                                    workDay = dayWorkDay,
                                                    isToday = date == LocalDate.now(),
                                                    isOffDay = isOffDay,
                                                    hasBreakViolation = state.breakViolationDates.contains(date),
                                                    hasOverlap = state.overlappingDates.contains(date),
                                                    flextime = if (showDailyFlex) state.dailyFlextimeByDate[date] else null,
                                                    onClick = { viewModel.selectDay(date) }
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

        Spacer(modifier = Modifier.height(12.dp))

        // Legend — FlowRow so it wraps on narrow screens
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LegendItem(color = OfficeColor, label = "Büro")
            LegendItem(color = HomeOfficeColor, label = "HO")
            LegendItem(color = BusinessTripColor, label = "Dienstgang / Dienstreise")
            LegendItem(color = VacationColor, label = "Urlaub")
            LegendItem(color = SpecialVacationColor, label = "Sonderurlaub")
            LegendItem(color = FlexDayColor, label = "Gleittag")
            LegendItem(color = SickDayColor, label = "Krank")
            LegendItem(color = PublicHolidayColor, label = "Feiertag")
        }

        Spacer(modifier = Modifier.height(100.dp))

        } // end scrollable Column
            } // end page == 0

            if (page == 1) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Einträge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${state.workDays.size} Einträge",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            if (state.workDays.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateView(
                        icon = Icons.Default.CalendarToday,
                        title = "Keine Einträge",
                        description = "In diesem Monat wurden bisher keine Arbeitstage erfasst."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.workDays.sortedBy { it.date }) { workDay ->
                        WorkDayListItem(
                            workDay = workDay,
                            netMinutes = state.netMinutesByDate[workDay.date] ?: 0,
                            dailyFlextime = state.dailyFlextimeByDate[workDay.date],
                            flextime = state.flextimeByDate[workDay.date],
                            hasOverlap = state.overlappingDates.contains(workDay.date),
                            onClick = { viewModel.selectDay(workDay.date) }
                        )
                    }
                }
            }
                } // end Column for page 1
            } // end page == 1

        } // end HorizontalPager
    }

    // Export format dialog
    if (state.showExportDialog) {
        val yearMonthStr = state.yearMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        AlertDialog(
            onDismissRequest = { viewModel.onExportDismiss() },
            title = { Text("Monat exportieren") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.overlappingDates.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                val datesText = state.overlappingDates.sorted().joinToString(", ") {
                                    it.format(DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN))
                                }
                                Text(
                                    text = "Achtung: Überlappende Zeitblöcke am $datesText. Im Export können Arbeitszeiten fehlerhaft summiert sein.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                    Text("Format wählen:")
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.onExportDismiss()
                            csvLauncher.launch("flex_$yearMonthStr.csv")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CSV")
                    }
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.onExportDismiss()
                            pdfLauncher.launch("flex_$yearMonthStr.pdf")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("PDF")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.onExportDismiss() }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // Edit dialog
    state.editingDay?.let { editDay ->
        val activeRule = state.workTimeRules.getRuleForDate(editDay.date)
        val activeDailyTarget = activeRule?.dailyWorkMinutes ?: state.settings.dailyWorkMinutes
        EditDayDialog(
            workDay = editDay,
            dailyWorkMinutes = activeDailyTarget,
            defaultStartTime = state.settings.defaultStartTime,
            isSpecialVacationBlocked = state.settings.specialVacationPaidOut && state.settings.settingsYear == editDay.date.year,
            onDismiss = { viewModel.clearEditing() },
            onSave = { dayType, note, timeBlocks ->
                viewModel.saveDay(editDay.date, dayType, note, timeBlocks)
            },
            onDelete = if (editDay.id != 0L) {
                { viewModel.deleteDay(editDay) }
            } else null
        )
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
        } // end Box
    } // end Surface
}


internal fun getMonthDayCellColor(
    date: LocalDate,
    workDay: WorkDay?,
    isOffDay: Boolean = false
): Color = getDayCellBackgroundColor(date, workDay, isWeekend = isOffDay)

@Composable
fun DayCell(
    date: LocalDate,
    workDay: WorkDay?,
    isToday: Boolean,
    isOffDay: Boolean = false,
    hasBreakViolation: Boolean = false,
    hasOverlap: Boolean = false,
    flextime: Long? = null,
    onClick: () -> Unit
) {
    val holidayName = PublicHolidays.getHolidayName(date)
    val bgColor = getMonthDayCellColor(date, workDay, isOffDay = isOffDay)

    val hatchColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val borderMod = if (workDay?.isPlanned == true) {
        Modifier.diagonalHatch(hatchColor)
    } else Modifier

    val hasOfficeBlock = workDay?.timeBlocks?.any { it.location == WorkLocation.OFFICE } == true
    val hasHomeOfficeBlock = workDay?.timeBlocks?.any { it.location == WorkLocation.HOME_OFFICE } == true
    val isSplitLocation = hasOfficeBlock && hasHomeOfficeBlock

    val todayBorder = if (isToday) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else null

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = todayBorder,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .then(borderMod)
            .clickable(enabled = holidayName == null, onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.primary
                    else if (holidayName != null) PublicHolidayColor
                    else if (isOffDay) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                if (flextime != null) {
                    val sign = if (flextime > 0) "+" else if (flextime < 0) "-" else ""
                    val absFlex = Math.abs(flextime)
                    val color = if (flextime < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    Text(
                        text = "$sign${absFlex / 60}:${(absFlex % 60).toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 8.sp),
                        color = color
                    )
                }
                if (isSplitLocation) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier
                            .size(width = 16.dp, height = 3.5.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(OfficeColor)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(HomeOfficeColor)
                        )
                    }
                } else if (hasOfficeBlock && !isToday) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 10.dp, height = 3.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(OfficeColor.copy(alpha = 0.85f))
                    )
                } else if (hasHomeOfficeBlock && !isToday) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 10.dp, height = 3.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(HomeOfficeColor.copy(alpha = 0.85f))
                    )
                } else if (isToday) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            if (hasBreakViolation || hasOverlap) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                )
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkDayListItem(
    workDay: WorkDay,
    netMinutes: Long,
    dailyFlextime: Long? = null,
    flextime: Long? = null,
    hasOverlap: Boolean = false,
    onClick: () -> Unit
) {
    val isWorkType = workDay.dayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS, DayType.BUSINESS_TRIP)
    val workBlocks = workDay.timeBlocks.filter { it.endTime != null }

    // Compute office/HO split once for both accentColor and typeLabel
    var officeMin = 0L
    var hoMin = 0L
    if (isWorkType && workBlocks.isNotEmpty()) {
        for (b in workBlocks) {
            val min = java.time.Duration.between(b.startTime, b.endTime!!).toMinutes()
            if (b.location == WorkLocation.OFFICE) officeMin += min else hoMin += min
        }
    }

    val accentColor = when (workDay.dayType) {
        DayType.VACATION -> VacationColor
        DayType.SPECIAL_VACATION -> SpecialVacationColor
        DayType.FLEX_DAY -> FlexDayColor
        DayType.SICK_DAY -> SickDayColor
        DayType.BUSINESS_TRIP -> BusinessTripColor
        else -> when {
            workBlocks.isEmpty() -> if (workDay.location == WorkLocation.OFFICE) OfficeColor else HomeOfficeColor
            officeMin >= hoMin -> OfficeColor
            else -> HomeOfficeColor
        }
    }

    val typeLabel = when (workDay.dayType) {
        DayType.WORK, DayType.SATURDAY_BONUS -> {
            if (workBlocks.isEmpty()) {
                if (workDay.location == WorkLocation.OFFICE) "Büro" else "HO"
            } else {
                when {
                    officeMin > 0 && hoMin > 0 -> "Gemischt"
                    officeMin > 0 -> "Büro"
                    else -> "HO"
                }
            }
        }
        DayType.BUSINESS_TRIP -> "Dienstgang"
        DayType.VACATION -> "Urlaub"
        DayType.SPECIAL_VACATION -> "Sonderurlaub"
        DayType.FLEX_DAY -> "Gleittag"
        DayType.SICK_DAY -> "Krank"
        DayType.OVERTIME_DAY -> "Überstundentag"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Left accent strip indicating day type
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Left Column: Date, type badge, and time blocks
                Column(modifier = Modifier.weight(1f)) {
                    val dayName = workDay.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN)
                    val dateStr = workDay.date.format(DateTimeFormatter.ofPattern("d. MMM"))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "$dayName, $dateStr",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = accentColor.copy(alpha = 0.15f),
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Text(
                                typeLabel,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor
                            )
                        }
                    }

                    // Show individual time blocks for work days
                    if (isWorkType && workBlocks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        workBlocks.forEach { block ->
                            val startStr = block.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                            val endStr = block.endTime!!.format(DateTimeFormatter.ofPattern("HH:mm"))
                            val locationLabel = if (block.location == WorkLocation.OFFICE) "Büro" else "HO"
                            val locationColor =
                                if (block.location == WorkLocation.OFFICE) OfficeColor else HomeOfficeColor
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "$startStr – $endStr",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .clip(CircleShape)
                                        .background(
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.4f
                                            )
                                        )
                                )
                                Text(
                                    locationLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = locationColor.copy(alpha = 0.8f)
                                )
                                if (block.isDuration) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = "Gesamtzeit (Ungerundet)",
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Right Column: Total time, flextime, and overlap warning
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    if (netMinutes > 0) {
                        Text(
                            "${netMinutes / 60}h ${netMinutes % 60}min",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (dailyFlextime != null && (isWorkType || workDay.dayType == DayType.FLEX_DAY || dailyFlextime != 0L)) {
                        Spacer(modifier = Modifier.height(2.dp))
                        val signDaily = if (dailyFlextime > 0) "+" else if (dailyFlextime < 0) "-" else ""
                        val absDaily = Math.abs(dailyFlextime)
                        val colorDaily = if (dailyFlextime < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        Text(
                            "Tag: $signDaily${absDaily / 60}h ${absDaily % 60}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorDaily
                        )
                    }
                    if (flextime != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        val signFlex = if (flextime > 0) "+" else if (flextime < 0) "-" else ""
                        val absFlex = Math.abs(flextime)
                        val colorFlex = if (flextime < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        Text(
                            "Gesamt: $signFlex${absFlex / 60}h ${absFlex % 60}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorFlex
                        )
                    }
                    if (hasOverlap) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    "Überlappung",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditDayDialog(
    workDay: WorkDay,
    dailyWorkMinutes: Int = 426,
    defaultStartTime: java.time.LocalTime = java.time.LocalTime.of(8, 0),
    isSpecialVacationBlocked: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (DayType, String?, List<TimeBlockInput>) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    var dayType by remember { mutableStateOf(workDay.dayType) }
    var note by remember { mutableStateOf(workDay.note ?: "") }

    val dialogState = calculateEditDayDialogState(
        workDay = workDay,
        dailyWorkMinutes = dailyWorkMinutes,
        defaultStartTime = defaultStartTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    )

    var selectedTab by remember { mutableIntStateOf(dialogState.selectedTab) }

    val blocks = remember {
        mutableStateListOf(*dialogState.blocks.map { b ->
            MutableBlockState(
                startText = TextFieldValue(b.startText),
                endText = TextFieldValue(b.endText),
                durationHours = b.durationHours,
                durationMinutes = b.durationMinutes,
                location = b.location
            )
        }.toTypedArray())
    }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(workDay.date.format(DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)))
                if (onDelete != null) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            onDelete()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Tag löschen",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Day type
                Text("Tagestyp", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = dayType == DayType.WORK,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            dayType = DayType.WORK
                        },
                        label = { Text("Arbeitstag") })
                    FilterChip(
                        selected = dayType == DayType.BUSINESS_TRIP,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            dayType = DayType.BUSINESS_TRIP
                        },
                        label = { Text("Dienstgang / Dienstreise") })
                    FilterChip(
                        selected = dayType == DayType.VACATION,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            dayType = DayType.VACATION
                        },
                        label = { Text("Urlaub") })
                    FilterChip(
                        selected = dayType == DayType.SPECIAL_VACATION,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            dayType = DayType.SPECIAL_VACATION
                        },
                        enabled = !isSpecialVacationBlocked,
                        label = { Text("Sonderurlaub") })
                    FilterChip(
                        selected = dayType == DayType.FLEX_DAY,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            dayType = DayType.FLEX_DAY
                        },
                        label = { Text("Gleittag") })
                    FilterChip(
                        selected = dayType == DayType.SATURDAY_BONUS,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            dayType = DayType.SATURDAY_BONUS
                        },
                        label = { Text("Samstag+") })
                    FilterChip(
                        selected = dayType == DayType.SICK_DAY,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            dayType = DayType.SICK_DAY
                        },
                        label = { Text("Krank") })
                }

                // Time entry — only for work day types
                if (dayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS, DayType.BUSINESS_TRIP)) {
                    PrimaryTabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0, onClick = { selectedTab = 0 },
                            text = { Text("Start / Ende") })
                        Tab(
                            selected = selectedTab == 1, onClick = { selectedTab = 1 },
                            text = { Text("Gesamtzeit") })
                    }

                    if (selectedTab == 0) {
                        val overlaps = remember(blocks.map { it.startText.text to it.endText.text }) {
                            val fmt = DateTimeFormatter.ofPattern("HH:mm")
                            val parsedBlocks = blocks.mapIndexedNotNull { index, b ->
                                try {
                                    val start = LocalTime.parse(b.startText.text, fmt)
                                    val end = LocalTime.parse(b.endText.text, fmt)
                                    TimeBlock(id = index.toLong(), startTime = start, endTime = end, location = b.location, isDuration = false)
                                } catch (_: Exception) { null }
                            }
                            CheckTimeBlockOverlapUseCase().findOverlaps(parsedBlocks)
                        }

                        if (overlaps.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    val fmt = DateTimeFormatter.ofPattern("HH:mm")
                                    val firstOverlap = overlaps.first()
                                    Text(
                                        text = "Achtung: Zeitblöcke überlappen sich (${firstOverlap.first.startTime.format(fmt)}–${firstOverlap.first.endTime?.format(fmt)} und ${firstOverlap.second.startTime.format(fmt)}–${firstOverlap.second.endTime?.format(fmt)})!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    // Per-block editors
                    blocks.forEachIndexed { index, block ->
                        if (index > 0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Block ${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (blocks.size > 1) {
                                IconButton(
                                    onClick = { blocks.removeAt(index) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Block löschen",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Location per block
                        if (dayType != DayType.BUSINESS_TRIP) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = block.location == WorkLocation.OFFICE,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        block.location = WorkLocation.OFFICE
                                    },
                                    label = { Text("Büro") }
                                )
                                FilterChip(
                                    selected = block.location == WorkLocation.HOME_OFFICE,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        block.location = WorkLocation.HOME_OFFICE
                                    },
                                    label = { Text("HO") }
                                )
                            }
                        }

                        // Time fields
                        if (selectedTab == 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = block.startText,
                                    onValueChange = { block.startText = formatTimeInput(it) },
                                    label = { Text("Start") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                OutlinedTextField(
                                    value = block.endText,
                                    onValueChange = { block.endText = formatTimeInput(it) },
                                    label = { Text("Ende") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = block.durationHours,
                                    onValueChange = { block.durationHours = it },
                                    label = { Text("Stunden") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                OutlinedTextField(
                                    value = block.durationMinutes,
                                    onValueChange = { block.durationMinutes = it },
                                    label = { Text("Minuten") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }
                        }
                    }

                    // Add block button
                    TextButton(
                        onClick = {
                            val lastLocation = if (dayType == DayType.BUSINESS_TRIP) WorkLocation.OFFICE else (blocks.lastOrNull()?.location ?: WorkLocation.HOME_OFFICE)
                            blocks.add(
                                MutableBlockState(
                                    startText = TextFieldValue(""),
                                    endText = TextFieldValue(""),
                                    durationHours = (dailyWorkMinutes / 60).toString(),
                                    durationMinutes = (dailyWorkMinutes % 60).toString(),
                                    location = lastLocation
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Block hinzufügen")
                    }
                }

                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Notiz") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                val timeBlockInputs = if (dayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS, DayType.BUSINESS_TRIP)) {
                    blocks.mapNotNull { block ->
                        val effectiveLocation = if (dayType == DayType.BUSINESS_TRIP) WorkLocation.OFFICE else block.location
                        if (selectedTab == 0) {
                            try {
                                val start = LocalTime.parse(
                                    block.startText.text,
                                    DateTimeFormatter.ofPattern("HH:mm")
                                )
                                val end = LocalTime.parse(
                                    block.endText.text,
                                    DateTimeFormatter.ofPattern("HH:mm")
                                )
                                TimeBlockInput(start, end, effectiveLocation, isDuration = false)
                            } catch (_: Exception) {
                                null
                            }
                        } else {
                            val h = block.durationHours.toIntOrNull() ?: 0
                            val m = block.durationMinutes.toIntOrNull() ?: 0
                            val total = h * 60 + m
                            if (total > 0) {
                                val start = defaultStartTime
                                TimeBlockInput(
                                    start,
                                    start.plusMinutes(total.toLong()),
                                    effectiveLocation,
                                    isDuration = true
                                )
                            } else null
                        }
                    }
                } else emptyList()
                onSave(dayType, note.ifBlank { null }, timeBlockInputs)
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

@Composable
private fun MetricMiniTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                value,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
