package com.flex.ui.quota

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flex.ui.components.InfoTooltip
import com.flex.ui.components.TOOLTIP_FLEXTIME
import com.flex.ui.components.TOOLTIP_FLEXTIME_TITLE
import com.flex.ui.components.TOOLTIP_OFFICE_DAYS
import com.flex.ui.components.TOOLTIP_OFFICE_DAYS_TITLE
import com.flex.ui.components.TOOLTIP_OFFICE_QUOTA
import com.flex.ui.components.TOOLTIP_OFFICE_QUOTA_TITLE
import com.flex.ui.components.TOOLTIP_OVERTIME
import com.flex.ui.components.TOOLTIP_OVERTIME_TITLE
import com.flex.ui.components.TOOLTIP_SPECIAL_VACATION
import com.flex.ui.components.TOOLTIP_SPECIAL_VACATION_TITLE
import com.flex.ui.components.TOOLTIP_VACATION
import com.flex.ui.components.TOOLTIP_VACATION_TITLE
import com.flex.ui.theme.HomeOfficeColor
import com.flex.ui.theme.OfficeColor
import com.flex.ui.theme.SickDayColor
import com.flex.ui.theme.SpecialVacationColor
import com.flex.ui.theme.VacationColor
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun QuotaScreen(viewModel: QuotaViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Quoten-Dashboard",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = state.yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Overall quota status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (state.quotaStatus.quotaMet)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        if (state.quotaStatus.quotaMet) "Quote erfüllt" else "Quote nicht erfüllt",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    InfoTooltip(title = TOOLTIP_OFFICE_QUOTA_TITLE, text = TOOLTIP_OFFICE_QUOTA)
                }
                Text(
                    "Mindestens ${state.effectiveQuotaPercent}% Büro ODER ${state.effectiveQuotaMinDays} Büro-Tage",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Office quota (hours)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
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
                            "Büro-Quote (Stunden)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        InfoTooltip(title = TOOLTIP_OFFICE_QUOTA_TITLE, text = TOOLTIP_OFFICE_QUOTA)
                    }
                    StatusBadge(met = state.quotaStatus.percentQuotaMet)
                }
                Spacer(modifier = Modifier.height(8.dp))

                val quota = state.quotaStatus
                val officeH = quota.officeMinutes / 60
                val officeM = quota.officeMinutes % 60
                val hoH = quota.homeOfficeMinutes / 60
                val hoM = quota.homeOfficeMinutes % 60
                val reqH = state.requiredOfficeMinutes / 60
                val reqM = state.requiredOfficeMinutes % 60
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
                        label = "Ziel",
                        value = "${reqH}h ${reqM}m",
                        modifier = Modifier.weight(1f)
                    )
                    QuotaStatItem(
                        label = "Gesamt",
                        value = "${totalH}h ${totalM}m",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Büro-Anteil: ${"%.1f".format(quota.officePercent)}% (Ziel: ${state.effectiveQuotaPercent}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = {
                        (quota.officePercent / state.effectiveQuotaPercent.toDouble()).toFloat()
                            .coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = OfficeColor,
                    trackColor = OfficeColor.copy(alpha = 0.15f),
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                )
            }
        }

        // Office quota (days)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
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
                            "Büro-Quote (Tage)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        InfoTooltip(title = TOOLTIP_OFFICE_DAYS_TITLE, text = TOOLTIP_OFFICE_DAYS)
                    }
                    StatusBadge(met = state.quotaStatus.daysQuotaMet)
                }
                Spacer(modifier = Modifier.height(8.dp))

                val quota = state.quotaStatus
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    QuotaStatItem(
                        label = "Büro-Tage",
                        value = quota.officeDays.toString(),
                        accentColor = OfficeColor,
                        modifier = Modifier.weight(1f)
                    )
                    QuotaStatItem(
                        label = "Ziel",
                        value = state.effectiveQuotaMinDays.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    if (quota.remainingWorkDays > 0) {
                        QuotaStatItem(
                            label = "Verbleibend",
                            value = "${quota.remainingWorkDays} AT",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (!quota.daysQuotaMet && quota.requiredOfficeDaysForQuota > 0) {
                        QuotaStatItem(
                            label = "Noch benötigt",
                            value = "${quota.requiredOfficeDaysForQuota} Tage",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = {
                        (quota.officeDays.toFloat() / state.effectiveQuotaMinDays.toFloat())
                            .coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = OfficeColor,
                    trackColor = OfficeColor.copy(alpha = 0.15f),
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                )
            }
        }

        // Flextime & Overtime side by side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Gleitzeit",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        InfoTooltip(title = TOOLTIP_FLEXTIME_TITLE, text = TOOLTIP_FLEXTIME)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.flextimeBalance.formatDisplay(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (state.flextimeBalance.isPositive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
            }
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Überstunden",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        InfoTooltip(title = TOOLTIP_OVERTIME_TITLE, text = TOOLTIP_OVERTIME)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.flextimeBalance.formatOvertime(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (state.flextimeBalance.isOvertimePositive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Vacation section header
        Text(
            text = "Urlaubs-Dashboard",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Annual vacation card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
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
                    val v = state.vacationInfo
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
                Spacer(modifier = Modifier.height(8.dp))

                val v = state.vacationInfo
                val totalAvailable = v.annualDays + v.carryOverDays
                val totalUsedAndPlanned = v.usedVacationDays + v.plannedVacationDays

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

                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = {
                        if (totalAvailable > 0) totalUsedAndPlanned.toFloat() / totalAvailable.toFloat()
                        else 0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = VacationColor,
                    trackColor = VacationColor.copy(alpha = 0.15f),
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                )
            }
        }

        // Sick days & Special vacation side by side if special days > 0, else sick days full width
        val v = state.vacationInfo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sick days
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Kranktage",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(SickDayColor)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${state.sickDays}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = SickDayColor
                    )
                    Text(
                        text = "Tage dieses Jahr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Special vacation
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Sonderurlaub",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        InfoTooltip(
                            title = TOOLTIP_SPECIAL_VACATION_TITLE,
                            text = TOOLTIP_SPECIAL_VACATION
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(SpecialVacationColor)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${v.remainingSpecialDays}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = SpecialVacationColor
                    )
                    Text(
                        text = "von ${v.specialDays} Tagen übrig",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Special vacation expiry warning (full width, below cards)
        val today = LocalDate.now()
        if (v.remainingSpecialDays > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (today.month >= Month.SEPTEMBER)
                        MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (today.month >= Month.SEPTEMBER)
                            "Achtung: Sonderurlaub verfällt bald!"
                        else
                            "Sonderurlaub verfällt am 31. Oktober ${state.yearMonth.year}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (today.month >= Month.SEPTEMBER) FontWeight.Bold else FontWeight.Normal,
                        color = if (today.month >= Month.SEPTEMBER)
                            MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (v.plannedSpecialDays > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Davon ${v.plannedSpecialDays} Tage geplant, ${v.usedSpecialDays} genommen",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (today.month >= Month.SEPTEMBER)
                                MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun StatusBadge(met: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (met)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = if (met) "Erfüllt" else "Nicht erfüllt",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (met)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

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
