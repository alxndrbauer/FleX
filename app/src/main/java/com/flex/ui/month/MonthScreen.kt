package com.flex.ui.month

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.ProgressIndicatorDefaults
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flex.domain.model.DayType
import com.flex.domain.model.PublicHolidays
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.ui.components.InfoTooltip
import com.flex.ui.components.TOOLTIP_PROGNOSIS
import com.flex.ui.components.TOOLTIP_PROGNOSIS_TITLE
import com.flex.ui.components.formatTimeInput
import com.flex.ui.theme.FlexDayColor
import com.flex.ui.theme.HomeOfficeColor
import com.flex.ui.theme.OfficeColor
import com.flex.ui.theme.PublicHolidayColor
import com.flex.ui.theme.SaturdayBonusColor
import com.flex.ui.theme.SickDayColor
import com.flex.ui.theme.SpecialVacationColor
import com.flex.ui.theme.VacationColor
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

@Composable
fun MonthScreen(viewModel: MonthViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Month navigation header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.previousMonth() }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Vorheriger Monat")
            }
            Text(
                text = state.yearMonth.format(
                    DateTimeFormatter.ofPattern(
                        "MMMM yyyy",
                        Locale.GERMAN
                    )
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = { viewModel.onExportClick() }) {
                Icon(Icons.Default.FileDownload, contentDescription = "Exportieren")
            }
            IconButton(onClick = { viewModel.nextMonth() }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Nächster Monat")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Prognosis card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Prognose",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    InfoTooltip(title = TOOLTIP_PROGNOSIS_TITLE, text = TOOLTIP_PROGNOSIS)
                }
                Spacer(modifier = Modifier.height(4.dp))

                val q = state.prognosisQuota

                // Office hours
                val officeH = state.officeMinutes / 60
                val officeM = state.officeMinutes % 60
                val reqH = state.requiredOfficeMinutes / 60
                val reqM = state.requiredOfficeMinutes % 60
                val totalH = state.totalWorkMinutes / 60
                val totalM = state.totalWorkMinutes % 60
                Text(
                    "Büro-Stunden: ${officeH}h ${officeM}min / ${reqH}h ${reqM}min (${totalH}h ${totalM}min gesamt)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                val hoursProgress = if (state.requiredOfficeMinutes > 0)
                    (state.officeMinutes.toFloat() / state.requiredOfficeMinutes).coerceIn(0f, 1f)
                else 0f
                LinearProgressIndicator(
                    progress = { hoursProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = ProgressIndicatorDefaults.linearColor,
                    trackColor = ProgressIndicatorDefaults.linearTrackColor,
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                )
                Spacer(modifier = Modifier.height(4.dp))

                val pctProg = (q.officePercent / state.effectiveQuotaPercent.toDouble()).toFloat()
                    .coerceIn(0f, 1f)
                val dayProg = q.officeDays.toFloat() / state.effectiveQuotaMinDays.toFloat()

                Text(
                    "${"%.1f".format(q.officePercent)}% Büro (Ziel: ${state.effectiveQuotaPercent}%) | ${q.officeDays}/${state.effectiveQuotaMinDays} Tage",
                    style = MaterialTheme.typography.bodySmall
                )
                LinearProgressIndicator(
                    progress = { pctProg },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = ProgressIndicatorDefaults.linearColor,
                    trackColor = ProgressIndicatorDefaults.linearTrackColor,
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                )
                Spacer(modifier = Modifier.height(2.dp))
                LinearProgressIndicator(
                    progress = { dayProg.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = ProgressIndicatorDefaults.linearColor,
                    trackColor = ProgressIndicatorDefaults.linearTrackColor,
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                )
                Spacer(modifier = Modifier.height(2.dp))

                val statusText = if (q.quotaMet) "Quote erfüllt" else "Quote nicht erfüllt"
                val statusColor =
                    if (q.quotaMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                Text(
                    statusText, color = statusColor, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall
                )

                val workedHours = state.workedMinutesMonth / 60
                val workedMinutes = state.workedMinutesMonth % 60
                val targetHours = state.targetMinutesMonth / 60
                val targetMinutes = state.targetMinutesMonth % 60
                val sign = if (state.differenceMinutesMonth >= 0) "+" else ""
                val diffHours = kotlin.math.abs(state.differenceMinutesMonth) / 60
                val diffMinutes = kotlin.math.abs(state.differenceMinutesMonth) % 60
                Text(
                    "Erbrachte Stunden: ${workedHours}h ${workedMinutes}min (Soll: ${targetHours}h ${targetMinutes}min) | Differenz: $sign${diffHours}h ${diffMinutes}min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // "Planung übernehmen" button
        if (state.hasPlannedDays) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.confirmPlannedDays() },
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

        // Day of week headers
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

        Spacer(modifier = Modifier.height(4.dp))

        // Calendar grid
        val firstDay = state.yearMonth.atDay(1)
        val startOffset = (firstDay.dayOfWeek.value - 1)
        val daysInMonth = state.yearMonth.lengthOfMonth()
        val workDayMap = state.workDays.associateBy { it.date }

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.height(260.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(startOffset) {
                Box(modifier = Modifier.aspectRatio(1f))
            }
            items(daysInMonth) { index ->
                val date = state.yearMonth.atDay(index + 1)
                val workDay = workDayMap[date]
                DayCell(
                    date = date,
                    workDay = workDay,
                    isToday = date == LocalDate.now(),
                    onClick = { viewModel.selectDay(date) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LegendItem(color = OfficeColor, label = "Büro")
            LegendItem(color = HomeOfficeColor, label = "HO")
            LegendItem(color = VacationColor, label = "Urlaub")
            LegendItem(color = FlexDayColor, label = "Gleittag")
            LegendItem(color = SickDayColor, label = "Krank")
            LegendItem(color = PublicHolidayColor, label = "Feiertag")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Entries list
        Text("Einträge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(state.workDays.sortedBy { it.date }) { workDay ->
                WorkDayListItem(
                    workDay = workDay,
                    netMinutes = state.netMinutesByDate[workDay.date] ?: 0,
                    onClick = { viewModel.selectDay(workDay.date) }
                )
            }
        }
    }

    // Export format dialog
    if (state.showExportDialog) {
        val yearMonthStr = state.yearMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        AlertDialog(
            onDismissRequest = { viewModel.onExportDismiss() },
            title = { Text("Monat exportieren") },
            text = { Text("Format wählen:") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onExportDismiss()
                    csvLauncher.launch("flex_$yearMonthStr.csv")
                }) {
                    Text("CSV")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.onExportDismiss()
                        pdfLauncher.launch("flex_$yearMonthStr.pdf")
                    }) {
                        Text("PDF")
                    }
                    TextButton(onClick = { viewModel.onExportDismiss() }) {
                        Text("Abbrechen")
                    }
                }
            }
        )
    }

    // Edit dialog
    state.editingDay?.let { editDay ->
        EditDayDialog(
            workDay = editDay,
            dailyWorkMinutes = state.settings.dailyWorkMinutes,
            onDismiss = { viewModel.clearEditing() },
            onSave = { dayType, note, timeBlocks ->
                viewModel.saveDay(editDay.date, dayType, note, timeBlocks)
            },
            onDelete = if (editDay.id != 0L) {
                { viewModel.deleteDay(editDay) }
            } else null
        )
    }
}

@Composable
fun DayCell(
    date: LocalDate,
    workDay: WorkDay?,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val holidayName = PublicHolidays.getHolidayName(date)
    val bgColor = when {
        holidayName != null -> PublicHolidayColor.copy(alpha = 0.3f)
        workDay == null -> Color.Transparent
        workDay.dayType == DayType.VACATION -> VacationColor.copy(alpha = 0.3f)
        workDay.dayType == DayType.SPECIAL_VACATION -> SpecialVacationColor.copy(alpha = 0.3f)
        workDay.dayType == DayType.FLEX_DAY -> FlexDayColor.copy(alpha = 0.3f)
        workDay.dayType == DayType.SATURDAY_BONUS -> SaturdayBonusColor.copy(alpha = 0.3f)
        workDay.dayType == DayType.SICK_DAY -> SickDayColor.copy(alpha = 0.3f)
        else -> {
            val blocks = workDay.timeBlocks.filter { it.endTime != null }
            if (blocks.isEmpty()) {
                if (workDay.location == WorkLocation.OFFICE) OfficeColor.copy(alpha = 0.3f)
                else HomeOfficeColor.copy(alpha = 0.3f)
            } else {
                var officeMin = 0L; var hoMin = 0L
                for (b in blocks) {
                    val min = java.time.Duration.between(b.startTime, b.endTime!!).toMinutes()
                    if (b.location == WorkLocation.OFFICE) officeMin += min else hoMin += min
                }
                if (officeMin >= hoMin) OfficeColor.copy(alpha = 0.3f) else HomeOfficeColor.copy(alpha = 0.3f)
            }
        }
    }

    val borderMod = if (workDay?.isPlanned == true) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
    } else Modifier

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .then(borderMod)
            .clickable(enabled = holidayName == null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isToday) MaterialTheme.colorScheme.primary
                else if (holidayName != null) PublicHolidayColor
                else MaterialTheme.colorScheme.onSurface
            )
            if (isToday) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
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
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
fun WorkDayListItem(workDay: WorkDay, netMinutes: Long, onClick: () -> Unit) {
    val isWorkType = workDay.dayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS)
    val workBlocks = workDay.timeBlocks.filter { it.endTime != null }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dayName = workDay.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN)
                val dateStr = workDay.date.format(DateTimeFormatter.ofPattern("d. MMM"))
                Text("$dayName, $dateStr", style = MaterialTheme.typography.bodyMedium)

                val typeLabel = when (workDay.dayType) {
                    DayType.WORK, DayType.SATURDAY_BONUS -> {
                        val blocks = workDay.timeBlocks.filter { it.endTime != null }
                        if (blocks.isEmpty()) {
                            if (workDay.location == WorkLocation.OFFICE) "Büro" else "HO"
                        } else {
                            var officeMin = 0L; var hoMin = 0L
                            for (b in blocks) {
                                val min = java.time.Duration.between(b.startTime, b.endTime!!).toMinutes()
                                if (b.location == WorkLocation.OFFICE) officeMin += min else hoMin += min
                            }
                            when {
                                officeMin > 0 && hoMin > 0 -> "Gemischt"
                                officeMin > 0 -> "Büro"
                                else -> "HO"
                            }
                        }
                    }
                    DayType.VACATION -> "Urlaub"
                    DayType.SPECIAL_VACATION -> "Sonderurlaub"
                    DayType.FLEX_DAY -> "Gleittag"
                    DayType.SICK_DAY -> "Krank"
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (netMinutes > 0) {
                        Text(
                            "${netMinutes / 60}h ${netMinutes % 60}min",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        typeLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
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
                    Text(
                        "$startStr – $endStr · $locationLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
    onDismiss: () -> Unit,
    onSave: (DayType, String?, List<TimeBlockInput>) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var dayType by remember { mutableStateOf(workDay.dayType) }
    var note by remember { mutableStateOf(workDay.note ?: "") }

    val dialogState = calculateEditDayDialogState(workDay, dailyWorkMinutes)

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
            Text(workDay.date.format(DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)))
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
                        selected = dayType == DayType.WORK, onClick = { dayType = DayType.WORK },
                        label = { Text("Arbeitstag") })
                    FilterChip(
                        selected = dayType == DayType.VACATION,
                        onClick = { dayType = DayType.VACATION },
                        label = { Text("Urlaub") })
                    FilterChip(
                        selected = dayType == DayType.SPECIAL_VACATION,
                        onClick = { dayType = DayType.SPECIAL_VACATION },
                        label = { Text("Sonderurlaub") })
                    FilterChip(
                        selected = dayType == DayType.FLEX_DAY,
                        onClick = { dayType = DayType.FLEX_DAY },
                        label = { Text("Gleittag") })
                    FilterChip(
                        selected = dayType == DayType.SATURDAY_BONUS,
                        onClick = { dayType = DayType.SATURDAY_BONUS },
                        label = { Text("Samstag+") })
                    FilterChip(
                        selected = dayType == DayType.SICK_DAY,
                        onClick = { dayType = DayType.SICK_DAY },
                        label = { Text("Krank") })
                }

                // Time entry — only for work day types
                if (dayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS)) {
                    PrimaryTabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0, onClick = { selectedTab = 0 },
                            text = { Text("Start / Ende") })
                        Tab(
                            selected = selectedTab == 1, onClick = { selectedTab = 1 },
                            text = { Text("Gesamtzeit") })
                    }

                    // Per-block editors
                    blocks.forEachIndexed { index, block ->
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Block ${index + 1}",
                                style = MaterialTheme.typography.labelMedium
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = block.location == WorkLocation.OFFICE,
                                onClick = { block.location = WorkLocation.OFFICE },
                                label = { Text("Büro") }
                            )
                            FilterChip(
                                selected = block.location == WorkLocation.HOME_OFFICE,
                                onClick = { block.location = WorkLocation.HOME_OFFICE },
                                label = { Text("HO") }
                            )
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
                            val lastLocation = blocks.lastOrNull()?.location ?: WorkLocation.HOME_OFFICE
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
                val timeBlockInputs = if (dayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS)) {
                    blocks.mapNotNull { block ->
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
                                TimeBlockInput(start, end, block.location, isDuration = false)
                            } catch (_: Exception) {
                                null
                            }
                        } else {
                            val h = block.durationHours.toIntOrNull() ?: 0
                            val m = block.durationMinutes.toIntOrNull() ?: 0
                            val total = h * 60 + m
                            if (total > 0) {
                                val start = LocalTime.of(8, 0)
                                TimeBlockInput(
                                    start,
                                    start.plusMinutes(total.toLong()),
                                    block.location,
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
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Löschen", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Abbrechen")
                }
            }
        }
    )
}
