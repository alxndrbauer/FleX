package com.flex.ui.year

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flex.domain.model.DayType
import com.flex.domain.model.WorkLocation
import com.flex.ui.theme.FlexDayColor
import com.flex.ui.theme.HomeOfficeColor
import com.flex.ui.theme.OfficeColor
import com.flex.ui.theme.PublicHolidayColor
import com.flex.ui.theme.SaturdayBonusColor
import com.flex.ui.theme.SickDayColor
import com.flex.ui.theme.SpecialVacationColor
import com.flex.ui.theme.VacationColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val CELL_SIZE = 12.dp
private val CELL_SPACING = 2.dp
private val WEEKDAY_LABEL_WIDTH = 20.dp
private val MONTH_LABEL_HEIGHT = 16.dp

private fun cellColor(entry: DayHeatmapEntry?, dailyWorkMinutes: Int, isSurface: Boolean): Color {
    if (entry == null) return Color.Transparent
    return when {
        entry.isPublicHoliday -> PublicHolidayColor.copy(alpha = 0.8f)
        entry.dayType == DayType.VACATION -> VacationColor.copy(alpha = 0.8f)
        entry.dayType == DayType.SPECIAL_VACATION -> SpecialVacationColor.copy(alpha = 0.8f)
        entry.dayType == DayType.FLEX_DAY -> FlexDayColor.copy(alpha = 0.8f)
        entry.dayType == DayType.SICK_DAY -> SickDayColor.copy(alpha = 0.8f)
        entry.dayType == DayType.SATURDAY_BONUS -> SaturdayBonusColor.copy(alpha = 0.8f)
        entry.dayType == DayType.WORK -> {
            val intensity = (entry.netMinutes.toFloat() / (dailyWorkMinutes * 1.2f)).coerceIn(0.15f, 1.0f)
            val base = if (entry.location == WorkLocation.OFFICE) OfficeColor else HomeOfficeColor
            base.copy(alpha = intensity)
        }
        isSurface -> Color(0xFF808080).copy(alpha = 0.12f)
        else -> Color.Transparent
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YearOverviewScreen(
    viewModel: YearOverviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val today = LocalDate.now()

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Year navigation header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = viewModel::previousYear) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Vorheriges Jahr")
                }
                Text(
                    text = state.year.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                IconButton(onClick = viewModel::nextYear) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Nächstes Jahr")
                }
            }
        }

        // Heatmap
        item {
            YearHeatmap(
                year = state.year,
                heatmapEntries = state.heatmapEntries,
                dailyWorkMinutes = state.dailyWorkMinutes,
                today = today
            )
        }

        // Legend
        item {
            HeatmapLegend()
        }

        // Summary card
        item {
            YearSummaryCard(summary = state.summary)
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun YearHeatmap(
    year: Int,
    heatmapEntries: Map<LocalDate, DayHeatmapEntry>,
    dailyWorkMinutes: Int,
    today: LocalDate
) {
    val jan1 = LocalDate.of(year, 1, 1)
    val dec31 = LocalDate.of(year, 12, 31)

    val startDay = jan1.with(DayOfWeek.MONDAY).let {
        if (it.isAfter(jan1)) it.minusWeeks(1) else it
    }
    val endDay = dec31.with(DayOfWeek.SUNDAY).let {
        if (it.isBefore(dec31)) it.plusWeeks(1) else it
    }

    val totalDays = startDay.until(endDay, java.time.temporal.ChronoUnit.DAYS).toInt() + 1
    val totalWeeks = (totalDays + 6) / 7

    val weekDayLabels = listOf("Mo", "", "Mi", "", "Fr", "", "")

    val lazyRowState = rememberLazyListState()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Weekday labels column
        Column(
            modifier = Modifier.padding(top = MONTH_LABEL_HEIGHT + CELL_SPACING)
        ) {
            weekDayLabels.forEach { label ->
                Box(
                    modifier = Modifier
                        .size(WEEKDAY_LABEL_WIDTH, CELL_SIZE)
                        .padding(end = 2.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (label.isNotEmpty()) {
                        Text(
                            text = label,
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                if (label != weekDayLabels.last()) {
                    Spacer(modifier = Modifier.height(CELL_SPACING))
                }
            }
        }

        LazyRow(
            state = lazyRowState,
            horizontalArrangement = Arrangement.spacedBy(CELL_SPACING)
        ) {
            items(totalWeeks) { weekIndex ->
                val weekStart = startDay.plusWeeks(weekIndex.toLong())

                val prevWeekStart = if (weekIndex > 0) startDay.plusWeeks((weekIndex - 1).toLong()) else null
                val showMonthLabel = if (prevWeekStart == null) {
                    true
                } else {
                    val prevWeekMonths = (0..6).map { prevWeekStart.plusDays(it.toLong()) }
                        .filter { it.year == year }
                        .map { it.month }
                        .toSet()
                    (0..6).map { weekStart.plusDays(it.toLong()) }
                        .filter { it.year == year }
                        .any { d -> d.dayOfMonth == 1 || d.month !in prevWeekMonths }
                }

                val monthLabel = if (showMonthLabel) {
                    val inYearDays = (0..6).map { weekStart.plusDays(it.toLong()) }
                        .filter { it.year == year }
                    val firstOfMonth = inYearDays.firstOrNull { it.dayOfMonth == 1 }
                    (firstOfMonth ?: inYearDays.firstOrNull())
                        ?.month?.getDisplayName(TextStyle.SHORT, Locale.GERMAN)
                } else null

                Column(
                    verticalArrangement = Arrangement.spacedBy(CELL_SPACING)
                ) {
                    Box(
                        modifier = Modifier
                            .width(CELL_SIZE)
                            .height(MONTH_LABEL_HEIGHT),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        if (monthLabel != null) {
                            Text(
                                text = monthLabel,
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }

                    for (dayOffset in 0..6) {
                        val date = weekStart.plusDays(dayOffset.toLong())
                        val isInYear = date.year == year
                        val entry = if (isInYear) heatmapEntries[date] else null
                        val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
                        val isSurface = isInYear && entry?.dayType == null && !isWeekend
                        val isWeekendNoEntry = isInYear && isWeekend && entry?.dayType == null && entry?.isPublicHoliday != true

                        val bgColor = when {
                            !isInYear -> Color.Transparent
                            isWeekendNoEntry -> Color(0xFF808080).copy(alpha = 0.08f)
                            else -> cellColor(entry, dailyWorkMinutes, isSurface)
                        }

                        val isToday = date == today
                        val borderModifier = if (isToday) {
                            Modifier.border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(2.dp)
                            )
                        } else {
                            Modifier
                        }

                        Box(
                            modifier = Modifier
                                .size(CELL_SIZE)
                                .background(
                                    color = bgColor,
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .then(borderModifier)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeatmapLegend() {
    val legendItems = listOf(
        "Büro" to OfficeColor,
        "Home-Office" to HomeOfficeColor,
        "Urlaub" to VacationColor,
        "Sonderurlaub" to SpecialVacationColor,
        "Gleittag" to FlexDayColor,
        "Krank" to SickDayColor,
        "Samstag+" to SaturdayBonusColor,
        "Feiertag" to PublicHolidayColor
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        legendItems.forEach { (label, color) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(8.dp)
                        .background(color.copy(alpha = 0.8f), shape = RoundedCornerShape(2.dp))
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun YearSummaryCard(summary: YearSummary) {
    val totalHours = summary.totalWorkMinutes / 60
    val totalMins = summary.totalWorkMinutes % 60

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header with total work days badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Jahresübersicht ${summary.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "${summary.totalWorkDays} Tage",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Group 1: Absences
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SummaryItem(
                    label = "Urlaub",
                    value = summary.vacationDays.toString(),
                    accentColor = VacationColor,
                    modifier = Modifier.weight(1f)
                )
                SummaryItem(
                    label = "Krank",
                    value = summary.sickDays.toString(),
                    accentColor = SickDayColor,
                    modifier = Modifier.weight(1f)
                )
                SummaryItem(
                    label = "Gleittage",
                    value = summary.flexDays.toString(),
                    accentColor = FlexDayColor,
                    modifier = Modifier.weight(1f)
                )
                SummaryItem(
                    label = "Feiertage",
                    value = summary.publicHolidayCount.toString(),
                    accentColor = PublicHolidayColor,
                    modifier = Modifier.weight(1f)
                )
                SummaryItem(
                    label = "Samstag+",
                    value = summary.saturdayBonusDays.toString(),
                    accentColor = SaturdayBonusColor,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()

            // Group 2: Work breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SummaryItem(
                    label = "Büro",
                    value = summary.officeWorkDays.toString(),
                    accentColor = OfficeColor,
                    modifier = Modifier.weight(1f)
                )
                SummaryItem(
                    label = "Home-Office",
                    value = summary.homeOfficeWorkDays.toString(),
                    accentColor = HomeOfficeColor,
                    modifier = Modifier.weight(1f)
                )
                SummaryItem(
                    label = "Stunden",
                    value = "${totalHours}h ${totalMins}m",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (accentColor != null) {
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
