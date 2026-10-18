package com.flex.ui.analytics.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import androidx.compose.foundation.shape.RoundedCornerShape
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.flex.domain.model.TimeSeriesPoint
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MonthlyWorkHoursChart(
    data: List<TimeSeriesPoint>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Text(
            text = "Keine Daten für diesen Zeitraum",
            modifier = modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val activeMonths = data.filter { it.value > 0 }
    val totalHours = data.sumOf { it.value } / 60.0
    val avgHours = if (activeMonths.isNotEmpty()) {
        activeMonths.map { it.value / 60.0 }.average()
    } else {
        0.0
    }

    val xLabels = data.map { it.date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

    val bottomFormatter = CartesianValueFormatter { _, x, _ ->
        xLabels.getOrElse(x.toInt()) { x.toInt().toString() }
    }
    val startFormatter = CartesianValueFormatter { _, y, _ ->
        "${String.format("%.1f", y)}h"
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(data) {
        modelProducer.runTransaction {
            columnModel {
                series(data.map { it.value / 60.0 }) // Convert minutes to hours
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Gesamt: ${String.format("%.1f", totalHours)}h",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Ø ${String.format("%.1f", avgHours)}h / Monat",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    
    val columnColor = MaterialTheme.colorScheme.primary
    val marker = rememberChartMarker(valueSuffix = "h")
    val columnLayer = rememberColumnCartesianLayer(
        columnProvider = ColumnCartesianLayer.ColumnProvider.series(
            rememberLineComponent(
                fill = Fill(columnColor),
                thickness = 16.dp,
                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
            )
        )
    )

    CartesianChartHost(
        chart = rememberCartesianChart(
            columnLayer,
            startAxis = VerticalAxis.rememberStart(valueFormatter = startFormatter),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
            marker = marker
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
    )
}
