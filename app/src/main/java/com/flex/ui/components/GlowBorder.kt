package com.flex.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Adds an ultra-subtle, elegant ambient glow effect along with a delicate hairline border
 * to clickable badges, chips, or cards in Material 3.
 *
 * @param color The accent color of the border and glow.
 * @param shape The shape matching the composable's corner radius (e.g. RoundedCornerShape(50) or RoundedCornerShape(14.dp)).
 * @param borderWidth The stroke width of the core border line (default: 0.5.dp).
 * @param glowRadius The spread of the soft halo around the border (default: 1.dp).
 * @param glowAlpha The intensity of the diffuse outer halo (default: 0.06f).
 * @param coreAlpha The opacity of the core border line (default: 0.20f for an ultra-subtle, whisper-quiet accent).
 */
fun Modifier.glowBorder(
    color: Color,
    shape: Shape,
    borderWidth: Dp = 0.5.dp,
    glowRadius: Dp = 1.dp,
    glowAlpha: Float = 0.06f,
    coreAlpha: Float = 0.20f
): Modifier = this.drawWithContent {
    drawContent()
    val outline = shape.createOutline(size, layoutDirection, this)

    // Diffuse outer halo (soft breath of light)
    drawOutline(
        outline = outline,
        color = color.copy(alpha = glowAlpha * 0.45f),
        style = Stroke(width = (borderWidth + glowRadius * 2).toPx())
    )
    // Soft middle glow
    drawOutline(
        outline = outline,
        color = color.copy(alpha = glowAlpha),
        style = Stroke(width = (borderWidth + glowRadius).toPx())
    )
    // Ultra-subtle hairline core border
    drawOutline(
        outline = outline,
        color = color.copy(alpha = coreAlpha),
        style = Stroke(width = borderWidth.toPx())
    )
}
