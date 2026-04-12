package com.flex.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flex.ui.analytics.components.FlextimeLineChart
import com.flex.ui.analytics.components.LocationDistributionChart
import com.flex.ui.analytics.components.MonthlyWorkHoursChart
import com.flex.ui.analytics.components.OvertimeLineChart
import com.flex.ui.analytics.components.TimeRangeSelector
import com.flex.ui.analytics.components.WeeklyWorkHoursChart

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Analytics",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            TimeRangeSelector(
                selectedRange = state.timeRange,
                onRangeChanged = viewModel::setTimeRange
            )
        }

        state.analyticsData?.let { data ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Gleitzeit-Verlauf", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        FlextimeLineChart(data = data.flextimeSeries)
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Standort-Verteilung", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        LocationDistributionChart(distribution = data.locationDistribution)
                    }
                }
            }

            data.weekComparison?.let { comparison ->
                if (comparison.hasData) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Wochenvergleich", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    WeekStatColumn("Vorwoche", comparison.previousWeekMinutes)
                                    WeekDeltaColumn(comparison.deltaMinutes)
                                    WeekStatColumn("Diese Woche", comparison.currentWeekMinutes)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Wöchentliche Arbeitszeit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        WeeklyWorkHoursChart(data = data.weeklyHours)
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Monatliche Arbeitszeit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        MonthlyWorkHoursChart(data = data.monthlyHours)
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Überstunden", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OvertimeLineChart(data = data.overtimeSeries)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStatColumn(label: String, minutes: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        val hours = minutes / 60
        val mins = minutes % 60
        Text(
            text = "${hours}h ${mins}m",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WeekDeltaColumn(deltaMinutes: Long) {
    val arrow = if (deltaMinutes >= 0) "▲" else "▼"
    val color = if (deltaMinutes >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val absDelta = kotlin.math.abs(deltaMinutes)
    val hours = absDelta / 60
    val mins = absDelta % 60
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = arrow,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
        Text(
            text = "${hours}h ${mins}m",
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}
