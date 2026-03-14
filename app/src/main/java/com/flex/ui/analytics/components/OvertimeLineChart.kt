package com.flex.ui.analytics.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.flex.domain.model.TimeSeriesPoint
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun OvertimeLineChart(
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

    val isDaily = data.map { YearMonth.from(it.date) }.toSet().size == 1
    val xLabels = if (isDaily) {
        data.map { it.date.dayOfMonth.toString() }
    } else {
        data.map { it.date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }

    val bottomFormatter = CartesianValueFormatter { _, x, _ ->
        xLabels.getOrElse(x.toInt()) { x.toInt().toString() }
    }
    val startFormatter = CartesianValueFormatter { _, y, _ ->
        "${String.format("%.1f", y)}h"
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(data) {
        modelProducer.runTransaction {
            lineSeries {
                series(data.map { it.value / 60.0 }) // Convert minutes to hours
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(valueFormatter = startFormatter),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
    )
}
