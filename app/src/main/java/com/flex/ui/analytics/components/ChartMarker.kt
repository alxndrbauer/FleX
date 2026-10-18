package com.flex.ui.analytics.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.DashedShape
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent

/**
 * Creates and remembers a floating marker tooltip for interactive chart scrubbing.
 */
@Composable
fun rememberChartMarker(valueSuffix: String = "h"): DefaultCartesianMarker {
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val textColor = MaterialTheme.colorScheme.onSurface
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    val label = rememberTextComponent(
        style = TextStyle(
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        ),
        padding = Insets(horizontal = 8.dp, vertical = 4.dp),
        background = rememberShapeComponent(
            fill = Fill(surfaceColor),
            shape = RoundedCornerShape(8.dp),
            strokeFill = Fill(outlineColor.copy(alpha = 0.5f)),
            strokeThickness = 1.dp
        )
    )

    val guideline = rememberLineComponent(
        fill = Fill(outlineColor.copy(alpha = 0.6f)),
        thickness = 1.5.dp,
        shape = DashedShape(dashLength = 4.dp, gapLength = 4.dp)
    )

    val valueFormatter = remember(valueSuffix) {
        DefaultCartesianMarker.ValueFormatter.default(suffix = valueSuffix)
    }

    return rememberDefaultCartesianMarker(
        label = label,
        valueFormatter = valueFormatter,
        guideline = guideline
    )
}
