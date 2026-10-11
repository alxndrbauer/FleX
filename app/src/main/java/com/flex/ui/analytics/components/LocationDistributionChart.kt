package com.flex.ui.analytics.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flex.domain.model.LocationDistribution
import com.flex.ui.theme.HomeOfficeColor
import com.flex.ui.theme.OfficeColor

@Composable
fun LocationDistributionChart(
    distribution: LocationDistribution,
    modifier: Modifier = Modifier
) {
    val totalMinutes = distribution.officeMinutes + distribution.homeOfficeMinutes

    if (totalMinutes == 0L) {
        Text(
            text = "Keine Daten für diesen Zeitraum",
            modifier = modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val officeHours = distribution.officeMinutes / 60
    val officeMins = distribution.officeMinutes % 60
    val hoHours = distribution.homeOfficeMinutes / 60
    val hoMins = distribution.homeOfficeMinutes % 60

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Office percentage
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Büro",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${officeHours}h ${officeMins}m (${String.format("%.1f", distribution.officePercent)}%)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = OfficeColor
            )
        }
        LinearProgressIndicator(
            progress = { (distribution.officePercent / 100.0).toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            color = OfficeColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        // Home-Office percentage
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Home-Office",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${hoHours}h ${hoMins}m (${String.format("%.1f", distribution.homeOfficePercent)}%)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = HomeOfficeColor
            )
        }
        LinearProgressIndicator(
            progress = { (distribution.homeOfficePercent / 100.0).toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            color = HomeOfficeColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
