package com.flex.ui.quota

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

        // Overall quota status - GANZ OBEN
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
                Spacer(modifier = Modifier.height(8.dp))

                val quota = state.quotaStatus
                val officeH = quota.officeMinutes / 60
                val officeM = quota.officeMinutes % 60
                val hoH = quota.homeOfficeMinutes / 60
                val hoM = quota.homeOfficeMinutes % 60

                Text("Büro: ${officeH}h ${officeM}min")
                Text("Home-Office: ${hoH}h ${hoM}min")

                Spacer(modifier = Modifier.height(4.dp))

                // Target hours from fixed monthly target
                val reqH = state.requiredOfficeMinutes / 60
                val reqM = state.requiredOfficeMinutes % 60
                val totalH = state.totalWorkMinutes / 60
                val totalM = state.totalWorkMinutes % 60
                Text(
                    "Ziel: ${reqH}h ${reqM}min (${state.effectiveQuotaPercent}% von ${totalH}h ${totalM}min)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text("Büro-Anteil: ${"%.1f".format(quota.officePercent)}% (Ziel: ${state.effectiveQuotaPercent}%)")
                LinearProgressIndicator(
                    progress = {
                        (quota.officePercent / state.effectiveQuotaPercent.toDouble()).toFloat()
                            .coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = ProgressIndicatorDefaults.linearColor,
                    trackColor = ProgressIndicatorDefaults.linearTrackColor,
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                )

                Spacer(modifier = Modifier.height(4.dp))
                val pctStatus = if (quota.percentQuotaMet) "Erfüllt" else "Nicht erfüllt"
                Text(
                    pctStatus,
                    color = if (quota.percentQuotaMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Office quota (days)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
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
                Spacer(modifier = Modifier.height(8.dp))

                val quota = state.quotaStatus
                Text("Büro-Tage: ${quota.officeDays} / ${state.effectiveQuotaMinDays}")

                val progress = quota.officeDays.toFloat() / state.effectiveQuotaMinDays.toFloat()
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = ProgressIndicatorDefaults.linearColor,
                    trackColor = ProgressIndicatorDefaults.linearTrackColor,
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                )

                Spacer(modifier = Modifier.height(4.dp))
                val dayStatus =
                    if (quota.daysQuotaMet) "Erfüllt" else "Noch ${quota.requiredOfficeDaysForQuota} Tage benötigt"
                Text(
                    dayStatus,
                    color = if (quota.daysQuotaMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )

                if (quota.remainingWorkDays > 0) {
                    Text(
                        "Verbleibende Arbeitstage: ${quota.remainingWorkDays}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Flextime
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Gleitzeit-Saldo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    InfoTooltip(title = TOOLTIP_FLEXTIME_TITLE, text = TOOLTIP_FLEXTIME)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.flextimeBalance.formatDisplay(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (state.flextimeBalance.isPositive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
        }

        // Overtime
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Überstunden-Saldo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    InfoTooltip(title = TOOLTIP_OVERTIME_TITLE, text = TOOLTIP_OVERTIME)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.flextimeBalance.formatOvertime(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (state.flextimeBalance.isOvertimePositive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
        }

        // Vacation dashboard
        Text(
            text = "Urlaubs-Dashboard",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
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
                Spacer(modifier = Modifier.height(8.dp))

                val v = state.vacationInfo
                Text("Jahresanspruch: ${v.annualDays} Tage")
                if (v.carryOverDays > 0) {
                    Text("Resturlaub Vorjahr: ${v.carryOverDays} Tage")
                }
                Text("Genommen: ${v.usedVacationDays} Tage")
                if (v.plannedVacationDays > 0) {
                    Text("Geplant: ${v.plannedVacationDays} Tage")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Verbleibend: ${v.remainingVacationDays} Tage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                val totalUsedAndPlanned = v.usedVacationDays + v.plannedVacationDays
                val totalAvailable = v.annualDays + v.carryOverDays
                val progress =
                    if (totalAvailable > 0) totalUsedAndPlanned.toFloat() / totalAvailable.toFloat() else 0f
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = ProgressIndicatorDefaults.linearColor,
                    trackColor = ProgressIndicatorDefaults.linearTrackColor,
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Kranktage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Dieses Jahr: ${state.sickDays} Tage")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
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
                Spacer(modifier = Modifier.height(8.dp))

                val v = state.vacationInfo
                Text("Anspruch: ${v.specialDays} Tage")
                Text("Genommen: ${v.usedSpecialDays} Tage")
                if (v.plannedSpecialDays > 0) {
                    Text("Geplant: ${v.plannedSpecialDays} Tage")
                }
                Text(
                    "Verbleibend: ${v.remainingSpecialDays} Tage",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Verfällt am 31. Oktober ${state.yearMonth.year}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )

                val today = LocalDate.now()
                if (today.month >= Month.SEPTEMBER && v.remainingSpecialDays > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Achtung: Sonderurlaub verfällt bald!",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
