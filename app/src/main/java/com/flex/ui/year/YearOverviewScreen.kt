package com.flex.ui.year

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import com.flex.ui.components.glowBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flex.domain.model.DayType
import com.flex.domain.model.WorkLocation
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val CELL_SIZE = 14.dp
private val CELL_SPACING = 2.dp
private val WEEKDAY_LABEL_WIDTH = 28.dp
private val MONTH_LABEL_HEIGHT = 18.dp

internal fun cellColor(entry: DayHeatmapEntry?, dailyWorkMinutes: Int, isSurface: Boolean): Color {
    if (entry == null) return Color.Transparent
    return when {
        entry.isPublicHoliday -> PublicHolidayColor.copy(alpha = 0.8f)
        entry.dayType == DayType.VACATION -> VacationColor.copy(alpha = 0.8f)
        entry.dayType == DayType.SPECIAL_VACATION -> SpecialVacationColor.copy(alpha = 0.8f)
        entry.dayType == DayType.FLEX_DAY -> FlexDayColor.copy(alpha = 0.8f)
        entry.dayType == DayType.SICK_DAY -> SickDayColor.copy(alpha = 0.8f)
        entry.dayType == DayType.SATURDAY_BONUS -> SaturdayBonusColor.copy(alpha = 0.8f)
        entry.dayType == DayType.BUSINESS_TRIP -> BusinessTripColor.copy(alpha = 0.8f)
        entry.dayType == DayType.OVERTIME_DAY -> OvertimeDayColor.copy(alpha = 0.8f)
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
    viewModel: YearOverviewViewModel = hiltViewModel(),
    onNavigateToMonth: (YearMonth) -> Unit = {},
    onNavigateToPlanning: (YearMonth) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val today = LocalDate.now()
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Modern Year navigation header
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.previousYear()
                        }
                    ) {
                        Icon(
                            Icons.Default.ChevronLeft,
                            contentDescription = "Vorheriges Jahr",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = state.year.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (state.year != today.year) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.selectYear(today.year)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Today,
                                        contentDescription = "Aktuelles Jahr",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Heute",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.nextYear()
                        }
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Nächstes Jahr",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Modern Heatmap Card
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "Jahres-Heatmap",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    YearHeatmap(
                        year = state.year,
                        heatmapEntries = state.heatmapEntries,
                        dailyWorkMinutes = state.dailyWorkMinutes,
                        today = today
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        thickness = 0.5.dp
                    )

                    HeatmapLegend()
                }
            }
        }

        // Modern Year Summary KPI Card
        item {
            ModernYearSummaryCard(summary = state.summary)
        }

        // 12 Months Navigation & Breakdown
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Monatsübersicht ${state.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Tippe auf einen Monat für Monatsdetails oder Planung",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 12 Month Tiles Grid
        items(
            items = state.monthSummaries,
            key = { it.yearMonth.toString() }
        ) { monthTile ->
            MonthOverviewTile(
                tile = monthTile,
                currentMonth = java.time.YearMonth.from(today),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (monthTile.isFuture) {
                        onNavigateToPlanning(monthTile.yearMonth)
                    } else {
                        onNavigateToMonth(monthTile.yearMonth)
                    }
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ModernYearSummaryCard(summary: YearSummary) {
    val totalHours = summary.totalWorkMinutes / 60
    val totalMins = summary.totalWorkMinutes % 60
    val totalOfficeHo = summary.officeWorkDays + summary.homeOfficeWorkDays
    val officeRatio = if (totalOfficeHo > 0) summary.officeWorkDays.toFloat() / totalOfficeHo else 0.5f

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Jahresstatistik ${summary.year}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Zusammenfassung aller erfassten Tage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${summary.totalWorkDays} Arbeitstage",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Key KPI Metric Row
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Gesamtarbeitszeit",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${totalHours}h ${totalMins}m",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Büro / Home-Office",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(OfficeColor)
                                )
                                Text(
                                    text = "${summary.officeWorkDays} T",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "•",
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(HomeOfficeColor)
                                )
                                Text(
                                    text = "${summary.homeOfficeWorkDays} T",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Visual distribution bar
            if (totalOfficeHo > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Büro ${(officeRatio * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = OfficeColor
                        )
                        Text(
                            text = "Home-Office ${((1f - officeRatio) * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = HomeOfficeColor
                        )
                    }
                    LinearProgressIndicator(
                        progress = { officeRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = OfficeColor,
                        trackColor = HomeOfficeColor,
                        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap
                    )
                }
            }

            // Absences & Special days section
            Text(
                text = "Abwesenheiten & Freitage",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ModernSummaryBadge(
                    label = "Urlaub",
                    value = "${summary.vacationDays}",
                    color = VacationColor,
                    modifier = Modifier.weight(1f)
                )
                ModernSummaryBadge(
                    label = "Krank",
                    value = "${summary.sickDays}",
                    color = SickDayColor,
                    modifier = Modifier.weight(1f)
                )
                ModernSummaryBadge(
                    label = "Gleittage",
                    value = "${summary.flexDays}",
                    color = FlexDayColor,
                    modifier = Modifier.weight(1f)
                )
                ModernSummaryBadge(
                    label = "Feiertage",
                    value = "${summary.publicHolidayCount}",
                    color = PublicHolidayColor,
                    modifier = Modifier.weight(1f)
                )
            }

            // Secondary row: Saturday Bonus & Business Trip if applicable
            if (summary.saturdayBonusDays > 0 || summary.businessTripDays > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (summary.saturdayBonusDays > 0) {
                        ModernSummaryBadge(
                            label = "Samstag+",
                            value = "${summary.saturdayBonusDays}",
                            color = SaturdayBonusColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (summary.businessTripDays > 0) {
                        ModernSummaryBadge(
                            label = "Dienstreise",
                            value = "${summary.businessTripDays}",
                            color = BusinessTripColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (summary.specialVacationPaidOut) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SpecialVacationColor.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(SpecialVacationColor)
                        )
                        Text(
                            text = "Sonderurlaub: 5 Tage ausgezahlt",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else if (summary.specialVacationDays > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SpecialVacationColor.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(SpecialVacationColor)
                        )
                        Text(
                            text = "Sonderurlaub: ${summary.specialVacationDays} Tage genommen",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthOverviewTile(
    tile: YearMonthTileData,
    currentMonth: YearMonth,
    onClick: () -> Unit
) {
    val monthName = tile.yearMonth.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)
    val isCurrent = tile.yearMonth == currentMonth
    val totalH = tile.totalWorkMinutes / 60
    val totalM = tile.totalWorkMinutes % 60

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isCurrent) Modifier.glowBorder(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                ) else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.surfaceContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 2.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = monthName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val (badgeText, badgeColor, badgeContentColor) = when {
                        isCurrent -> Triple("Aktuell", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                        tile.isFuture -> Triple("Zukunft", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                        else -> Triple("Abgeschlossen", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = badgeColor
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeContentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                // Stats line
                if (tile.totalWorkMinutes > 0L) {
                    Text(
                        text = "${totalH}h ${totalM}m • ${tile.workDays} Arbeitstage",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else if (tile.isFuture) {
                    Text(
                        text = "Keine erfassten Zeiten • Zur Planung",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "0h 00m gearbeitet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (tile.officeDays > 0 || tile.homeOfficeDays > 0) {
                        Text(
                            text = "🏢 ${tile.officeDays} T   🏠 ${tile.homeOfficeDays} T",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (tile.vacationDays > 0) {
                        Text(
                            text = "🏖️ ${tile.vacationDays} T Urlaub",
                            style = MaterialTheme.typography.labelSmall,
                            color = VacationColor
                        )
                    }
                }
            }

            // Trailing action hint
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (tile.isFuture) Icons.Default.EditCalendar else Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (tile.isFuture) "Planen" else "Öffnen",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernSummaryBadge(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
    val currentMonthWeekIndex = if (year == today.year) {
        val currentMonthStart = LocalDate.of(year, today.month, 1)
        val currentMonthWeekStart = currentMonthStart.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        )
        java.time.temporal.ChronoUnit.WEEKS
            .between(startDay, currentMonthWeekStart)
            .toInt()
            .coerceIn(0, totalWeeks - 1)
    } else {
        0
    }
    val lazyRowState = rememberLazyListState(initialFirstVisibleItemIndex = currentMonthWeekIndex)

    LaunchedEffect(year, today.year, today.month) {
        lazyRowState.scrollToItem(currentMonthWeekIndex)
    }

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
                    modifier = Modifier.zIndex(if (monthLabel != null) 1f else 0f),
                    verticalArrangement = Arrangement.spacedBy(CELL_SPACING)
                ) {
                    Box(
                        modifier = Modifier
                            .width(CELL_SIZE)
                            .height(MONTH_LABEL_HEIGHT),
                        contentAlignment = Alignment.TopStart
                    ) {
                        if (monthLabel != null) {
                            Text(
                                text = monthLabel,
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                textAlign = TextAlign.Start,
                                maxLines = 1,
                                modifier = Modifier.wrapContentWidth(unbounded = true)
                            )
                        }
                    }

                    for (dayOffset in 0..6) {
                        val date = weekStart.plusDays(dayOffset.toLong())
                        val isInYear = date.year == year
                        val entry = if (isInYear) heatmapEntries[date] else null
                        val isOffDay = entry?.let { !it.isScheduledWorkDay }
                            ?: (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY)
                        val isSurface = isInYear && entry?.dayType == null && !isOffDay
                        val isOffDayNoEntry = isInYear && isOffDay && entry?.dayType == null && entry?.isPublicHoliday != true

                        val targetMinutes = entry?.dailyTargetMinutes ?: dailyWorkMinutes
                        val bgColor = when {
                            !isInYear -> Color.Transparent
                            isOffDayNoEntry -> Color(0xFF808080).copy(alpha = 0.08f)
                            else -> cellColor(entry, targetMinutes, isSurface)
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
        "Dienstgang / Dienstreise" to BusinessTripColor,
        "Urlaub" to VacationColor,
        "Sonderurlaub" to SpecialVacationColor,
        "Gleittag" to FlexDayColor,
        "Krank" to SickDayColor,
        "Samstag+" to SaturdayBonusColor,
        "Überstunden" to OvertimeDayColor,
        "Feiertag" to PublicHolidayColor
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        legendItems.forEach { (label, color) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.85f))
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
