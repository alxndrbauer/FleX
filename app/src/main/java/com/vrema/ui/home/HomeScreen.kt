package com.vrema.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vrema.domain.model.DayType
import com.vrema.domain.model.PublicHolidays
import com.vrema.domain.model.TimeBlock
import com.vrema.domain.model.WorkLocation
import com.vrema.ui.theme.PublicHolidayColor
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var showManualEntry by remember { mutableStateOf(false) }
    var editingBlock by remember { mutableStateOf<TimeBlock?>(null) }

    val isToday = state.selectedDate == state.today

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Date navigation header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.goToPreviousDay() }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Vorheriger Tag")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isToday) "Heute" else state.selectedDate.format(
                            DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN)
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = state.selectedDate.format(
                            DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.goToNextDay() }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Nächster Tag")
                }
            }
            if (!isToday) {
                TextButton(
                    onClick = { viewModel.goToToday() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Zurück zu Heute")
                }
            }
        }

        // Holiday card
        val holidayName = PublicHolidays.getHolidayName(state.selectedDate)
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

        // Flextime balance card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.flextimeBalance.isPositive)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Gleitzeit-Saldo (Soll: ${state.flextimeBalance.formatTarget()})",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = state.flextimeBalance.formatDisplay(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Quota progress bar
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Büro-Quote", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))

                    val quota = state.quotaStatus

                    // Office hours
                    val officeH = state.officeMinutes / 60
                    val officeM = state.officeMinutes % 60
                    val reqH = state.requiredOfficeMinutes / 60
                    val reqM = state.requiredOfficeMinutes % 60
                    Text(
                        "Büro-Stunden: ${officeH}h ${officeM}min / ${reqH}h ${reqM}min",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val hoursProgress = if (state.requiredOfficeMinutes > 0)
                        (state.officeMinutes.toFloat() / state.requiredOfficeMinutes).coerceIn(0f, 1f)
                    else 0f
                    LinearProgressIndicator(
                    progress = { hoursProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = ProgressIndicatorDefaults.linearColor,
                    trackColor = ProgressIndicatorDefaults.linearTrackColor,
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val pctProgress = (quota.officePercent / state.effectiveQuotaPercent.toDouble()).toFloat().coerceIn(0f, 1f)
                    Text(
                        "${"%.1f".format(quota.officePercent)}% Büro (Ziel: ${state.effectiveQuotaPercent}%)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(
                    progress = { pctProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = ProgressIndicatorDefaults.linearColor,
                    trackColor = ProgressIndicatorDefaults.linearTrackColor,
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val dayProgress = quota.officeDays.toFloat() / state.effectiveQuotaMinDays.toFloat()
                    Text(
                        "${quota.officeDays} / ${state.effectiveQuotaMinDays} Büro-Tage",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(
                    progress = { dayProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = ProgressIndicatorDefaults.linearColor,
                    trackColor = ProgressIndicatorDefaults.linearTrackColor,
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val statusText = if (quota.quotaMet) "Quote erfüllt" else "Quote noch nicht erfüllt"
                    val statusColor = if (quota.quotaMet) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    Text(statusText, color = statusColor, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (holidayName == null) {
            // Location selector (default for new blocks)
            item {
                Text("Arbeitsort (Standard)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.selectedLocation == WorkLocation.OFFICE,
                        onClick = { viewModel.setLocation(WorkLocation.OFFICE) },
                        label = { Text("Büro") }
                    )
                    FilterChip(
                        selected = state.selectedLocation == WorkLocation.HOME_OFFICE,
                        onClick = { viewModel.setLocation(WorkLocation.HOME_OFFICE) },
                        label = { Text("Home-Office") }
                    )
                }
            }

            // Day type selector
            item {
                Column {
                    Text("Tagestyp", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val dayTypes = listOf(
                            DayType.WORK to "Arbeitstag",
                            DayType.VACATION to "Urlaub",
                            DayType.SPECIAL_VACATION to "Sonderurlaub",
                            DayType.FLEX_DAY to "Gleittag",
                            DayType.SATURDAY_BONUS to "Samstag+"
                        )
                        dayTypes.forEach { (type, label) ->
                            FilterChip(
                                selected = state.selectedDayType == type,
                                onClick = { viewModel.setDayType(type) },
                                label = { Text(label) }
                            )
                        }
                    }

                    // Explicit save button for non-work types
                    val nonWorkTypes = listOf(DayType.VACATION, DayType.SPECIAL_VACATION, DayType.FLEX_DAY)
                    val savedDayType = state.workDay?.dayType
                    if (state.selectedDayType in nonWorkTypes && state.selectedDayType != savedDayType) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val typeLabel = when (state.selectedDayType) {
                            DayType.VACATION -> "Urlaub"
                            DayType.SPECIAL_VACATION -> "Sonderurlaub"
                            DayType.FLEX_DAY -> "Gleittag"
                            else -> ""
                        }
                        Button(
                            onClick = { viewModel.markAsNonWorkDay(state.selectedDayType) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Als $typeLabel eintragen")
                        }
                    }
                }
            }

            // Clock in/out button — only for today
            item {
                val isWorkDay = state.selectedDayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS)
                if (isWorkDay) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isToday) {
                            Button(
                                onClick = {
                                    if (state.isClockRunning) viewModel.clockOut() else viewModel.clockIn()
                                },
                                modifier = Modifier.weight(1f),
                                colors = if (state.isClockRunning) {
                                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                } else {
                                    ButtonDefaults.buttonColors()
                                }
                            ) {
                                Icon(
                                    if (state.isClockRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (state.isClockRunning) "Ausstempeln" else "Einstempeln")
                            }
                        }

                        OutlinedButton(
                            onClick = { showManualEntry = true },
                            modifier = if (isToday) Modifier else Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Manuell")
                        }
                    }
                }
            }
        }

        // Selected day's work time
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (isToday) "Heutige Arbeitszeit" else "Arbeitszeit",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val hours = state.dayWorkTime.netMinutes / 60
                    val mins = state.dayWorkTime.netMinutes % 60
                    Text(
                        text = "${hours}h ${mins}min (netto)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    val targetHours = state.settings.dailyWorkMinutes / 60
                    val targetMins = state.settings.dailyWorkMinutes % 60
                    Text(
                        text = "Soll: ${targetHours}h ${targetMins}min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (state.dayWorkTime.breakMinutes > 0) {
                        Text(
                            text = "Pause: ${state.dayWorkTime.breakMinutes}min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                }
            }
        }

        // Time blocks list
        if (state.timeBlocks.isNotEmpty()) {
            item {
                Text("Zeitblöcke", style = MaterialTheme.typography.labelLarge)
            }

            items(state.timeBlocks) { block ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingBlock = block }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val startStr = block.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                            val endStr = block.endTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "laufend…"
                            Text(
                                text = "$startStr – $endStr",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            val locationLabel = when (block.location) {
                                WorkLocation.OFFICE -> "Büro"
                                WorkLocation.HOME_OFFICE -> "Home-Office"
                            }
                            Text(
                                text = locationLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            IconButton(onClick = { editingBlock = block }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Bearbeiten",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { viewModel.deleteTimeBlock(block) }) {
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
fun ManualTimeEntryDialog(
    dailyWorkMinutes: Int = 426,
    selectedLocation: WorkLocation = WorkLocation.OFFICE,
    onDismiss: () -> Unit,
    onConfirmStartEnd: (LocalTime, LocalTime, WorkLocation) -> Unit,
    onConfirmDuration: (Int, WorkLocation) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val defaultEnd = LocalTime.of(8, 0).plusMinutes(dailyWorkMinutes.toLong())
    var startText by remember { mutableStateOf("08:00") }
    var endText by remember { mutableStateOf(defaultEnd.format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var durationHours by remember { mutableStateOf((dailyWorkMinutes / 60).toString()) }
    var durationMinutes by remember { mutableStateOf((dailyWorkMinutes % 60).toString()) }
    var dialogLocation by remember { mutableStateOf(selectedLocation) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zeit erfassen") },
        text = {
            Column {
                TabRow(selectedTabIndex = selectedTab) {
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

                // Location selector
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
                        onValueChange = { startText = it },
                        label = { Text("Startzeit (HH:mm)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it },
                        label = { Text("Endzeit (HH:mm)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = durationHours,
                            onValueChange = { durationHours = it },
                            label = { Text("Stunden") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = durationMinutes,
                            onValueChange = { durationMinutes = it },
                            label = { Text("Minuten") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (selectedTab == 0) {
                    try {
                        val start = LocalTime.parse(startText, DateTimeFormatter.ofPattern("HH:mm"))
                        val end = LocalTime.parse(endText, DateTimeFormatter.ofPattern("HH:mm"))
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
    var startText by remember { mutableStateOf(block.startTime.format(fmt)) }
    var endText by remember { mutableStateOf(block.endTime?.format(fmt) ?: "") }
    var dialogLocation by remember { mutableStateOf(block.location) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zeitblock bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Location selector
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
                        onValueChange = { startText = it },
                        label = { Text("Start (HH:mm)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it },
                        label = { Text("Ende (HH:mm)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("laufend") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    val start = LocalTime.parse(startText, DateTimeFormatter.ofPattern("HH:mm"))
                    val end = if (endText.isBlank()) null
                    else LocalTime.parse(endText, DateTimeFormatter.ofPattern("HH:mm"))
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
