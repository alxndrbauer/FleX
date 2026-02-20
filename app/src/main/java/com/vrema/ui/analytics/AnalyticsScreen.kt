package com.vrema.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vrema.ui.analytics.components.FlextimeLineChart
import com.vrema.ui.analytics.components.LocationDistributionChart
import com.vrema.ui.analytics.components.MonthlyWorkHoursChart
import com.vrema.ui.analytics.components.OvertimeLineChart
import com.vrema.ui.analytics.components.TimeRangeSelector
import com.vrema.ui.analytics.components.WeeklyWorkHoursChart

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
                        Text("Gleitzeit-Verlauf", style = MaterialTheme.typography.titleSmall)
                        FlextimeLineChart(data = data.flextimeSeries)
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Überstunden", style = MaterialTheme.typography.titleSmall)
                        OvertimeLineChart(data = data.overtimeSeries)
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Standort-Verteilung", style = MaterialTheme.typography.titleSmall)
                        LocationDistributionChart(distribution = data.locationDistribution)
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Wöchentliche Arbeitszeit", style = MaterialTheme.typography.titleSmall)
                        WeeklyWorkHoursChart(data = data.weeklyHours)
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Monatliche Arbeitszeit", style = MaterialTheme.typography.titleSmall)
                        MonthlyWorkHoursChart(data = data.monthlyHours)
                    }
                }
            }
        }
    }
}
