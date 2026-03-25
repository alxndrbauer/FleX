package com.flex.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

internal fun DrawScope.drawHatchLines(color: Color) {
    val spacing = 8.dp.toPx()
    val strokeWidth = 1.5.dp.toPx()
    val w = size.width
    val h = size.height
    var x = -h
    while (x <= w) {
        drawLine(color, Offset(x, h), Offset(x + h, 0f), strokeWidth)
        x += spacing
    }
}

internal fun Modifier.diagonalHatch(color: Color): Modifier = drawBehind { drawHatchLines(color) }
