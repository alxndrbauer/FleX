package com.flex.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Blue = Color(0xFF1A73E8)
private val BlueLight = Color(0xFF4DA3FF)
private val Green = Color(0xFF34A853)
private val Orange = Color(0xFFFF9800)
private val Yellow = Color(0xFFFFC107)

val OfficeColor = Color(0xFF1A73E8)
val HomeOfficeColor = Color(0xFF34A853)
val VacationColor = Color(0xFFFFC107)
val SpecialVacationColor = Color(0xFFFF9800)
val FlexDayColor = Color(0xFF9E9E9E)
val SaturdayBonusColor = Color(0xFFE53935)
val PublicHolidayColor = Color(0xFFE91E63)

private val LightColorScheme = lightColorScheme(
    primary = Blue,
    secondary = Green,
    tertiary = Orange,
)

private val DarkColorScheme = darkColorScheme(
    primary = BlueLight,
    secondary = Green,
    tertiary = Orange,
)

@Composable
fun FlexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
