package com.flex.ui.home

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import com.flex.ui.navigation.LocalFloatingNavVisible
import androidx.compose.ui.graphics.graphicsLayer
import com.flex.ui.home.components.CircularWorkGauge
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flex.R
import com.flex.domain.model.BreakViolationType
import com.flex.domain.model.DayType
import com.flex.domain.model.ProjectedEndTime
import com.flex.domain.model.PublicHolidays
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkLocation
import com.flex.domain.usecase.CheckTimeBlockOverlapUseCase
import com.flex.ui.yearchange.YearChangeDialog
import com.flex.ui.yearchange.YearChangeViewModel
import com.flex.ui.components.EmptyStateView
import com.flex.ui.components.InfoTooltip
import com.flex.ui.components.OfficeQuotaCard
import com.flex.ui.components.TOOLTIP_FLEXTIME
import com.flex.ui.components.TOOLTIP_FLEXTIME_TITLE
import com.flex.ui.components.TOOLTIP_OFFICE_QUOTA
import com.flex.ui.components.TOOLTIP_OFFICE_QUOTA_TITLE
import com.flex.ui.components.formatTimeInput
import com.flex.ui.theme.BusinessTripColor
import com.flex.ui.theme.FlexDayColor
import com.flex.ui.theme.HomeOfficeColor
import com.flex.ui.theme.OfficeColor
import com.flex.ui.theme.OvertimeDayColor
import com.flex.ui.theme.PublicHolidayColor
import com.flex.ui.theme.SaturdayBonusColor
import com.flex.ui.theme.SickDayColor
import com.flex.ui.theme.SpecialVacationColor
import com.flex.ui.theme.VacationColor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAbout: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    yearChangeViewModel: YearChangeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val remaining by viewModel.remainingMinutes.collectAsState()
    val yearChangeState by yearChangeViewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    var showManualEntry by remember { mutableStateOf(false) }
    var editingBlock by remember { mutableStateOf<TimeBlock?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

    LaunchedEffect(viewModel) {
        viewModel.whatsNewEvent.collect { versionName ->
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Aktualisiert auf v$versionName",
                    actionLabel = "Was ist neu?",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) onNavigateToAbout()
            }
        }
    }

    val isToday = state.selectedDate == state.today
    val holidayName = PublicHolidays.getHolidayName(state.selectedDate)
    val isWorkDay = state.selectedDayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS, DayType.BUSINESS_TRIP)

    val isNavVisible = LocalFloatingNavVisible.current
    val fabBottomPadding by animateDpAsState(
        targetValue = if (isNavVisible) 76.dp else 16.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "fab_bottom_padding"
    )

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (isWorkDay && holidayName == null && isToday) {
                val fabContainerColor by animateColorAsState(
                    targetValue = if (state.isClockRunning) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                    animationSpec = tween(300),
                    label = "fab_container_color"
                )
                val fabContentColor by animateColorAsState(
                    targetValue = if (state.isClockRunning) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    animationSpec = tween(300),
                    label = "fab_content_color"
                )
                val fabScale by animateFloatAsState(
                    targetValue = if (state.isClockRunning) 1.04f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "fab_scale"
                )

                ExtendedFloatingActionButton(
                    onClick = {
                        if (state.isClockRunning) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.clockOut()
                        } else {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            viewModel.clockIn()
                        }
                    },
                    icon = {
                        Crossfade(
                            targetState = state.isClockRunning,
                            animationSpec = tween(250),
                            label = "fab_icon_crossfade"
                        ) { isRunning ->
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    text = {
                        AnimatedContent(
                            targetState = state.isClockRunning,
                            transitionSpec = {
                                (fadeIn(tween(200)) + slideInVertically { it / 2 })
                                    .togetherWith(fadeOut(tween(150)) + slideOutVertically { -it / 2 })
                            },
                            label = "fab_text_animation"
                        ) { isRunning ->
                            Text(
                                text = if (isRunning) "Ausstempeln" else "Einstempeln",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    containerColor = fabContainerColor,
                    contentColor = fabContentColor,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = fabBottomPadding)
                        .graphicsLayer {
                            scaleX = fabScale
                            scaleY = fabScale
                        }
                )
            }
        }
    ) { innerPadding ->
        var showDatePicker by remember { mutableStateOf(false) }

        if (showDatePicker) {
            val initialMillis = state.selectedDate
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            viewModel.navigateToDate(
                                Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                            )
                        }
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // Day navigation header — fixed at the top, consistent with Month & Planning
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.goToPreviousDay() }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Vorheriger Tag")
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showDatePicker = true }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isToday) "Heute" else state.selectedDate.format(DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN)),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = state.selectedDate.format(DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (!isToday) {
                        Surface(
                            onClick = { viewModel.goToToday() },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Today,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "Zurück zu Heute",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = { viewModel.goToNextDay() }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Nächster Tag")
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = 120.dp
                )
            ) {
                // Permission warning card
                if (state.permissionIssues.isNotEmpty()) {
                    item {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Berechtigungen fehlen",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        state.permissionIssues.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                TextButton(onClick = onNavigateToSettings) {
                                    Text(
                                        "Einstellungen",
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // Hero card
                item {
                    HeroCard(
                        state = state,
                        remaining = remaining,
                        isToday = isToday,
                        onLocationChange = { viewModel.setLocation(it) },
                        onSelectDayType = { viewModel.saveDayType(it) },
                        onDeleteDay = { viewModel.deleteDay() },
                        onManualEntryClick = { showManualEntry = true },
                        onClockToggle = {
                            if (state.isClockRunning) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.clockOut()
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                viewModel.clockIn()
                            }
                        }
                    )
                }

            // Holiday card
            if (holidayName != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = PublicHolidayColor.copy(alpha = 0.15f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Feiertag",
                                style = MaterialTheme.typography.labelMedium,
                                color = PublicHolidayColor
                            )
                            Text(
                                text = holidayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PublicHolidayColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "An Feiertagen sind keine Einträge möglich.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Compact quota card
            item {
                CompactQuotaCard(state = state)
            }

            if (holidayName == null) {


                // Manuell button (when time blocks exist)
                if (isWorkDay && state.timeBlocks.isNotEmpty()) {
                    item {
                        OutlinedButton(
                            onClick = { showManualEntry = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Manuell")
                        }
                    }
                }
            }

            // Time blocks list
            if (state.timeBlocks.isNotEmpty()) {
                item {
                    Text("Zeitblöcke", style = MaterialTheme.typography.labelLarge)
                }

                item {
                    Column {
                        state.timeBlocks.forEachIndexed { index, block ->
                            TimelineBlockItem(
                                block = block,
                                isFirst = index == 0,
                                isLast = index == state.timeBlocks.lastIndex,
                                isWorkDayPlanned = state.workDay?.isPlanned == true,
                                onEdit = { editingBlock = it },
                                onDelete = { viewModel.deleteTimeBlock(it) },
                                onToggleLocation = { viewModel.toggleTimeBlockLocation(it) }
                            )
                        }
                    }
                }
            } else if (isWorkDay && holidayName == null) {
                item {
                    EmptyStateView(
                        icon = Icons.Default.Schedule,
                        title = "Keine Zeitblöcke vorhanden",
                        description = if (isToday) "Starte die Zeiterfassung mit dem Stempel-Button oder trage Zeiten manuell nach."
                            else "Für diesen Tag wurden noch keine Arbeitszeiten erfasst.",
                        actionLabel = "Zeit manuell eintragen",
                        onAction = { showManualEntry = true }
                    )
                }
            }
        }
    }
}

    // Year change dialog (automatic on new year)
    if (yearChangeState.showDialog) {
        YearChangeDialog(
            state = yearChangeState,
            onDismiss = { yearChangeViewModel.dismiss() },
            onConfirm = { carryOver, annual ->
                yearChangeViewModel.applyYearChange(carryOver, annual)
            }
        )
    }

    // Manual entry dialog
    if (showManualEntry) {
        val isBusinessTrip = state.selectedDayType == DayType.BUSINESS_TRIP
        val defaultMinutes = if (state.dailyTargetMinutes > 0) state.dailyTargetMinutes else state.settings.dailyWorkMinutes
        ManualTimeEntryDialog(
            dailyWorkMinutes = defaultMinutes,
            selectedLocation = if (isBusinessTrip) WorkLocation.OFFICE else state.selectedLocation,
            defaultStartTime = state.settings.defaultStartTime,
            existingBlocks = state.timeBlocks,
            isBusinessTrip = isBusinessTrip,
            onDismiss = { showManualEntry = false },
            onConfirmStartEnd = { start, end, location ->
                viewModel.saveManualEntry(start, end, if (isBusinessTrip) WorkLocation.OFFICE else location)
                showManualEntry = false
            },
            onConfirmDuration = { totalMinutes, location ->
                viewModel.saveDurationEntry(totalMinutes, if (isBusinessTrip) WorkLocation.OFFICE else location)
                showManualEntry = false
            }
        )
    }

    // Edit time block dialog
    editingBlock?.let { block ->
        val isBusinessTrip = state.selectedDayType == DayType.BUSINESS_TRIP
        EditTimeBlockDialog(
            block = block,
            existingBlocks = state.timeBlocks,
            isBusinessTrip = isBusinessTrip,
            onDismiss = { editingBlock = null },
            onSave = { startTime, endTime, location, isDuration ->
                viewModel.updateTimeBlock(block, startTime, endTime, if (isBusinessTrip) WorkLocation.OFFICE else location, isDuration)
                editingBlock = null
            },
            onDelete = {
                viewModel.deleteTimeBlock(block)
                editingBlock = null
            },
            onBook = if (state.workDay?.isPlanned == true) ({ startTime, endTime, location, isDuration ->
                viewModel.bookTimeBlock(block, startTime, endTime, if (isBusinessTrip) WorkLocation.OFFICE else location, isDuration)
                editingBlock = null
            }) else null
        )
    }
}

@Composable
private fun HeroCard(
    state: HomeUiState,
    remaining: Int?,
    isToday: Boolean,
    onLocationChange: (WorkLocation) -> Unit = {},
    onSelectDayType: (DayType) -> Unit = {},
    onDeleteDay: () -> Unit = {},
    onManualEntryClick: () -> Unit = {},
    onClockToggle: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val rawDailyProgress = if (state.dailyTargetMinutes > 0)
        (state.dayWorkTime.netMinutes.toFloat() / state.dailyTargetMinutes).coerceAtLeast(0f)
    else 0f
    val overtimeMinutes = if (state.dailyTargetMinutes > 0 && state.dayWorkTime.netMinutes > state.dailyTargetMinutes) {
        (state.dayWorkTime.netMinutes - state.dailyTargetMinutes).toInt()
    } else 0

    val hours = state.dayWorkTime.netMinutes / 60
    val mins = state.dayWorkTime.netMinutes % 60
    val netTimeText = if (hours > 0) "${hours}h ${mins}m" else "${mins}min"

    val dailyGoalReached = state.dailyTargetMinutes > 0 && state.dayWorkTime.netMinutes >= state.dailyTargetMinutes
    val statusLabel = when {
        state.isClockRunning -> "Läuft"
        dailyGoalReached -> "Fertig"
        state.timeBlocks.isNotEmpty() -> "Pausiert"
        else -> null
    }
    val statusColor = when {
        state.isClockRunning -> MaterialTheme.colorScheme.primary
        remaining == 0 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val dayTypes = listOf(
        DayType.WORK             to Triple("Arbeitstag",    Icons.Default.Work,          MaterialTheme.colorScheme.primary),
        DayType.BUSINESS_TRIP    to Triple("Dienstgang / Dienstreise", Icons.Default.Commute, BusinessTripColor),
        DayType.VACATION         to Triple("Urlaub",         Icons.Default.BeachAccess,   VacationColor),
        DayType.SPECIAL_VACATION to Triple("Sonderurlaub",   Icons.Default.Star,          SpecialVacationColor),
        DayType.FLEX_DAY         to Triple("Gleittag",       Icons.Default.Schedule,      FlexDayColor),
        DayType.SATURDAY_BONUS   to Triple("Samstag+",       Icons.Default.Timer,         SaturdayBonusColor),
        DayType.SICK_DAY         to Triple("Krank",          Icons.Default.LocalHospital, SickDayColor),
        DayType.OVERTIME_DAY     to Triple("Überstunden",    Icons.Default.ArrowDownward, OvertimeDayColor),
    )
    val currentDayTypeEntry = dayTypes.find { it.first == state.selectedDayType }
    val (currentDayTypeLabel, currentDayTypeIcon, currentDayTypeColor) = currentDayTypeEntry?.second
        ?: Triple("Arbeitstag", Icons.Default.Work, MaterialTheme.colorScheme.primary)

    var showDayTypeSheet by remember { mutableStateOf(false) }

    val isPaidOut = state.settings.specialVacationPaidOut && state.settings.settingsYear == state.selectedDate.year
    val isAfterOct31 = state.selectedDate.isAfter(LocalDate.of(state.selectedDate.year, 10, 31))
    val isSpecialVacationBlocked = isPaidOut || isAfterOct31

    if (showDayTypeSheet) {
        AlertDialog(
            onDismissRequest = { showDayTypeSheet = false },
            title = {
                Text(
                    "Tagestyp auswählen",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    dayTypes.forEach { (type, info) ->
                        val (label, icon, color) = info
                        val isSelected = state.selectedDayType == type
                        val isBlocked = type == DayType.SPECIAL_VACATION && isSpecialVacationBlocked

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
                            border = if (isSelected) BorderStroke(1.5.dp, color) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !isBlocked) {
                                    onSelectDayType(type)
                                    showDayTypeSheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isBlocked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else color,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isBlocked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isBlocked) {
                                        Text(
                                            text = if (isPaidOut) "bereits ausgezahlt" else "nach dem 31.10. nicht mehr wählbar",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDayTypeSheet = false }) {
                    Text("Schließen")
                }
            },
            dismissButton = {
                if (state.workDay != null && state.workDay.dayType != DayType.WORK) {
                    TextButton(
                        onClick = {
                            onDeleteDay()
                            showDayTypeSheet = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Eintrag löschen")
                    }
                }
            }
        )
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            val tH = state.dailyTargetMinutes / 60
            val tM = state.dailyTargetMinutes % 60
            val targetText = "${tH}h ${tM}min"
            val prognosisText = if (state.isClockRunning && isToday && (remaining == null || remaining > 0)) {
                state.projectedEndTime?.formatCompact()
            } else null

            // Google Health style 2-column layout: Circle on left, 3 cards on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left: Circular progress gauge (Nettozeit + Soll + Feierabend inside)
                CircularWorkGauge(
                    netTimeText = netTimeText,
                    targetText = targetText,
                    progress = rawDailyProgress,
                    isClockRunning = state.isClockRunning,
                    dailyGoalReached = dailyGoalReached,
                    statusLabel = statusLabel,
                    statusColor = statusColor,
                    projectedEndTime = prognosisText,
                    overtimeMinutes = overtimeMinutes,
                    sizeDp = 136
                )

                // Right: 3 compact metric cards
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Card 1: Gleitzeit (Saldo)
                    val liveDelta = if (isToday) state.liveFlextimeDelta else 0L
                    val balanceMin = (state.liveFlextimeBalance?.totalMinutes
                        ?: state.flextimeBalance.totalMinutes) + liveDelta
                    val bH = kotlin.math.abs(balanceMin) / 60
                    val bM = kotlin.math.abs(balanceMin) % 60
                    val bSign = if (balanceMin >= 0L) "+" else "-"
                    val balanceText = "$bSign${bH}h ${bM}m"
                    val isPos = balanceMin >= 0L

                    HeroMetricCard(
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        iconTint = if (isPos) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        iconBgColor = (if (isPos) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error).copy(alpha = 0.16f),
                        title = "Gleitzeit",
                        value = balanceText,
                        valueColor = if (isPos) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f)
                    )

                    // Card 2: Pause
                    val breakMin = state.dayWorkTime.breakMinutes
                    val breakText = if (breakMin > 0) "${breakMin} min" else "–"
                    val breakSubtitle = when {
                        state.breakCheckResult.violations.isNotEmpty() -> "Pflicht!"
                        state.dayWorkTime.netMinutes >= 360 && breakMin < 30 -> "30m nötig"
                        breakMin >= 30 -> "OK ✓"
                        else -> null
                    }

                    HeroMetricCard(
                        icon = Icons.Default.Coffee,
                        iconTint = MaterialTheme.colorScheme.primary,
                        iconBgColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        title = "Pause",
                        value = breakText,
                        subtitle = breakSubtitle,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                    )

                    // Card 3: Verbleibend
                    val remainingText = when {
                        remaining == 0 -> "Erreicht ✓"
                        remaining != null -> {
                            val rH = remaining / 60
                            val rM = remaining % 60
                            if (rH > 0) "${rH}h ${rM}m" else "${rM}min"
                        }
                        else -> "–"
                    }

                    HeroMetricCard(
                        icon = if (remaining == 0) Icons.Default.CheckCircle else Icons.Default.HourglassTop,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        iconBgColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                        title = "Verbleibend",
                        value = remainingText,
                        valueColor = if (remaining == 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
                    )
                }
            }

            // Bottom quick action pill buttons (Google Health style)
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isWorkDay = state.selectedDayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS, DayType.BUSINESS_TRIP)
                val holidayName = PublicHolidays.getHolidayName(state.selectedDate)

                // Eintragen
                FilledTonalButton(
                    onClick = onManualEntryClick,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Eintragen", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }

                // Starten / Stoppen
                if (isToday && isWorkDay && holidayName == null) {
                    FilledTonalButton(
                        onClick = onClockToggle,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (state.isClockRunning) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (state.isClockRunning) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (state.isClockRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (state.isClockRunning) "Stoppen" else "Starten",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Location toggle button (Office / Home-Office)
                val isOffice = state.selectedLocation == WorkLocation.OFFICE
                FilledTonalIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onLocationChange(if (isOffice) WorkLocation.HOME_OFFICE else WorkLocation.OFFICE)
                    },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = (if (isOffice) OfficeColor else HomeOfficeColor).copy(alpha = 0.16f),
                        contentColor = if (isOffice) OfficeColor else HomeOfficeColor
                    ),
                    modifier = Modifier.size(38.dp)
                ) {
                    Crossfade(
                        targetState = isOffice,
                        animationSpec = tween(200),
                        label = "loc_switcher_crossfade"
                    ) { officeSelected ->
                        Icon(
                            imageVector = if (officeSelected) Icons.Default.Business else Icons.Default.Home,
                            contentDescription = if (officeSelected) "Arbeitsort: Büro (tippen für Home-Office)" else "Arbeitsort: Home-Office (tippen für Büro)",
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                // Second small button: Tagestyp button
                FilledTonalIconButton(
                    onClick = { showDayTypeSheet = true },
                    shape = CircleShape,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = currentDayTypeIcon,
                        contentDescription = "Tagestyp wählen",
                        tint = currentDayTypeColor,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            if (state.dayWorkTime.exceedsMaxHours) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Achtung: Max. 10h Arbeitszeit überschritten!",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            val breakViolations = state.breakCheckResult.violations
            if (breakViolations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                breakViolations.forEach { violation ->
                    val text = when (violation.type) {
                        BreakViolationType.CONTINUOUS_WORK_EXCEEDS_6H ->
                            "⚠ Über 6h ohne Pause (${violation.continuousWorkMinutes / 60}h ${violation.continuousWorkMinutes % 60}min)"
                        BreakViolationType.INSUFFICIENT_TOTAL_BREAK ->
                            "⚠ Pause zu kurz: ${violation.actualBreakMinutes}min / 30min (§4 ArbZG)"
                        BreakViolationType.INSUFFICIENT_TOTAL_BREAK_9H ->
                            "⚠ Pause zu kurz: ${violation.actualBreakMinutes}min / 45min (§4 ArbZG)"
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (state.hasTimeBlockOverlap) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⚠ Achtung: Zeitblöcke überlappen sich!",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HeroMetricCard(
    icon: ImageVector,
    iconTint: Color,
    iconBgColor: Color,
    title: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    containerColor: Color,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = valueColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (subtitle == "Pflicht!") MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactQuotaCard(state: HomeUiState) {
    val quota = state.quotaStatus

    // Today is excluded from quota calc when it has no completed blocks yet (only running block).
    // Add a +1 live bonus day when clocked in as OFFICE before any clock-out.
    val todayHasNoCompletedBlock = state.timeBlocks.none { it.endTime != null }
    val liveOfficeDelta = if (state.liveFlextimeDelta > 0 && state.selectedLocation == WorkLocation.OFFICE)
        state.liveFlextimeDelta else 0L
    val liveOfficeDayBonus = if (liveOfficeDelta > 0 && todayHasNoCompletedBlock) 1 else 0
    val liveOfficeMin = state.officeMinutes + liveOfficeDelta
    val fixedTarget = if (state.effectiveQuotaPercent > 0)
        state.requiredOfficeMinutes * 100.0 / state.effectiveQuotaPercent else 0.0
    val livePercent = if (fixedTarget > 0) liveOfficeMin.toDouble() / fixedTarget * 100 else 0.0

    val currentPct = if (liveOfficeDelta > 0) livePercent else quota.officePercent
    val isMet = currentPct >= state.effectiveQuotaPercent || quota.quotaMet
    val displayDays = quota.officeDays + liveOfficeDayBonus
    val requiredDays = state.effectiveQuotaMinDays
    val currentOfficeMinutes = if (liveOfficeDelta > 0) liveOfficeMin else state.officeMinutes.toLong()

    OfficeQuotaCard(
        officePercent = currentPct,
        targetPercent = state.effectiveQuotaPercent,
        isMet = isMet,
        officeDays = displayDays,
        requiredDays = requiredDays,
        officeMinutes = currentOfficeMinutes,
        requiredOfficeMinutes = state.requiredOfficeMinutes,
        isLive = liveOfficeDelta > 0
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TimelineBlockItem(
    block: TimeBlock,
    isFirst: Boolean,
    isLast: Boolean,
    isWorkDayPlanned: Boolean = false,
    onEdit: (TimeBlock) -> Unit,
    onDelete: (TimeBlock) -> Unit,
    onToggleLocation: (TimeBlock) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val locationColor = when (block.location) {
        WorkLocation.OFFICE -> OfficeColor
        WorkLocation.HOME_OFFICE -> HomeOfficeColor
    }
    val locationLabel = when (block.location) {
        WorkLocation.OFFICE -> "Büro"
        WorkLocation.HOME_OFFICE -> "Home-Office"
    }
    val startStr = block.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    val endStr = block.endTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "laufend…"
    val dotColor = locationColor
    val lineColor = locationColor.copy(alpha = 0.3f)

    Column {
        // Start cap: horizontal tick above first item
        if (isFirst) {
            Row {
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(12.dp)
                        .drawBehind {
                            val cx = size.width / 2
                            val capHalf = 5.dp.toPx()
                            // Vertical line from cap to bottom
                            drawLine(
                                color = lineColor,
                                start = Offset(cx, 4.dp.toPx()),
                                end = Offset(cx, size.height),
                                strokeWidth = 2.dp.toPx()
                            )
                            // Horizontal cap
                            drawLine(
                                color = lineColor,
                                start = Offset(cx - capHalf, 4.dp.toPx()),
                                end = Offset(cx + capHalf, 4.dp.toPx()),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                )
            }
        }

        // Card row: dot + connecting line drawn on the Row canvas via drawBehind
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val cx = 10.dp.toPx()
                    val dotRadius = 5.dp.toPx()
                    val dotY = size.height / 2
                    // Line from top to dot
                    drawLine(
                        color = lineColor,
                        start = Offset(cx, 0f),
                        end = Offset(cx, dotY - dotRadius),
                        strokeWidth = 2.dp.toPx()
                    )
                    // Line from dot to bottom
                    drawLine(
                        color = lineColor,
                        start = Offset(cx, dotY + dotRadius),
                        end = Offset(cx, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(cx, dotY)
                    )
                }
                .combinedClickable(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleLocation(block)
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEdit(block)
                    }
                ),
            verticalAlignment = Alignment.Top
        ) {
            Spacer(modifier = Modifier.width(20.dp))
            Spacer(modifier = Modifier.width(8.dp))

            // Content card
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = locationColor.copy(alpha = 0.08f)
                )
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
                            text = "$startStr – $endStr",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = locationColor.copy(alpha = 0.15f),
                                modifier = Modifier.wrapContentSize()
                            ) {
                                Text(
                                    text = locationLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = locationColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (block.isDuration) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.wrapContentSize()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = "Gesamtzeit",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                            if (isWorkDayPlanned) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier.wrapContentSize()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = "Geplant",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                    IconButton(onClick = { onDelete(block) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Löschen",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Gap connector (between items) or end cap (last item)
        Row {
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(16.dp)
                    .drawBehind {
                        val cx = size.width / 2
                        if (!isLast) {
                            // Connector line through the gap
                            drawLine(
                                color = lineColor,
                                start = Offset(cx, 0f),
                                end = Offset(cx, size.height),
                                strokeWidth = 2.dp.toPx()
                            )
                        } else {
                            // End cap: short line + horizontal tick
                            val capHalf = 5.dp.toPx()
                            drawLine(
                                color = lineColor,
                                start = Offset(cx, 0f),
                                end = Offset(cx, size.height * 0.7f),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawLine(
                                color = lineColor,
                                start = Offset(cx - capHalf, size.height * 0.7f),
                                end = Offset(cx + capHalf, size.height * 0.7f),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
            )
        }
    }
}

@Composable
fun ManualTimeEntryDialog(
    dailyWorkMinutes: Int = 426,
    selectedLocation: WorkLocation = WorkLocation.OFFICE,
    defaultStartTime: LocalTime = LocalTime.of(8, 0),
    existingBlocks: List<TimeBlock> = emptyList(),
    isBusinessTrip: Boolean = false,
    onDismiss: () -> Unit,
    onConfirmStartEnd: (LocalTime, LocalTime, WorkLocation) -> Unit,
    onConfirmDuration: (Int, WorkLocation) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val defaultEnd = defaultStartTime.plusMinutes(dailyWorkMinutes.toLong())
    var startText by remember { mutableStateOf(TextFieldValue(defaultStartTime.format(DateTimeFormatter.ofPattern("HH:mm")))) }
    var endText by remember { mutableStateOf(TextFieldValue(defaultEnd.format(DateTimeFormatter.ofPattern("HH:mm")))) }
    var durationHours by remember { mutableStateOf((dailyWorkMinutes / 60).toString()) }
    var durationMinutes by remember { mutableStateOf((dailyWorkMinutes % 60).toString()) }
    var dialogLocation by remember { mutableStateOf(if (isBusinessTrip) WorkLocation.OFFICE else selectedLocation) }

    val overlappingBlock = remember(startText.text, endText.text, existingBlocks) {
        try {
            val start = LocalTime.parse(startText.text, DateTimeFormatter.ofPattern("HH:mm"))
            val end = LocalTime.parse(endText.text, DateTimeFormatter.ofPattern("HH:mm"))
            CheckTimeBlockOverlapUseCase().findOverlap(start, end, existingBlocks)
        } catch (_: Exception) { null }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zeit erfassen") },
        text = {
            Column {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Start / Ende") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Gesamtzeit") }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (!isBusinessTrip) {
                    Text("Arbeitsort", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = dialogLocation == WorkLocation.OFFICE,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                dialogLocation = WorkLocation.OFFICE
                            },
                            label = { Text("Büro") }
                        )
                        FilterChip(
                            selected = dialogLocation == WorkLocation.HOME_OFFICE,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                dialogLocation = WorkLocation.HOME_OFFICE
                            },
                            label = { Text("Home-Office") }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = formatTimeInput(it) },
                        label = { Text("Startzeit (HH:mm)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = formatTimeInput(it) },
                        label = { Text("Endzeit (HH:mm)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    if (overlappingBlock != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "⚠ Überlappt mit bestehendem Zeitblock (${overlappingBlock.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))} – ${overlappingBlock.endTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "laufend"})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = durationHours,
                            onValueChange = { durationHours = it },
                            label = { Text("Stunden") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = durationMinutes,
                            onValueChange = { durationMinutes = it },
                            label = { Text("Minuten") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (selectedTab == 0) {
                    try {
                        val start = LocalTime.parse(startText.text, DateTimeFormatter.ofPattern("HH:mm"))
                        val end = LocalTime.parse(endText.text, DateTimeFormatter.ofPattern("HH:mm"))
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onConfirmStartEnd(start, end, dialogLocation)
                    } catch (_: Exception) { }
                } else {
                    val h = durationHours.toIntOrNull() ?: 0
                    val m = durationMinutes.toIntOrNull() ?: 0
                    val total = h * 60 + m
                    if (total > 0) {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onConfirmDuration(total, dialogLocation)
                    }
                }
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
fun EditTimeBlockDialog(
    block: TimeBlock,
    existingBlocks: List<TimeBlock> = emptyList(),
    isBusinessTrip: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (startTime: LocalTime, endTime: LocalTime?, location: WorkLocation, isDuration: Boolean) -> Unit,
    onDelete: () -> Unit,
    onBook: ((startTime: LocalTime, endTime: LocalTime?, location: WorkLocation, isDuration: Boolean) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    var startText by remember { mutableStateOf(TextFieldValue(block.startTime.format(fmt))) }
    var endText by remember { mutableStateOf(TextFieldValue(block.endTime?.format(fmt) ?: "")) }
    var dialogLocation by remember { mutableStateOf(if (isBusinessTrip) WorkLocation.OFFICE else block.location) }
    var isDuration by remember { mutableStateOf(block.isDuration) }
    var hasSwitchedFromDuration by remember { mutableStateOf(false) }

    val overlappingBlock = remember(startText.text, endText.text, existingBlocks, block.id) {
        try {
            val start = LocalTime.parse(startText.text, DateTimeFormatter.ofPattern("HH:mm"))
            val end = if (endText.text.isBlank()) null else LocalTime.parse(endText.text, DateTimeFormatter.ofPattern("HH:mm"))
            CheckTimeBlockOverlapUseCase().findOverlap(start, end, existingBlocks, excludeBlockId = block.id)
        } catch (_: Exception) { null }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Zeitblock bearbeiten")
                if (isDuration) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.wrapContentSize()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Gesamtzeit",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isBusinessTrip) {
                    Text("Arbeitsort", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = dialogLocation == WorkLocation.OFFICE,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                dialogLocation = WorkLocation.OFFICE
                            },
                            label = { Text("Büro") }
                        )
                        FilterChip(
                            selected = dialogLocation == WorkLocation.HOME_OFFICE,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                dialogLocation = WorkLocation.HOME_OFFICE
                            },
                            label = { Text("Home-Office") }
                        )
                    }
                }

                Text("Erfassungstyp", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isDuration,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (isDuration) hasSwitchedFromDuration = true
                            isDuration = false
                        },
                        label = { Text("Start / Ende") }
                    )
                    FilterChip(
                        selected = isDuration,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isDuration = true
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Gesamtzeit") }
                    )
                }

                if (!isDuration && (block.isDuration || hasSwitchedFromDuration)) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Hinweis: Beim Wechsel zu Start/Ende wird die gesetzliche Pause abgezogen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = formatTimeInput(it) },
                        label = { Text("Start (HH:mm)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = formatTimeInput(it) },
                        label = { Text("Ende (HH:mm)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("laufend") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                if (overlappingBlock != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "⚠ Überlappt mit bestehendem Zeitblock (${overlappingBlock.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))} – ${overlappingBlock.endTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "laufend"})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (onBook != null) {
                    HorizontalDivider()
                    TextButton(
                        onClick = {
                            try {
                                val start = LocalTime.parse(startText.text, DateTimeFormatter.ofPattern("HH:mm"))
                                val end = if (endText.text.isBlank()) null
                                else LocalTime.parse(endText.text, DateTimeFormatter.ofPattern("HH:mm"))
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onBook(start, end, dialogLocation, isDuration)
                            } catch (_: Exception) { }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Buchen")
                    }
                    HorizontalDivider()
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onDelete()
                    }) {
                        Text("Löschen", color = MaterialTheme.colorScheme.error)
                    }
                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("Abbrechen")
                        }
                        TextButton(onClick = {
                            try {
                                val start = LocalTime.parse(startText.text, DateTimeFormatter.ofPattern("HH:mm"))
                                val end = if (endText.text.isBlank()) null
                                else LocalTime.parse(endText.text, DateTimeFormatter.ofPattern("HH:mm"))
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onSave(start, end, dialogLocation, isDuration)
                            } catch (_: Exception) { }
                        }) {
                            Text("Speichern")
                        }
                    }
                }
            }
        },
        dismissButton = {}
    )
}
