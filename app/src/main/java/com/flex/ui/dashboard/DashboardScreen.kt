package com.flex.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flex.domain.model.FlextimeChartPeriod
import com.flex.domain.model.UpcomingEvent
import com.flex.domain.model.UpcomingEventType
import com.flex.ui.analytics.components.FlextimeLineChart
import com.flex.ui.components.InfoTooltip
import com.flex.ui.components.TOOLTIP_FLEXTIME
import com.flex.ui.components.TOOLTIP_FLEXTIME_TITLE
import com.flex.ui.components.TOOLTIP_OFFICE_QUOTA
import com.flex.ui.components.TOOLTIP_OFFICE_QUOTA_TITLE
import com.flex.ui.components.TOOLTIP_OVERTIME
import com.flex.ui.components.TOOLTIP_OVERTIME_TITLE
import com.flex.ui.components.TOOLTIP_SPECIAL_VACATION
import com.flex.ui.components.TOOLTIP_SPECIAL_VACATION_TITLE
import com.flex.ui.components.TOOLTIP_VACATION
import com.flex.ui.components.TOOLTIP_VACATION_TITLE
import com.flex.ui.theme.BusinessTripColor
import com.flex.ui.theme.FlexDayColor
import com.flex.ui.theme.HomeOfficeColor
import com.flex.ui.theme.OfficeColor
import com.flex.ui.theme.PublicHolidayColor
import com.flex.ui.theme.SickDayColor
import com.flex.ui.theme.SpecialVacationColor
import com.flex.ui.theme.VacationColor
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    var showPayoutConfirmationDialog by remember { mutableStateOf(false) }
    var showRevertConfirmationDialog by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header: Year Selector
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                    IconButton(onClick = viewModel::previousYear) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Vorheriges Jahr")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = state.selectedYear.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.selectedYear != today.year) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                onClick = { viewModel.selectYear(today.year) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    IconButton(onClick = viewModel::nextYear) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Nächstes Jahr")
                    }
                }
        }

        // 1. Current Week Status Card (Only shown if selected year matches current year)
        val currentWeek = state.currentWeekStatus
        if (state.selectedYear == today.year && currentWeek != null) {
            item {
                val workedH = currentWeek.workedMinutes / 60
                val workedM = currentWeek.workedMinutes % 60
                val targetH = currentWeek.targetMinutes / 60
                val targetM = currentWeek.targetMinutes % 60
                val progress = if (currentWeek.targetMinutes > 0) {
                    (currentWeek.workedMinutes.toFloat() / currentWeek.targetMinutes.toFloat()).coerceIn(0f, 1f)
                } else 0f

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Aktuelle Woche (KW ${currentWeek.weekNumber})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${workedH}h ${workedM}m / ${targetH}h ${if (targetM > 0) "${targetM}m" else "00m"}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            QuotaStatItem(
                                label = "Büro",
                                value = "${currentWeek.officeDays} T",
                                accentColor = OfficeColor,
                                modifier = Modifier.weight(1f)
                            )
                            QuotaStatItem(
                                label = "Home-Office",
                                value = "${currentWeek.homeOfficeDays} T",
                                accentColor = HomeOfficeColor,
                                modifier = Modifier.weight(1f)
                            )
                            QuotaStatItem(
                                label = "Gearbeitet",
                                value = "${workedH}h ${workedM}m",
                                modifier = Modifier.weight(1f)
                            )
                            QuotaStatItem(
                                label = "Soll-Woche",
                                value = "${targetH}h ${if (targetM > 0) "${targetM}m" else "00m"}",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 2. Büro-Quote Card with Month Switcher & Forecast (placed directly under Aktuelle Woche)
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Header Row: Title & Status Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Büro-Quote",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            InfoTooltip(title = TOOLTIP_OFFICE_QUOTA_TITLE, text = TOOLTIP_OFFICE_QUOTA)
                        }
                        StatusBadge(
                            isActualMet = state.quotaStatus.quotaMet,
                            isProjectedMet = state.forecast?.isProjectedQuotaMet ?: state.quotaStatus.quotaMet,
                            hasPlannedDays = (state.forecast?.plannedOfficeDays ?: 0) > 0
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Month Selector Sub-bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = viewModel::previousMonth,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Vorheriger Monat", modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = state.selectedYearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = viewModel::nextMonth,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Nächster Monat", modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "Ziel: Mindestens ${state.effectiveQuotaPercent}% Büro ODER ${state.effectiveQuotaMinDays} Büro-Tage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quota KPI Stats Row (Ist-Stand)
                    val quota = state.quotaStatus
                    val officeH = quota.officeMinutes / 60
                    val officeM = quota.officeMinutes % 60
                    val hoH = quota.homeOfficeMinutes / 60
                    val hoM = quota.homeOfficeMinutes % 60
                    val totalH = state.totalWorkMinutes / 60
                    val totalM = state.totalWorkMinutes % 60

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        QuotaStatItem(
                            label = "Büro",
                            value = "${officeH}h ${officeM}m",
                            accentColor = OfficeColor,
                            modifier = Modifier.weight(1f)
                        )
                        QuotaStatItem(
                            label = "Home-Office",
                            value = "${hoH}h ${hoM}m",
                            accentColor = HomeOfficeColor,
                            modifier = Modifier.weight(1f)
                        )
                        QuotaStatItem(
                            label = "Büro-Tage",
                            value = "${quota.officeDays} / ${state.effectiveQuotaMinDays}",
                            modifier = Modifier.weight(1f)
                        )
                        QuotaStatItem(
                            label = "Gesamt",
                            value = "${totalH}h ${totalM}m",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress indicators
                    Text(
                        "Büro-Stundenanteil: ${"%.1f".format(quota.officePercent)}% (Ziel: ${state.effectiveQuotaPercent}%)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = {
                            (quota.officePercent / state.effectiveQuotaPercent.toDouble()).toFloat().coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = OfficeColor,
                        trackColor = OfficeColor.copy(alpha = 0.15f),
                        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Büro-Tage: ${quota.officeDays} von ${state.effectiveQuotaMinDays} Tagen",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (state.effectiveQuotaMinDays > 0)
                                (quota.officeDays.toFloat() / state.effectiveQuotaMinDays.toFloat()).coerceIn(0f, 1f)
                            else 1f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = OfficeColor,
                        trackColor = OfficeColor.copy(alpha = 0.15f),
                        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap
                    )

                    // Forecast & Prognosis Box (inkl. geplanter Tage)
                    val forecast = state.forecast
                    if (forecast != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (forecast.isQuotaMet || forecast.isProjectedQuotaMet) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.tertiary
                                        )
                                        Text(
                                            text = "Prognose (inkl. Planung)",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    if (forecast.plannedOfficeDays > 0 || forecast.plannedHomeOfficeDays > 0) {
                                        Text(
                                            text = "+${forecast.plannedOfficeDays} Büro / +${forecast.plannedHomeOfficeDays} HO geplant",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (forecast.projectedQuotaText.isNotEmpty()) {
                                    Text(
                                        text = forecast.projectedQuotaText,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (!forecast.isQuotaMet && forecast.quotaPacingText.isNotEmpty() && forecast.quotaPacingText != forecast.projectedQuotaText) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = forecast.quotaPacingText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                    )
                                }

                                if (forecast.projectedFlextimeText != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = forecast.projectedFlextimeText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Flextime Balance & Progression Chart Card (Gleitzeit-Verlauf)
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Header Row: Title & Stand
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Gleitzeit & Verlauf",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            InfoTooltip(title = TOOLTIP_FLEXTIME_TITLE, text = TOOLTIP_FLEXTIME)
                        }

                        Text(
                            text = "Stand ${state.selectedYearMonth.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.GERMAN))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Big Balance & Period delta row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = state.flextimeBalance.formatDisplay(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (state.flextimeBalance.isPositive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Aktueller Gleitzeitsaldo",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val currentSeries = if (state.selectedFlextimePeriod == FlextimeChartPeriod.MONTH) {
                            state.monthFlextimeSeries
                        } else {
                            state.yearFlextimeSeries
                        }
                        if (currentSeries.isNotEmpty()) {
                            val lastPoint = currentSeries.last()
                            val hours = lastPoint.value / 60
                            val mins = kotlin.math.abs(lastPoint.value % 60)
                            val sign = if (lastPoint.value >= 0) "+" else "-"
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (lastPoint.value >= 0)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = "$sign${kotlin.math.abs(hours)}h ${mins}m",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (lastPoint.value >= 0)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = state.selectedFlextimePeriod == FlextimeChartPeriod.MONTH,
                            onClick = { viewModel.selectFlextimePeriod(FlextimeChartPeriod.MONTH) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) {
                            Text("Monat (${state.selectedYearMonth.format(DateTimeFormatter.ofPattern("MMM", Locale.GERMAN))})")
                        }
                        SegmentedButton(
                            selected = state.selectedFlextimePeriod == FlextimeChartPeriod.YEAR,
                            onClick = { viewModel.selectFlextimePeriod(FlextimeChartPeriod.YEAR) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) {
                            Text("Jahr (${state.selectedYear})")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val series = if (state.selectedFlextimePeriod == FlextimeChartPeriod.MONTH) {
                        state.monthFlextimeSeries
                    } else {
                        state.yearFlextimeSeries
                    }

                    if (series.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (state.selectedFlextimePeriod == FlextimeChartPeriod.MONTH)
                                    "Keine Arbeitszeiten für ${state.selectedYearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))} erfasst."
                                else
                                    "Keine Arbeitszeiten für das Jahr ${state.selectedYear} erfasst.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        FlextimeLineChart(
                            data = series,
                            modifier = Modifier.fillMaxWidth(),
                            chartHeight = 220.dp
                        )
                    }
                }
            }
        }

        // 2. Upcoming Events ("Demnächst") Card
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Demnächst",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (state.upcomingEvents.isEmpty()) {
                        Text(
                            text = "Keine anstehenden Urlaube oder Termine im restlichen Jahr.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.upcomingEvents.forEachIndexed { index, event ->
                                UpcomingEventRow(event = event)
                                if (index < state.upcomingEvents.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Work Habits Card
        if (state.workHabits.avgStartTime != null || state.workHabits.avgNetMinutes > 0L) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Arbeitszeit-Gewohnheiten",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Durchschnittswerte in ${state.selectedYearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val habits = state.workHabits
                        val netH = habits.avgNetMinutes / 60
                        val netM = habits.avgNetMinutes % 60
                        val startStr = habits.avgStartTime?.format(DateTimeFormatter.ofPattern("HH:mm"))?.plus(" Uhr") ?: "-"
                        val endStr = habits.avgEndTime?.format(DateTimeFormatter.ofPattern("HH:mm"))?.plus(" Uhr") ?: "-"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            QuotaStatItem(
                                label = "Ø Beginn",
                                value = startStr,
                                modifier = Modifier.weight(1f)
                            )
                            QuotaStatItem(
                                label = "Ø Feierabend",
                                value = endStr,
                                modifier = Modifier.weight(1f)
                            )
                            QuotaStatItem(
                                label = "Ø Netto",
                                value = "${netH}h ${netM}m",
                                accentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            QuotaStatItem(
                                label = "Ø Pause",
                                value = "${habits.avgBreakMinutes}m",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 4. Annual Vacation KPI Card
        item {
            val v = state.vacationInfo
            val totalAvailable = v.annualDays + v.carryOverDays
            val totalUsedAndPlanned = v.usedVacationDays + v.plannedVacationDays

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Jahresurlaub",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            InfoTooltip(title = TOOLTIP_VACATION_TITLE, text = TOOLTIP_VACATION)
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = VacationColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${v.remainingVacationDays} Tage übrig",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = VacationColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        QuotaStatItem(
                            label = "Anspruch",
                            value = "${v.annualDays} T",
                            accentColor = VacationColor,
                            modifier = Modifier.weight(1f)
                        )
                        if (v.carryOverDays > 0) {
                            QuotaStatItem(
                                label = "Übertrag",
                                value = "${v.carryOverDays} T",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        QuotaStatItem(
                            label = "Genommen",
                            value = "${v.usedVacationDays} T",
                            modifier = Modifier.weight(1f)
                        )
                        if (v.plannedVacationDays > 0) {
                            QuotaStatItem(
                                label = "Geplant",
                                value = "${v.plannedVacationDays} T",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (totalAvailable > 0) totalUsedAndPlanned.toFloat() / totalAvailable.toFloat()
                            else 0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = VacationColor,
                        trackColor = VacationColor.copy(alpha = 0.15f),
                        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap
                    )
                }
            }
        }

        // 5. Full Absence & Budget Summary Widget
        item {
            val a = state.absenceSummary
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Abwesenheiten im Überblick (${state.selectedYear})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        QuotaStatItem(
                            label = "Urlaub",
                            value = "${a.vacationDays} T",
                            accentColor = VacationColor,
                            modifier = Modifier.weight(1f)
                        )
                        QuotaStatItem(
                            label = "Sonderurlaub",
                            value = "${a.specialVacationDays} T",
                            accentColor = SpecialVacationColor,
                            modifier = Modifier.weight(1f)
                        )
                        QuotaStatItem(
                            label = "Gleittag",
                            value = "${a.flexDays} T",
                            accentColor = FlexDayColor,
                            modifier = Modifier.weight(1f)
                        )
                        QuotaStatItem(
                            label = "Krank",
                            value = "${a.sickDays} T",
                            accentColor = SickDayColor,
                            modifier = Modifier.weight(1f)
                        )
                        QuotaStatItem(
                            label = "Reise",
                            value = "${a.businessTripDays} T",
                            accentColor = BusinessTripColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Overtime KPI Card (Überstunden)
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Überstunden",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            InfoTooltip(title = TOOLTIP_OVERTIME_TITLE, text = TOOLTIP_OVERTIME)
                        }
                        Text(
                            text = state.flextimeBalance.formatOvertime(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (state.flextimeBalance.isOvertimePositive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Stand ${state.selectedYearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Special vacation action/warning card
        item {
            val v = state.vacationInfo
            if (v.isSpecialPaidOut) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Sonderurlaub ausgezahlt",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Die 5 Sonderurlaubstage für ${state.selectedYear} wurden vollständig zur Auszahlung markiert.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val canRevert = !today.isAfter(LocalDate.of(state.selectedYear, 10, 31))
                        if (canRevert) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showRevertConfirmationDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Auszahlung widerrufen", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else if (v.remainingSpecialDays > 0) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        val isUrgent = today.year == state.selectedYear && today.month >= Month.SEPTEMBER
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isUrgent) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = if (isUrgent)
                                    "Achtung: Sonderurlaub verfällt bald!"
                                else
                                    "Sonderurlaub verfällt am 31. Oktober ${state.selectedYear}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isUrgent)
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (v.plannedSpecialDays > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Davon ${v.plannedSpecialDays} Tage geplant, ${v.usedSpecialDays} genommen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (v.canPayoutSpecialDays) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { showPayoutConfirmationDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Sonderurlaub auszahlen (5 Tage)", fontWeight = FontWeight.SemiBold)
                            }
                        } else if (v.usedSpecialDays > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Keine Auszahlung möglich, da bereits Sonderurlaub genommen wurde.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPayoutConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showPayoutConfirmationDialog = false },
            title = { Text("Sonderurlaub auszahlen") },
            text = {
                val v = state.vacationInfo
                if (v.plannedSpecialDays > 0) {
                    Text("Du hast noch ${v.plannedSpecialDays} Sonderurlaubstage im Kalender geplant. Beim Auszahlen werden diese gelöscht und alle 5 Tage als ausgezahlt markiert. Möchtest du fortfahren?")
                } else {
                    Text("Möchtest du alle 5 Tage Sonderurlaub für ${state.selectedYear} auszahlen lassen? Sie stehen danach nicht mehr im Kalender zur Verfügung.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    showPayoutConfirmationDialog = false
                    viewModel.payoutSpecialVacation()
                }) {
                    Text("Auszahlen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPayoutConfirmationDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    if (showRevertConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showRevertConfirmationDialog = false },
            title = { Text("Auszahlung widerrufen") },
            text = {
                Text("Möchtest du die Auszahlung des Sonderurlaubs für ${state.selectedYear} widerrufen? Die 5 Tage stehen dir anschließend wieder im Kalender zur Verfügung.")
            },
            confirmButton = {
                Button(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    showRevertConfirmationDialog = false
                    viewModel.revertSpecialVacationPayout()
                }) {
                    Text("Widerrufen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevertConfirmationDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
private fun UpcomingEventRow(event: UpcomingEvent) {
    val (icon, color) = when (event.eventType) {
        UpcomingEventType.VACATION -> Icons.Default.BeachAccess to VacationColor
        UpcomingEventType.FLEX_DAY -> Icons.Default.Schedule to FlexDayColor
        UpcomingEventType.BUSINESS_TRIP -> Icons.Default.Business to BusinessTripColor
        UpcomingEventType.PUBLIC_HOLIDAY -> Icons.Default.CalendarMonth to PublicHolidayColor
    }

    val dateText = if (event.endDate != null && event.endDate != event.date) {
        val startFormatter = if (event.date.month == event.endDate.month) {
            DateTimeFormatter.ofPattern("d.", Locale.GERMAN)
        } else {
            DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)
        }
        val endFormatter = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)
        "${event.date.format(startFormatter)} – ${event.endDate.format(endFormatter)}"
    } else {
        event.date.format(DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = "${event.daysCount} ${if (event.daysCount == 1) "Tag" else "Tage"}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun StatusBadge(
    isActualMet: Boolean,
    isProjectedMet: Boolean = isActualMet,
    hasPlannedDays: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (label, containerColor, contentColor) = when {
        isActualMet -> Triple(
            "Erfüllt",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        isProjectedMet && hasPlannedDays -> Triple(
            "Geplant erfüllt",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        else -> Triple(
            "Offen",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun StatusBadge(met: Boolean) = StatusBadge(isActualMet = met, isProjectedMet = met)

@Composable
private fun QuotaStatItem(
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
