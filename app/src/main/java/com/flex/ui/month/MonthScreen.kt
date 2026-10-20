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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flex.domain.model.DayType
import com.flex.domain.model.PublicHolidays
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.usecase.CheckTimeBlockOverlapUseCase
import com.flex.ui.components.FlexSnackbarHost
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
import java.time.YearMonth
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
    var showMonthFlextimeDetailsSheet by remember { mutableStateOf(false) }

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

                val isPositive = state.differenceMinutesMonth >= 0
                val diffColor = if (isPositive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error

                // Prominent Monthly Flextime Card with robust M3 colors and interactive sheet trigger
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showMonthFlextimeDetailsSheet = true
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isPositive) MaterialTheme.colorScheme.tertiaryContainer
                                        else MaterialTheme.colorScheme.errorContainer
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = null,
                                    tint = if (isPositive) MaterialTheme.colorScheme.onTertiaryContainer
                                           else MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "Gleitzeit Monat",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Details anzeigen",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                val totalFlexMin = state.prognosisFlextime.totalMinutes
                                Text(
                                    "Gesamt-Saldo: ${formatFlextimeBalance(totalFlexMin)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = formatFlextimeBalance(state.differenceMinutesMonth),
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
                        value = formatWorkTime(state.actualWorkedMinutesMonth),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    MetricMiniTile(
                        title = "Angerechnet",
                        value = formatWorkTime(state.workedMinutesMonth),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    MetricMiniTile(
                        title = "Soll (Monat)",
                        value = formatWorkTime(state.targetMinutesMonth),
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
                var selectedFilter by remember { mutableStateOf(MonthEntriesFilter.ALL) }
                val filteredWorkDays = remember(state.workDays, selectedFilter) {
                    MonthEntriesHelper.filterWorkDays(state.workDays, selectedFilter)
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Modern M3 Header with Quick-Stats & Filters
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Title + Compact Summary
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Einträge",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val totalFilteredNetMinutes = if (selectedFilter == MonthEntriesFilter.ALL) {
                                state.actualWorkedMinutesMonth
                            } else {
                                filteredWorkDays.sumOf { state.netMinutesByDate[it.date] ?: 0L }
                            }
                            val summary = MonthEntriesHelper.formatMonthEntriesSummary(
                                count = filteredWorkDays.size,
                                totalNetMinutes = totalFilteredNetMinutes
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Filter Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MonthEntriesFilter.entries.forEach { filter ->
                                val selected = selectedFilter == filter
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedFilter = filter
                                    },
                                    label = {
                                        Text(
                                            text = filter.label,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    leadingIcon = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

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
                    } else if (filteredWorkDays.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyStateView(
                                icon = Icons.Default.CalendarToday,
                                title = "Keine passenden Einträge",
                                description = "Für den Filter „${selectedFilter.label}“ liegen keine Einträge vor."
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(bottom = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredWorkDays, key = { it.id.takeIf { id -> id != 0L } ?: it.date.toEpochDay() }) { workDay ->
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
    if (showMonthFlextimeDetailsSheet) {
        MonthFlextimeDetailsSheet(
            state = state,
            onDismiss = { showMonthFlextimeDetailsSheet = false }
        )
    }

    FlexSnackbarHost(
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
    val haptic = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299f + it.green * 0.587f + it.blue * 0.114f) < 0.5f
    }

    val badgeInfo = MonthEntriesHelper.getDayTypeBadgeInfo(workDay)
    val accentTextColor = MonthEntriesHelper.getAccessibleBadgeTextColor(
        dayType = workDay.dayType,
        isOffice = badgeInfo.isOffice,
        isDark = isDark
    )

    val isWorkType = workDay.dayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS, DayType.BUSINESS_TRIP)
    val workBlocks = remember(workDay.timeBlocks) { workDay.timeBlocks.sortedBy { it.startTime } }

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Prägnantes Icon-Badge für den Tagestyp (42dp x 42dp)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeInfo.accentColor.copy(alpha = if (isDark) 0.20f else 0.12f))
                    .border(
                        1.dp,
                        badgeInfo.accentColor.copy(alpha = if (isDark) 0.40f else 0.25f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeInfo.icon,
                    fontSize = if (MonthEntriesHelper.isCompoundBadgeIcon(badgeInfo.icon)) 13.sp else 20.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Linke Seite: Wochentag fett + formatiertes Datum, Tagestyp-Pill, gestempelte Zeitblöcke
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val (dayPrefix, dateSuffix) = MonthEntriesHelper.formatWorkDayHeaderDate(workDay.date)
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                append(dayPrefix)
                            }
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                append(dateSuffix)
                            }
                        },
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )

                    // Tagestyp-Pill
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = badgeInfo.accentColor.copy(alpha = if (isDark) 0.22f else 0.12f),
                        border = BorderStroke(
                            1.dp,
                            badgeInfo.accentColor.copy(alpha = if (isDark) 0.45f else 0.30f)
                        ),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Text(
                            text = badgeInfo.label,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = accentTextColor
                        )
                    }
                }

                // Optional note
                if (!workDay.note.isNullOrBlank()) {
                    Text(
                        text = workDay.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Gestempelte Zeitblöcke als kompakte abgerundete M3-Pills
                if (isWorkType && workBlocks.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        workBlocks.forEach { block ->
                            val startStr = block.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                            val endStr = block.endTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "jetzt"
                            val locationLabel = if (block.location == WorkLocation.OFFICE) "Büro" else "HO"
                            val locationDotColor = if (block.location == WorkLocation.OFFICE) OfficeColor else HomeOfficeColor
                            val locationTextColor = MonthEntriesHelper.getAccessibleLocationTextColor(
                                location = block.location,
                                isDark = isDark
                            )

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(locationDotColor)
                                    )
                                    Text(
                                        text = "$startStr – $endStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = locationLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = locationTextColor
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
                }
            }

            // Rechte Seite: Große Nettoarbeitszeit, Tages-Differenz Chip, Kumulierter Saldo, Warnung
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Große Nettoarbeitszeit (z. B. 7h 30m)
                val netFormatted = MonthEntriesHelper.formatNetWorkTime(netMinutes)
                if (netFormatted.isNotEmpty()) {
                    Text(
                        text = netFormatted,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }

                // Tages-Differenz als farbiger Chip (tertiary für Plus, error für Minus) mit zweistelligen Minuten
                if (dailyFlextime != null && (isWorkType || workDay.dayType == DayType.FLEX_DAY || dailyFlextime != 0L)) {
                    val isPositive = dailyFlextime >= 0
                    val diffText = MonthEntriesHelper.formatDailyFlextime(dailyFlextime)

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isPositive) MaterialTheme.colorScheme.tertiaryContainer
                                else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = diffText,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isPositive) MaterialTheme.colorScheme.onTertiaryContainer
                                   else MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 1
                        )
                    }
                }

                // Kumulierter Saldo dezent formatiert (Stand: +21h 32m)
                if (flextime != null) {
                    val saldoText = MonthEntriesHelper.formatCumulativeFlextime(flextime)
                    Text(
                        text = saldoText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                // Overlap warning
                if (hasOverlap) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
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
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 1
                            )
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
        color = MaterialTheme.colorScheme.surfaceContainer,
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

private fun formatFlextimeBalance(minutes: Long): String {
    val absM = kotlin.math.abs(minutes)
    val h = absM / 60
    val m = absM % 60
    val sign = if (minutes >= 0L) "+" else "-"
    val mStr = if (m < 10) "0$m" else "$m"
    return "$sign${h}h ${mStr}m"
}

private fun formatWorkTime(minutes: Long): String {
    val absM = kotlin.math.abs(minutes)
    val h = absM / 60
    val m = absM % 60
    val mStr = if (m < 10) "0$m" else "$m"
    return "${h}h ${mStr}m"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthFlextimeDetailsSheet(
    state: MonthUiState,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val monthName = state.yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))

    val baseMin = state.baseFlextimeMinutes
    val diffMin = state.differenceMinutesMonth
    val totalMin = state.prognosisFlextime.totalMinutes
    val isTotalPos = totalMin >= 0L
    val isDiffPos = diffMin >= 0L
    val diffColor = if (isDiffPos) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    val totalColor = if (isTotalPos) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error

    val isPastMonth = state.yearMonth.isBefore(YearMonth.now())
    val isCurrentMonth = state.yearMonth == YearMonth.now()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDiffPos) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.errorContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDiffPos) Icons.AutoMirrored.Filled.TrendingUp
                        else Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = if (isDiffPos) MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Monats-Gleitzeit",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Aufschlüsselung für $monthName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Card 1: Übertrag aus Vormonaten (Basis-Saldo vor Monatsbeginn)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Übertrag aus Vormonaten",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Basis-Saldo vor Monatsbeginn",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val isBasePos = baseMin >= 0L
                    Text(
                        text = formatFlextimeBalance(baseMin),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isBasePos) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                    )
                }
            }

            // Card 2: Monatsergebnis: Geleistete/Angerechnete Stunden vs. Monatssoll = Monats-Differenz
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val cardHeader = when {
                        isPastMonth -> "Monatsergebnis ($monthName)"
                        isCurrentMonth -> "Monatsergebnis bisher ($monthName)"
                        else -> "Monatsplanung ($monthName)"
                    }
                    Text(
                        text = cardHeader,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Side-by-side metric tiles: Geleistet & Angerechnet
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isCurrentMonth) "Geleistet (Netto bisher)" else "Geleistet (Netto)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatWorkTime(state.actualWorkedMinutesMonth),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isCurrentMonth) "Angerechnet (bisher)" else "Angerechnet",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatWorkTime(state.workedMinutesMonth),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Calculation Row: Angerechnet vs. Soll = Differenz
                    val workedText = formatWorkTime(state.workedMinutesMonth)
                    val targetText = if (isCurrentMonth) formatWorkTime(state.targetMinutesToDate) else formatWorkTime(state.targetMinutesMonth)
                    val targetLabel = if (isCurrentMonth) "Soll (bisher)" else "Monatssoll"
                    val diffLabel = if (isCurrentMonth) "Diff. (bisher)" else "Monats-Diff."

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Angerechnet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = workedText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "vs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column {
                            Text(
                                text = targetLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = targetText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "=",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = diffLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatFlextimeBalance(diffMin),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = diffColor
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.8.dp
                    )

                    val diffExplanation = if (isCurrentMonth) {
                        "$workedText (Angerechnet) − $targetText (Soll bisher) = ${formatFlextimeBalance(diffMin)}"
                    } else if (isPastMonth) {
                        "$workedText (Angerechnet) − $targetText (Monatssoll) = ${formatFlextimeBalance(diffMin)}"
                    } else {
                        "Monatssoll: ${formatWorkTime(state.targetMinutesMonth)} · Geplante Abweichung: ${formatFlextimeBalance(diffMin)}"
                    }

                    Text(
                        text = diffExplanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    if (isCurrentMonth) {
                        Text(
                            text = "Monatssoll gesamt: ${formatWorkTime(state.targetMinutesMonth)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Card 3: Prognostizierter Gesamtsaldo zum Monatsende
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, totalColor.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPastMonth) "Gesamtsaldo nach diesem Monat" else "Prognostizierter Gesamtsaldo",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = formatFlextimeBalance(totalMin),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = totalColor
                        )
                    }

                    val remainingDelta = totalMin - (baseMin + diffMin)
                    val formulaText = when {
                        isPastMonth ->
                            "${formatFlextimeBalance(baseMin)} (Übertrag) + (${formatFlextimeBalance(diffMin)}) ($monthName) = ${formatFlextimeBalance(totalMin)}"
                        isCurrentMonth && remainingDelta != 0L ->
                            "${formatFlextimeBalance(baseMin)} (Übertrag) + (${formatFlextimeBalance(diffMin)}) (bisher) + (${formatFlextimeBalance(remainingDelta)}) (Planung) = ${formatFlextimeBalance(totalMin)}"
                        isCurrentMonth ->
                            "${formatFlextimeBalance(baseMin)} (Übertrag) + (${formatFlextimeBalance(diffMin)}) (bisher) = ${formatFlextimeBalance(totalMin)}"
                        else ->
                            "${formatFlextimeBalance(baseMin)} (Übertrag) + (${formatFlextimeBalance(diffMin)}) (Planung $monthName) = ${formatFlextimeBalance(totalMin)}"
                    }

                    Text(
                        text = formulaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

