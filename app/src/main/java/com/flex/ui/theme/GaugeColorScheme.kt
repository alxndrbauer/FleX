package com.flex.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.flex.domain.model.GaugeColorScheme

/** Provided once in MainActivity from the stored setting; read by CircularWorkGauge. */
val LocalGaugeColorScheme = compositionLocalOf { GaugeColorScheme.THEME }

/** Fixed palette of the classic green gauge (used when CLASSIC_GREEN is selected). */
object ClassicGreenGauge {
    val ring = Color(0xFF55D26F)
    val overtimeBand = Color(0xFF178A36)
    val center = Color(0xFF00502B)
    val track = Color(0xFF0A3D22)
    val text = Color(0xFFE8F5EA)
    val outline = Color(0xFF1B1F1C)
}
