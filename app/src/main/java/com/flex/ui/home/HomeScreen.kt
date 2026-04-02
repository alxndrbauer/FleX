package com.flex.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flex.domain.model.BreakViolationType
import com.flex.domain.model.DayType
import com.flex.domain.model.PublicHolidays
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkLocation
import com.flex.ui.yearchange.YearChangeDialog
import com.flex.ui.yearchange.YearChangeViewModel
import com.flex.ui.components.InfoTooltip
import com.flex.ui.components.TOOLTIP_FLEXTIME
import com.flex.ui.components.TOOLTIP_FLEXTIME_TITLE
import com.flex.ui.components.TOOLTIP_OFFICE_QUOTA
import com.flex.ui.components.TOOLTIP_OFFICE_QUOTA_TITLE
import com.flex.ui.components.formatTimeInput
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
    viewModel: HomeViewModel = hiltViewModel(),
    yearChangeViewModel: YearChangeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val remaining by viewModel.remainingMinutes.collectAsState()
    val yearChangeState by yearChangeViewModel.uiState.collectAsState()
    var showManualEntry by remember { mutableStateOf(false) }
    var editingBlock by remember { mutableStateOf<TimeBlock?>(null) }
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
    val isWorkDay = state.selectedDayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS)

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (isWorkDay && holidayName == null && isToday) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (state.isClockRunning) viewModel.clockOut() else viewModel.clockIn()
                    },
                    icon = {
                        Icon(
                            if (state.isClockRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                    },
                    text = { Text(if (state.isClockRunning) "Ausstempeln" else "Einstempeln") },
                    containerColor = if (state.isClockRunning)
                        MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (state.isClockRunning)
                        MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
        ) {
            // Hero card
            item {
                HeroCard(
                    state = state,
                    remaining = remaining,
                    isToday = isToday,
                    onPreviousDay = { viewModel.goToPreviousDay() },
                    onNextDay = { viewModel.goToNextDay() },
                    onGoToToday = { viewModel.goToToday() },
                    onJumpToDate = { viewModel.navigateToDate(it) }
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
                // Location selector
                item {
                    Text(
                        "Arbeitsort (Standard)",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = state.selectedLocation == WorkLocation.OFFICE,
                            onClick = { viewModel.setLocation(WorkLocation.OFFICE) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            icon = {
                                Icon(Icons.Default.Business, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                            }
                        ) {
                            Text("Büro")
                        }
                        SegmentedButton(
                            selected = state.selectedLocation == WorkLocation.HOME_OFFICE,
                            onClick = { viewModel.setLocation(WorkLocation.HOME_OFFICE) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            icon = {
                                Icon(Icons.Default.Home, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                            }
                        ) {
                            Text("Home-Office")
                        }
                    }
                }

                // Day type selector
                item {
                    Column {
                        Text("Tagestyp", style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(bottom = 6.dp))

                        val dayTypes = listOf(
                            DayType.WORK             to Triple("Arbeitstag",    Icons.Default.Work,          MaterialTheme.colorScheme.primary),
                            DayType.VACATION         to Triple("Urlaub",         Icons.Default.BeachAccess,   VacationColor),
                            DayType.SPECIAL_VACATION to Triple("Sonderurlaub",   Icons.Default.Star,          SpecialVacationColor),
                            DayType.FLEX_DAY         to Triple("Gleittag",       Icons.Default.Schedule,      FlexDayColor),
                            DayType.SATURDAY_BONUS   to Triple("Samstag+",       Icons.Default.Timer,         SaturdayBonusColor),
                            DayType.SICK_DAY         to Triple("Krank",          Icons.Default.LocalHospital, SickDayColor),
                            DayType.OVERTIME_DAY     to Triple("Überstunden",    Icons.Default.ArrowDownward, OvertimeDayColor),
                        )
                        val currentEntry = dayTypes.find { it.first == state.selectedDayType }
                        val (currentLabel, currentIcon, currentColor) = currentEntry?.second
                            ?: Triple("Arbeitstag", Icons.Default.Work, MaterialTheme.colorScheme.primary)

                        var dayTypeExpanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = dayTypeExpanded,
                            onExpandedChange = { dayTypeExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = currentLabel,
                                onValueChange = {},
                                readOnly = true,
                                leadingIcon = {
                                    Icon(
                                        currentIcon,
                                        contentDescription = null,
                                        tint = currentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayTypeExpanded)
                                },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                    focusedLeadingIconColor = currentColor,
                                    unfocusedLeadingIconColor = currentColor
                                ),
                                modifier = Modifier
                                    .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = dayTypeExpanded,
                                onDismissRequest = { dayTypeExpanded = false }
                            ) {
                                dayTypes.forEach { (type, info) ->
                                    val (label, icon, color) = info
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        leadingIcon = {
                                            Icon(
                                                icon,
                                                contentDescription = null,
                                                tint = color,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        onClick = {
                                            viewModel.setDayType(type)
                                            dayTypeExpanded = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }
                        }

                        val nonWorkSaveTypes = setOf(
                            DayType.VACATION, DayType.SPECIAL_VACATION,
                            DayType.FLEX_DAY, DayType.SATURDAY_BONUS, DayType.SICK_DAY, DayType.OVERTIME_DAY
                        )
                        val savedDayType = state.workDay?.dayType
                        val showSaveButton = state.selectedDayType in nonWorkSaveTypes &&
                            state.selectedDayType != savedDayType
                        val showDeleteButton = savedDayType in nonWorkSaveTypes &&
                            state.selectedDayType == savedDayType
                        if (showSaveButton) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val typeLabel = when (state.selectedDayType) {
                                DayType.VACATION -> "Urlaub"
                                DayType.SPECIAL_VACATION -> "Sonderurlaub"
                                DayType.FLEX_DAY -> "Gleittag"
                                DayType.SICK_DAY -> "Kranktag"
                                DayType.OVERTIME_DAY -> "Überstundentag"
                                else -> "Samstag+"
                            }
                            Button(
                                onClick = { viewModel.saveDayType(state.selectedDayType) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Als $typeLabel eintragen")
                            }
                        }
                        if (showDeleteButton) {
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { viewModel.deleteDay() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Eintrag löschen")
                            }
                        }
                    }
                }

                // Manuell button (clock-in is now FAB)
                item {
                    if (isWorkDay) {
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

                items(state.timeBlocks) { block ->
                    TimelineBlockItem(
                        block = block,
                        onEdit = { editingBlock = it },
                        onDelete = { viewModel.deleteTimeBlock(it) }
                    )
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
        ManualTimeEntryDialog(
            dailyWorkMinutes = state.settings.dailyWorkMinutes,
            selectedLocation = state.selectedLocation,
            onDismiss = { showManualEntry = false },
            onConfirmStartEnd = { start, end, location ->
                viewModel.saveManualEntry(start, end, location)
                showManualEntry = false
            },
            onConfirmDuration = { totalMinutes, location ->
                viewModel.saveDurationEntry(totalMinutes, location)
                showManualEntry = false
            }
        )
    }

    // Edit time block dialog
    editingBlock?.let { block ->
        EditTimeBlockDialog(
            block = block,
            onDismiss = { editingBlock = null },
            onSave = { startTime, endTime, location ->
                viewModel.updateTimeBlock(block, startTime, endTime, location)
                editingBlock = null
            },
            onDelete = {
                viewModel.deleteTimeBlock(block)
                editingBlock = null
            }
        )
    }
}

@Composable
private fun HeroCard(
    state: HomeUiState,
    remaining: Int?,
    isToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onGoToToday: () -> Unit,
    onJumpToDate: (LocalDate) -> Unit,
) {
    val rawDailyProgress = if (state.settings.dailyWorkMinutes > 0)
        (state.dayWorkTime.netMinutes.toFloat() / state.settings.dailyWorkMinutes).coerceIn(0f, 1f)
    else 0f
    val animatedDailyProgress by animateFloatAsState(
        targetValue = rawDailyProgress,
        animationSpec = tween(800),
        label = "dailyProgress"
    )

    val hours = state.dayWorkTime.netMinutes / 60
    val mins = state.dayWorkTime.netMinutes % 60
    val netTimeText = if (hours > 0) "${hours}h ${mins}m" else "${mins}min"

    val dailyGoalReached = state.dayWorkTime.netMinutes >= state.settings.dailyWorkMinutes
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
    val progressColor = if (dailyGoalReached) MaterialTheme.colorScheme.secondary
        else MaterialTheme.colorScheme.primary

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
                        onJumpToDate(
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

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Date + navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousDay) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Vorheriger Tag")
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isToday) "Heute" else state.selectedDate.format(
                            DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN)
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isToday) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = state.selectedDate.format(
                            DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isToday) {
                        TextButton(
                            onClick = onGoToToday,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("Zurück zu Heute", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                IconButton(onClick = onNextDay) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Nächster Tag")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Circular progress
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { animatedDailyProgress },
                    modifier = Modifier.size(120.dp),
                    strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round,
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = netTimeText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (statusLabel != null) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stats row: Soll | Pause | Verbleibend
            val tH = state.settings.dailyWorkMinutes / 60
            val tM = state.settings.dailyWorkMinutes % 60
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Soll", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${tH}h ${tM}min", style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Pause", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (state.dayWorkTime.breakMinutes > 0) "${state.dayWorkTime.breakMinutes}min" else "–",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Verbleibend", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when {
                        remaining == 0 -> Text(
                            "Erreicht ✓",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        remaining != null -> {
                            val rH = remaining / 60
                            val rM = remaining % 60
                            Text(
                                if (rH > 0) "${rH}h ${rM}min" else "${rM}min",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        else -> Text("–", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Progress bar for remaining time
            remaining?.let { rem ->
                if (rem > 0) {
                    val animProg by animateFloatAsState(
                        targetValue = (1f - rem.toFloat() / state.settings.dailyWorkMinutes).coerceIn(0f, 1f),
                        animationSpec = tween(600),
                        label = "remainingProgress"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { animProg },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }

            if (state.dayWorkTime.exceedsMaxHours) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Achtung: Max. 10h Arbeitszeit überschritten!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Gleitzeit: gesamt + dieser Monat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Gleitzeit gesamt",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.liveFlextimeDelta > 0L) {
                            val liveTotal = state.flextimeBalance.totalMinutes + state.liveFlextimeDelta
                            val sign = if (liveTotal >= 0) "+" else "-"
                            val h = kotlin.math.abs(liveTotal) / 60
                            val m = kotlin.math.abs(liveTotal) % 60
                            Text(
                                text = "→ ${sign}${h}h ${m}min",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (liveTotal >= 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                text = state.flextimeBalance.formatDisplay(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (state.flextimeBalance.isPositive)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (YearMonth.from(state.selectedDate) == YearMonth.now()) "Dieser Monat"
                                   else state.selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val monthlyEarned = state.monthlyFlextimeBalance.earnedMinutes
                        if (state.liveFlextimeDelta > 0L) {
                            val liveMonthly = monthlyEarned + state.liveFlextimeDelta
                            val liveSign = if (liveMonthly >= 0) "+" else "-"
                            val liveH = kotlin.math.abs(liveMonthly) / 60
                            val liveM = kotlin.math.abs(liveMonthly) % 60
                            Text(
                                text = "→ ${liveSign}${liveH}h ${liveM}min",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (liveMonthly >= 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                        } else {
                            val monthlySign = if (monthlyEarned >= 0) "+" else "-"
                            val monthlyH = kotlin.math.abs(monthlyEarned) / 60
                            val monthlyM = kotlin.math.abs(monthlyEarned) % 60
                            Text(
                                text = "$monthlySign${monthlyH}h ${monthlyM}min",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (monthlyEarned >= 0)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
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

    val officeH = state.officeMinutes / 60
    val officeM = state.officeMinutes % 60
    val reqH = state.requiredOfficeMinutes / 60
    val reqM = state.requiredOfficeMinutes % 60

    // Today is excluded from quota calc when it has no completed blocks yet (only running block).
    // Add a +1 live bonus day when clocked in as OFFICE before any clock-out.
    val todayHasNoCompletedBlock = state.timeBlocks.none { it.endTime != null }
    val liveOfficeDelta = if (state.liveFlextimeDelta > 0 && state.selectedLocation == WorkLocation.OFFICE)
        state.liveFlextimeDelta else 0L
    val liveOfficeDayBonus = if (liveOfficeDelta > 0 && todayHasNoCompletedBlock) 1 else 0
    val liveOfficeMin = state.officeMinutes + liveOfficeDelta
    val liveH = liveOfficeMin / 60
    val liveM = liveOfficeMin % 60
    // officePercent = officeMinutes / fixedTarget * 100, where fixedTarget = requiredOfficeMinutes * 100 / quotaPercent
    val fixedTarget = if (state.effectiveQuotaPercent > 0)
        state.requiredOfficeMinutes * 100.0 / state.effectiveQuotaPercent else 0.0
    val livePercent = if (fixedTarget > 0) liveOfficeMin.toDouble() / fixedTarget * 100 else 0.0

    val rawHoursProgress = if (state.requiredOfficeMinutes > 0)
        (state.officeMinutes.toFloat() / state.requiredOfficeMinutes).coerceIn(0f, 1f)
    else 0f
    val rawPctProgress = (quota.officePercent / state.effectiveQuotaPercent.toDouble())
        .toFloat().coerceIn(0f, 1f)
    val rawDayProgress = (quota.officeDays.toFloat() / state.effectiveQuotaMinDays.toFloat())
        .coerceIn(0f, 1f)

    val animHours by animateFloatAsState(rawHoursProgress, tween(600), label = "hoursProgress")
    val animPct by animateFloatAsState(rawPctProgress, tween(600), label = "pctProgress")
    val animDays by animateFloatAsState(rawDayProgress, tween(600), label = "dayProgress")

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Büro-Quote", style = MaterialTheme.typography.labelMedium)
                    InfoTooltip(title = TOOLTIP_OFFICE_QUOTA_TITLE, text = TOOLTIP_OFFICE_QUOTA)
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (quota.quotaMet)
                        MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.wrapContentSize()
                ) {
                    Text(
                        text = if (quota.quotaMet) "Erfüllt" else "Ausstehend",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (quota.quotaMet)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            CompactProgressRow(
                label = if (liveOfficeDelta > 0) "→ ${liveH}h ${liveM}min" else "${officeH}h ${officeM}min",
                sublabel = "von ${reqH}h ${reqM}min Büro",
                progress = if (liveOfficeDelta > 0) {
                    if (state.requiredOfficeMinutes > 0)
                        (liveOfficeMin.toFloat() / state.requiredOfficeMinutes).coerceIn(0f, 1f)
                    else 0f
                } else animHours,
                isMet = rawHoursProgress >= 1f
            )
            Spacer(modifier = Modifier.height(6.dp))
            CompactProgressRow(
                label = if (liveOfficeDelta > 0) "→ ${"%.1f".format(livePercent)}%" else "${"%.1f".format(quota.officePercent)}%",
                sublabel = "von ${state.effectiveQuotaPercent}% Ziel",
                progress = if (liveOfficeDelta > 0) {
                    (livePercent / state.effectiveQuotaPercent.toDouble()).toFloat().coerceIn(0f, 1f)
                } else animPct,
                isMet = quota.officePercent >= state.effectiveQuotaPercent
            )
            Spacer(modifier = Modifier.height(6.dp))
            CompactProgressRow(
                label = if (liveOfficeDayBonus > 0) "→ ${quota.officeDays + liveOfficeDayBonus} Tage" else "${quota.officeDays} Tage",
                sublabel = "von ${state.effectiveQuotaMinDays} Büro-Tagen",
                progress = if (liveOfficeDayBonus > 0)
                    ((quota.officeDays + liveOfficeDayBonus).toFloat() / state.effectiveQuotaMinDays).coerceIn(0f, 1f)
                else animDays,
                isMet = quota.officeDays >= state.effectiveQuotaMinDays
            )
        }
    }
}

@Composable
private fun CompactProgressRow(
    label: String,
    sublabel: String,
    progress: Float,
    isMet: Boolean
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(
                sublabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (isMet) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun TimelineBlockItem(
    block: TimeBlock,
    onEdit: (TimeBlock) -> Unit,
    onDelete: (TimeBlock) -> Unit
) {
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable { onEdit(block) },
        verticalAlignment = Alignment.Top
    ) {
        // Left: colored timeline indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(locationColor)
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(locationColor.copy(alpha = 0.3f))
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right: content card
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
}

@Composable
fun ManualTimeEntryDialog(
    dailyWorkMinutes: Int = 426,
    selectedLocation: WorkLocation = WorkLocation.OFFICE,
    onDismiss: () -> Unit,
    onConfirmStartEnd: (LocalTime, LocalTime, WorkLocation) -> Unit,
    onConfirmDuration: (Int, WorkLocation) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val defaultEnd = LocalTime.of(8, 0).plusMinutes(dailyWorkMinutes.toLong())
    var startText by remember { mutableStateOf(TextFieldValue("08:00")) }
    var endText by remember { mutableStateOf(TextFieldValue(defaultEnd.format(DateTimeFormatter.ofPattern("HH:mm")))) }
    var durationHours by remember { mutableStateOf((dailyWorkMinutes / 60).toString()) }
    var durationMinutes by remember { mutableStateOf((dailyWorkMinutes % 60).toString()) }
    var dialogLocation by remember { mutableStateOf(selectedLocation) }

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

                Text("Arbeitsort", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = dialogLocation == WorkLocation.OFFICE,
                        onClick = { dialogLocation = WorkLocation.OFFICE },
                        label = { Text("Büro") }
                    )
                    FilterChip(
                        selected = dialogLocation == WorkLocation.HOME_OFFICE,
                        onClick = { dialogLocation = WorkLocation.HOME_OFFICE },
                        label = { Text("Home-Office") }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

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
                        onConfirmStartEnd(start, end, dialogLocation)
                    } catch (_: Exception) { }
                } else {
                    val h = durationHours.toIntOrNull() ?: 0
                    val m = durationMinutes.toIntOrNull() ?: 0
                    val total = h * 60 + m
                    if (total > 0) onConfirmDuration(total, dialogLocation)
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
    onDismiss: () -> Unit,
    onSave: (startTime: LocalTime, endTime: LocalTime?, location: WorkLocation) -> Unit,
    onDelete: () -> Unit
) {
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    var startText by remember { mutableStateOf(TextFieldValue(block.startTime.format(fmt))) }
    var endText by remember { mutableStateOf(TextFieldValue(block.endTime?.format(fmt) ?: "")) }
    var dialogLocation by remember { mutableStateOf(block.location) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zeitblock bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Arbeitsort", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = dialogLocation == WorkLocation.OFFICE,
                        onClick = { dialogLocation = WorkLocation.OFFICE },
                        label = { Text("Büro") }
                    )
                    FilterChip(
                        selected = dialogLocation == WorkLocation.HOME_OFFICE,
                        onClick = { dialogLocation = WorkLocation.HOME_OFFICE },
                        label = { Text("Home-Office") }
                    )
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    val start = LocalTime.parse(startText.text, DateTimeFormatter.ofPattern("HH:mm"))
                    val end = if (endText.text.isBlank()) null
                    else LocalTime.parse(endText.text, DateTimeFormatter.ofPattern("HH:mm"))
                    onSave(start, end, dialogLocation)
                } catch (_: Exception) { }
            }) {
                Text("Speichern")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) {
                    Text("Abbrechen")
                }
            }
        }
    )
}
