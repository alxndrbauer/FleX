package com.flex.ui.home

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import com.flex.ui.components.glowBorder
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.Role
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
import com.flex.ui.components.FlexSnackbarHost
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

// The black/white luminance cutoff keeps the opaque category fills above WCAG AA 4.5:1.
internal fun dayTypeSaveButtonContentColor(backgroundColor: Color): Color =
    if (backgroundColor.luminance() > 0.179f) Color.Black else Color.White

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

    val listState = rememberLazyListState()
    val heroItemIndex = if (state.permissionIssues.isNotEmpty()) 1 else 0
    val isHeroScrolledOutOfView by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > heroItemIndex ||
                (listState.firstVisibleItemIndex == heroItemIndex && listState.firstVisibleItemScrollOffset > 240)
        }
    }

    val isNavVisible = LocalFloatingNavVisible.current
    val hasActiveSnackbar = snackbarHostState.currentSnackbarData != null
    val fabBottomPadding by animateDpAsState(
        targetValue = when {
            hasActiveSnackbar && isNavVisible -> 156.dp
            hasActiveSnackbar && !isNavVisible -> 84.dp
            isNavVisible -> 76.dp
            else -> 16.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "fab_bottom_padding"
    )

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { FlexSnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            AnimatedVisibility(
                visible = isToday && isHeroScrolledOutOfView,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut(tween(150)) + fadeOut()
            ) {
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
                    targetValue = if (state.isClockRunning) 1.05f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "fab_scale"
                )

                FloatingActionButton(
                    onClick = {
                        if (state.isClockRunning) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.clockOut()
                        } else {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            viewModel.clockIn()
                        }
                    },
                    containerColor = fabContainerColor,
                    contentColor = fabContentColor,
                    shape = CircleShape,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = fabBottomPadding)
                        .graphicsLayer {
                            scaleX = fabScale
                            scaleY = fabScale
                        }
                ) {
                    Crossfade(
                        targetState = state.isClockRunning,
                        animationSpec = tween(250),
                        label = "fab_icon_crossfade"
                    ) { isRunning ->
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isRunning) "Ausstempeln" else "Einstempeln",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (isToday) "Heute" else state.selectedDate.format(DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN)),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        if (!isToday) {
                            val todayButtonShape = RoundedCornerShape(50)
                            Row(
                                modifier = Modifier
                                    .clip(todayButtonShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                        todayButtonShape
                                    )
                                    .clickable(role = Role.Button) { viewModel.goToToday() }
                                    .padding(horizontal = 7.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Heute",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Text(
                        text = state.selectedDate.format(DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                IconButton(onClick = { viewModel.goToNextDay() }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Nächster Tag")
                }
            }

            LazyColumn(
                state = listState,
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
                        onSelectDayType = { viewModel.setDayType(it) },
                        onSaveDayType = { viewModel.saveDayType(it) },
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

            // Saturday bonus info banner
            if (state.selectedDayType == DayType.SATURDAY_BONUS) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = SaturdayBonusColor.copy(alpha = 0.12f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Samstag+",
                                style = MaterialTheme.typography.labelMedium,
                                color = SaturdayBonusColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Samstagsarbeit: Jede geleistete Stunde wird + 50% auf das Überstundenkonto angerechnet.",
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

            // Time blocks list
            if (state.timeBlocks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Zeitblöcke",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val totalNetMinutes = state.dayWorkTime.netMinutes
                            val tH = totalNetMinutes / 60
                            val tM = totalNetMinutes % 60
                            val timeSummary = if (tH > 0) "${tH}h ${tM}m" else "${tM}min"
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = "${state.timeBlocks.size} ${if (state.timeBlocks.size == 1) "Block" else "Blöcke"} • $timeSummary",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showManualEntry = true
                            },
                            enabled = holidayName == null,
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Eintragen",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
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
            } else if (run {
                val currentWorkDay = state.workDay
                val nonWorkDayTypes = setOf(
                    DayType.VACATION, DayType.SPECIAL_VACATION,
                    DayType.FLEX_DAY, DayType.SICK_DAY, DayType.OVERTIME_DAY
                )
                state.selectedDayType in nonWorkDayTypes || (currentWorkDay != null && currentWorkDay.dayType in nonWorkDayTypes)
            }) {
                item {
                    val currentWorkDay = state.workDay
                    val nonWorkDayTypes = setOf(
                        DayType.VACATION, DayType.SPECIAL_VACATION,
                        DayType.FLEX_DAY, DayType.SICK_DAY, DayType.OVERTIME_DAY
                    )
                    val activeType = if (state.selectedDayType in nonWorkDayTypes) {
                        state.selectedDayType
                    } else if (currentWorkDay != null && currentWorkDay.dayType in nonWorkDayTypes) {
                        currentWorkDay.dayType
                    } else {
                        state.selectedDayType
                    }
                    val isSaved = currentWorkDay != null && currentWorkDay.dayType == activeType && !currentWorkDay.isPlanned
                    val (typeLabel, typeIcon, typeColor) = when (activeType) {
                        DayType.VACATION -> Triple("Urlaub", Icons.Default.BeachAccess, VacationColor)
                        DayType.SPECIAL_VACATION -> Triple("Sonderurlaub", Icons.Default.Star, SpecialVacationColor)
                        DayType.FLEX_DAY -> Triple("Gleittag", Icons.Default.Schedule, FlexDayColor)
                        DayType.SATURDAY_BONUS -> Triple("Samstag+", Icons.Default.Timer, SaturdayBonusColor)
                        DayType.SICK_DAY -> Triple("Krank", Icons.Default.LocalHospital, SickDayColor)
                        DayType.OVERTIME_DAY -> Triple("Überstunden", Icons.Default.ArrowDownward, OvertimeDayColor)
                        else -> Triple("Tag", Icons.Default.Work, MaterialTheme.colorScheme.primary)
                    }
                    val desc = if (isSaved) {
                        when (activeType) {
                            DayType.VACATION -> "Für diesen Tag ist Urlaub eingetragen."
                            DayType.SPECIAL_VACATION -> "Für diesen Tag ist Sonderurlaub eingetragen."
                            DayType.FLEX_DAY -> "Für diesen Tag ist ein Gleittag eingetragen."
                            DayType.SICK_DAY -> "Für diesen Tag ist ein Kranktag eingetragen."
                            DayType.OVERTIME_DAY -> "Für diesen Tag ist ein Überstundentag eingetragen."
                            DayType.SATURDAY_BONUS -> "Samstagsarbeit mit 50% Überstunden-Bonus."
                            else -> ""
                        }
                    } else if (currentWorkDay?.isPlanned == true) {
                        "Für diesen Tag ist $typeLabel geplant."
                    } else {
                        "Als $typeLabel ausgewählt. Klicke auf 'Eintragen' zum Speichern."
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = typeColor.copy(alpha = 0.12f)
                        ),
                        border = BorderStroke(1.dp, typeColor.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(typeColor.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = typeIcon,
                                        contentDescription = null,
                                        tint = typeColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = typeLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = typeColor
                                    )
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (!isSaved) {
                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                        viewModel.saveDayType(activeType)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = typeColor,
                                        contentColor = dayTypeSaveButtonContentColor(typeColor)
                                    )
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Als $typeLabel eintragen", fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                        viewModel.deleteDay()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Eintrag löschen", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    EmptyStateView(
                        icon = Icons.Default.Schedule,
                        title = "Keine Zeitblöcke vorhanden",
                        description = if (isToday) "Starte die Zeiterfassung mit dem Stempel-Button oder trage Zeiten manuell nach."
                            else "Für diesen Tag wurden noch keine Arbeitszeiten erfasst.",
                        actionLabel = "Zeit manuell eintragen",
                        actionEnabled = holidayName == null,
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
    onSaveDayType: (DayType) -> Unit = {},
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

    val nonWorkSaveTypes = setOf(
        DayType.VACATION, DayType.SPECIAL_VACATION,
        DayType.FLEX_DAY, DayType.SICK_DAY, DayType.OVERTIME_DAY
    )
    val savedDayType = state.workDay?.dayType
    val currentEffectiveType = savedDayType ?: DayType.WORK
    val dayTypeChanged = state.selectedDayType != currentEffectiveType
    val isNonWorkSelected = state.selectedDayType in nonWorkSaveTypes
    val needsSave = dayTypeChanged || (state.workDay?.isPlanned == true && isNonWorkSelected)
    val isSavedNonWork = savedDayType in nonWorkSaveTypes && state.selectedDayType == savedDayType && state.workDay?.isPlanned != true

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

    val statusLabel = when {
        state.isClockRunning -> "Läuft"
        dailyGoalReached -> "Fertig"
        state.timeBlocks.isNotEmpty() -> "Pausiert"
        isSavedNonWork -> currentDayTypeLabel
        isNonWorkSelected -> currentDayTypeLabel
        else -> null
    }
    val statusColor = when {
        state.isClockRunning -> MaterialTheme.colorScheme.primary
        remaining == 0 -> MaterialTheme.colorScheme.secondary
        isSavedNonWork || isNonWorkSelected -> currentDayTypeColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    var showDayTypeSheet by remember { mutableStateOf(false) }
    var sheetSelectedType by remember(state.selectedDayType) { mutableStateOf(state.selectedDayType) }
    var showFlextimeDetailsSheet by remember { mutableStateOf(false) }

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
                        val isSelected = sheetSelectedType == type
                        val isBlocked = type == DayType.SPECIAL_VACATION && isSpecialVacationBlocked

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
                            border = if (isSelected) BorderStroke(1.5.dp, color) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !isBlocked) {
                                    sheetSelectedType = type
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

    if (showFlextimeDetailsSheet) {
        FlextimeDetailsSheet(
            state = state,
            isToday = isToday,
            onDismiss = { showFlextimeDetailsSheet = false }
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
            val targetText = if (state.dailyTargetMinutes > 0) "${tH}h ${tM}min" else ""
            val prognosisText = if (state.isClockRunning && isToday && (remaining == null || remaining > 0)) {
                state.projectedEndTime?.formatCompact()
            } else null

            var rightColumnHeightDp by remember { mutableStateOf(135.dp) }
            val density = LocalDensity.current

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
                    sizeDp = kotlin.math.round(rightColumnHeightDp.value).toInt()
                )

                // Right: 3 compact metric cards
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .onSizeChanged { intSize ->
                            if (intSize.height > 0) {
                                val hDp = with(density) { intSize.height.toDp() }
                                if (kotlin.math.abs(rightColumnHeightDp.value - hDp.value) > 0.5f) {
                                    rightColumnHeightDp = hDp
                                }
                            }
                        },
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Card 1: Gleitzeit (Saldo)
                    val isLiveFlextime = state.isClockRunning && isToday
                    val displayBalance = if (isLiveFlextime) {
                        state.liveFlextimeBalance ?: state.flextimeBalance
                    } else {
                        state.flextimeBalance
                    }
                    val balanceMin = displayBalance.totalMinutes
                    val bH = kotlin.math.abs(balanceMin) / 60
                    val bM = kotlin.math.abs(balanceMin) % 60
                    val bSign = if (balanceMin >= 0L) "+" else "-"
                    val balanceText = "$bSign${bH}h ${bM}m"
                    val isPos = balanceMin >= 0L

                    HeroMetricCard(
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        iconTint = if (isPos) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        iconBgColor = if (isPos) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
                        borderGlowColor = if (isPos) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        title = "Gleitzeit",
                        value = balanceText,
                        valueColor = if (isPos) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                        isLive = isLiveFlextime,
                        liveColor = if (isPos) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showFlextimeDetailsSheet = true
                        }
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
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        iconBgColor = MaterialTheme.colorScheme.primaryContainer,
                        title = "Pause",
                        value = breakText,
                        subtitle = breakSubtitle,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
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
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        iconBgColor = MaterialTheme.colorScheme.secondaryContainer,
                        title = "Verbleibend",
                        value = remainingText,
                        valueColor = if (remaining == 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
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

                // Eintragen / Löschen
                if (needsSave) {
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            onSaveDayType(state.selectedDayType)
                        },
                        enabled = holidayName == null,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Eintragen", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                } else if (isSavedNonWork) {
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            onDeleteDay()
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Löschen", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                } else {
                    FilledTonalButton(
                        onClick = onManualEntryClick,
                        enabled = holidayName == null,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Eintragen", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }

                // Starten / Stoppen
                if (isToday) {
                    val clockEnabled = holidayName == null && state.selectedDayType !in setOf(
                        DayType.VACATION, DayType.SPECIAL_VACATION,
                        DayType.FLEX_DAY, DayType.SICK_DAY, DayType.OVERTIME_DAY
                    )
                    FilledTonalButton(
                        onClick = onClockToggle,
                        enabled = clockEnabled,
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
                run {
                    val locationNotApplicable = state.selectedDayType in setOf(
                        DayType.VACATION, DayType.SPECIAL_VACATION,
                        DayType.FLEX_DAY, DayType.SICK_DAY, DayType.OVERTIME_DAY
                    )
                    val locationFixed = state.selectedDayType == DayType.BUSINESS_TRIP
                    val locationButtonEnabled = !locationNotApplicable && !locationFixed
                    val effectiveLocation = if (locationFixed) WorkLocation.OFFICE else state.selectedLocation
                    val isOffice = effectiveLocation == WorkLocation.OFFICE
                    FilledTonalIconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLocationChange(if (isOffice) WorkLocation.HOME_OFFICE else WorkLocation.OFFICE)
                        },
                        enabled = locationButtonEnabled,
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
    borderGlowColor: Color? = null,
    subtitle: String? = null,
    isLive: Boolean = false,
    liveColor: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val cardShape = RoundedCornerShape(14.dp)
    val glowColor = borderGlowColor ?: iconTint
    val resolvedLiveColor = liveColor ?: borderGlowColor ?: MaterialTheme.colorScheme.primary

    Surface(
        shape = cardShape,
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .glowBorder(color = glowColor, shape = cardShape)
                        .clip(cardShape)
                        .clickable(role = Role.Button, onClick = onClick)
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (isLive) Modifier.weight(1f, fill = false) else Modifier
                    )
                    if (isLive) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse_hero_live")
                        val pulseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(850, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse_hero_live_alpha"
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = resolvedLiveColor.copy(alpha = 0.20f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(resolvedLiveColor.copy(alpha = pulseAlpha))
                                )
                                Text(
                                    text = "LIVE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = resolvedLiveColor,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
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
                        val subColor = when (subtitle) {
                            "Pflicht!" -> MaterialTheme.colorScheme.error
                            "OK ✓" -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = subColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlextimeDetailsSheet(
    state: HomeUiState,
    isToday: Boolean,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isLive = state.isClockRunning && isToday
    val netMin = state.dayWorkTime.netMinutes
    val targetMin = state.dailyTargetMinutes.toLong()
    val hasCompletedBlocks = state.timeBlocks.any { it.endTime != null }

    val baseMin: Long = state.baseFlextimeBalance.totalMinutes
    val totalMin: Long = if (isLive) {
        state.liveFlextimeBalance?.totalMinutes ?: (baseMin + (netMin - targetMin))
    } else if (isToday) {
        state.flextimeBalance.totalMinutes
    } else {
        state.selectedDateFlextimeBalance.totalMinutes
    }

    val isNotStartedToday = isToday && !isLive && !hasCompletedBlocks && netMin == 0L && targetMin > 0
    val dayDiff: Long = if (isNotStartedToday) 0L else (totalMin - baseMin)

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
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Gleitzeit-Aufschlüsselung",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isLive) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "LIVE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (isLive) "Echtzeit-Berechnung während der Arbeitszeit"
                               else if (isToday) "Aufschlüsselung für den heutigen Tag"
                               else "Aufschlüsselung für ${state.selectedDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Card 1: Stand bis gestern (Basis-Gleitzeitsaldo)
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
                            text = if (isToday) "Stand bis gestern" else "Stand vor diesem Tag",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isToday) "Basis-Gleitzeitsaldo vor Arbeitsbeginn" else "Übertrag aus vorangegangenen Tagen",
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

            // Card 2: Heutiger Arbeitstag / Ausgewählter Tag
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
                    Text(
                        text = if (isToday) "Heutiger Arbeitstag" else "Arbeitstag (${state.selectedDate.format(DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMAN))})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Netto
                        Column {
                            Text(
                                text = if (isToday) "Netto heute" else "Netto",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val isNonWorkDayType = state.selectedDayType in setOf(DayType.VACATION, DayType.SPECIAL_VACATION, DayType.FLEX_DAY, DayType.OVERTIME_DAY, DayType.SICK_DAY)
                            val netText = if (netMin > 0) formatWorkTime(netMin) else if (isNonWorkDayType) "–" else "0m"
                            Text(
                                text = netText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "vs.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Soll
                        Column {
                            Text(
                                text = "Tagessoll",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (targetMin > 0) formatWorkTime(targetMin) else "–",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "=",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Differenz
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Tages-Differenz",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val diffText = if (isNotStartedToday) "–" else formatDayDiff(dayDiff)
                            val diffColor = if (isNotStartedToday || dayDiff == 0L) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else if (dayDiff > 0L) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                            Text(
                                text = diffText,
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

                    val calcExplanation = when {
                        isNotStartedToday ->
                            "Noch keine Arbeitszeit erfasst. Das Tagessoll (${formatWorkTime(targetMin)}) wird bei Arbeitsbeginn verrechnet."
                        state.selectedDayType == DayType.FLEX_DAY ->
                            "Gleitzeittag: Volles Tagessoll (${formatWorkTime(targetMin)}) wird vom Gleitzeitsaldo abgezogen."
                        state.selectedDayType == DayType.OVERTIME_DAY ->
                            "Überstundentag: Voller Tag wird vom Überstundenkonto abgezogen (Gleitzeit bleibt unberührt)."
                        state.selectedDayType in setOf(DayType.VACATION, DayType.SPECIAL_VACATION, DayType.SICK_DAY) ->
                            "${getDayTypeLabel(state.selectedDayType)}: Neutral für den Gleitzeitsaldo (keine Differenz)."
                        state.selectedDayType == DayType.SATURDAY_BONUS ->
                            if (netMin > 0) {
                                "Samstagsarbeit: ${formatWorkTime(netMin)} Netto + 50% Bonus (${formatWorkTime((netMin * 0.5).toLong())}) = ${formatWorkTime((netMin * 1.5).toLong())} auf Überstundenkonto."
                            } else {
                                "Samstagsarbeit: Arbeitszeit wird + 50% auf das Überstundenkonto angerechnet."
                            }
                        targetMin == 0L && netMin > 0L ->
                            "Arbeitsfreier Tag: Gesamte Arbeitszeit (+${formatWorkTime(netMin)}) wird als Gleitzeit verbucht."
                        targetMin == 0L ->
                            "Arbeitsfreier Tag"
                        else ->
                            "${formatWorkTime(netMin)} Netto − ${formatWorkTime(targetMin)} Soll = ${formatDayDiff(dayDiff)}"
                    }

                    Text(
                        text = calcExplanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            val isTotalPos = totalMin >= 0L
            val saldoColor = if (isTotalPos) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error

            // Card 3: Aktueller Live-Saldo / Gesamtsaldo
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, saldoColor.copy(alpha = 0.4f)),
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
                        val totalTitle = when {
                            isLive -> "Aktueller Live-Saldo (Gesamt)"
                            isToday -> "Aktueller Gesamtsaldo"
                            else -> "Saldo nach diesem Tag"
                        }
                        Text(
                            text = totalTitle,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = formatFlextimeBalance(totalMin),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = saldoColor
                        )
                    }

                    val dateLabel = if (isToday) "Heute" else state.selectedDate.format(DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMAN))
                    val formulaText = when {
                        isNotStartedToday ->
                            "${formatFlextimeBalance(baseMin)} (Basis) = ${formatFlextimeBalance(totalMin)}"
                        else ->
                            "${formatFlextimeBalance(baseMin)} (Basis) + (${formatDayDiff(dayDiff)}) ($dateLabel) = ${formatFlextimeBalance(totalMin)}"
                    }

                    Text(
                        text = formulaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    if (!isToday && totalMin != state.flextimeBalance.totalMinutes) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Aktueller Gesamtsaldo heute: ${formatFlextimeBalance(state.flextimeBalance.totalMinutes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isLive && dayDiff < 0L) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "💡 Hinweis: Der Live-Saldo sinkt morgens um das Tagessoll und wächst mit jeder gearbeiteten Minute an.",
                            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (isNotStartedToday) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "💡 Hinweis: Sobald die Arbeitszeiterfassung startet, wird das Tagessoll berücksichtigt und der Saldo wächst minütlich.",
                            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Schließen", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun formatWorkTime(minutes: Long): String {
    val absM = kotlin.math.abs(minutes)
    val h = absM / 60
    val m = absM % 60
    val mStr = if (h > 0 && m < 10) "0$m" else "$m"
    return if (h > 0) "${h}h ${mStr}m" else "${m}m"
}

private fun formatDayDiff(minutes: Long): String {
    val absM = kotlin.math.abs(minutes)
    val h = absM / 60
    val m = absM % 60
    val sign = if (minutes > 0L) "+" else if (minutes < 0L) "-" else ""
    return if (h > 0) {
        val mStr = if (m < 10) "0$m" else "$m"
        "$sign${h}h ${mStr}m"
    } else if (minutes == 0L) {
        "0m"
    } else {
        "$sign${m}m"
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

private fun getDayTypeLabel(type: DayType): String = when (type) {
    DayType.WORK -> "Arbeitstag"
    DayType.BUSINESS_TRIP -> "Dienstreise"
    DayType.VACATION -> "Urlaub"
    DayType.SPECIAL_VACATION -> "Sonderurlaub"
    DayType.FLEX_DAY -> "Gleitzeittag"
    DayType.SATURDAY_BONUS -> "Samstagsbonus"
    DayType.SICK_DAY -> "Krank"
    DayType.OVERTIME_DAY -> "Überstundentag"
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
    val currentOfficeMinutes = if (liveOfficeDelta > 0) liveOfficeMin else state.officeMinutes

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
    val isRunning = block.endTime == null
    val locationColor = when (block.location) {
        WorkLocation.OFFICE -> OfficeColor
        WorkLocation.HOME_OFFICE -> HomeOfficeColor
    }

    val startStr = block.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    val endStr = block.endTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "laufend…"
    val timeRangeText = "$startStr – $endStr"

    val durationMinutes = if (block.endTime != null) {
        java.time.Duration.between(block.startTime, block.endTime).toMinutes().coerceAtLeast(0)
    } else {
        java.time.Duration.between(block.startTime, LocalTime.now()).toMinutes().coerceAtLeast(0)
    }
    val dH = durationMinutes / 60
    val dM = durationMinutes % 60
    val durationText = if (dH > 0) "${dH}h ${dM}m" else "${dM}min"

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_block")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Column {
        // Start cap: horizontal tick and connection above first item
        if (isFirst) {
            Row {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(10.dp)
                        .drawBehind {
                            val cx = size.width / 2
                            val capHalf = 5.dp.toPx()
                            val railColor = locationColor.copy(alpha = 0.3f)
                            drawLine(
                                color = railColor,
                                start = Offset(cx - capHalf, 3.dp.toPx()),
                                end = Offset(cx + capHalf, 3.dp.toPx()),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawLine(
                                color = railColor,
                                start = Offset(cx, 3.dp.toPx()),
                                end = Offset(cx, size.height),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                )
            }
        }

        // Card row: Timeline node with icon on left + M3 card on right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top
        ) {
            // Left: Timeline Rail
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2
                    val nodeCenterY = size.height / 2
                    val nodeRadius = 14.dp.toPx()
                    val strokeW = 2.dp.toPx()
                    val railColor = locationColor.copy(alpha = 0.3f)

                    // Line from top to node
                    drawLine(
                        color = railColor,
                        start = Offset(cx, 0f),
                        end = Offset(cx, nodeCenterY - nodeRadius),
                        strokeWidth = strokeW
                    )

                    // Line from node to bottom
                    drawLine(
                        color = railColor,
                        start = Offset(cx, nodeCenterY + nodeRadius),
                        end = Offset(cx, size.height),
                        strokeWidth = strokeW
                    )
                }

                // Node Circle with location icon
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(locationColor.copy(alpha = if (isRunning) 0.22f else 0.12f))
                        .border(
                            width = if (isRunning) 2.dp else 1.5.dp,
                            color = if (isRunning) locationColor.copy(alpha = pulseAlpha) else locationColor,
                            shape = CircleShape
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleLocation(block)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (block.location == WorkLocation.OFFICE) Icons.Default.Business else Icons.Default.Home,
                        contentDescription = null,
                        tint = locationColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Content Card
            Card(
                modifier = Modifier
                    .weight(1f)
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.surfaceContainerHigh
                    else MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = if (isRunning) BorderStroke(1.5.dp, locationColor.copy(alpha = 0.55f))
                else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    // Top Row: Time range + Live/Duration Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timeRangeText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (isRunning) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = locationColor.copy(alpha = 0.16f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(locationColor.copy(alpha = pulseAlpha))
                                    )
                                    Text(
                                        text = "LIVE • $durationText",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = locationColor
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = durationText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bottom Row: Badges on left, Actions on right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Badges
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            if (block.isDuration) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(11.dp)
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
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(11.dp)
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

                        // Action buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onEdit(block)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Bearbeiten",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onDelete(block)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Löschen",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Gap connector (between items) or end cap (last item)
        Row {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(14.dp)
                    .drawBehind {
                        val cx = size.width / 2
                        val railColor = locationColor.copy(alpha = 0.3f)
                        if (!isLast) {
                            // Connector line through the gap
                            drawLine(
                                color = railColor,
                                start = Offset(cx, 0f),
                                end = Offset(cx, size.height),
                                strokeWidth = 2.dp.toPx()
                            )
                        } else {
                            // End cap: short line + horizontal tick
                            val capHalf = 5.dp.toPx()
                            drawLine(
                                color = railColor,
                                start = Offset(cx, 0f),
                                end = Offset(cx, size.height * 0.7f),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawLine(
                                color = railColor,
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
