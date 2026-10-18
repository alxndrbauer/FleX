package com.flex.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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
val SickDayColor = Color(0xFFFF8C00)
val OvertimeDayColor = Color(0xFF9C27B0)
val BusinessTripColor = Color(0xFF00897B)

private val LightColorScheme = lightColorScheme(
    primary = Blue,
    secondary = Green,
    tertiary = Orange,
    surface = Color(0xFFFAF9FD),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F3F7),
    surfaceContainer = Color(0xFFEEEEF2),
    surfaceContainerHigh = Color(0xFFE8E8EC),
    surfaceContainerHighest = Color(0xFFE2E2E6),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
)

private val DarkColorScheme = darkColorScheme(
    primary = BlueLight,
    secondary = Green,
    tertiary = Orange,
    surface = Color(0xFF121316),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color(0xFF0D0E11),
    surfaceContainerLow = Color(0xFF1A1C1E),
    surfaceContainer = Color(0xFF1E2023),
    surfaceContainerHigh = Color(0xFF282A2D),
    surfaceContainerHighest = Color(0xFF333538),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
)

private val AmoledColorScheme = darkColorScheme(
    primary = BlueLight,
    onPrimary = Color.Black,
    secondary = Green,
    onSecondary = Color.Black,
    tertiary = Orange,
    background = Color.Black,
    onBackground = Color(0xFFE2E2E6),
    surface = Color.Black,
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF141416),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF242424),
    outline = Color(0xFF55575F),
    outlineVariant = Color(0xFF2E3036),
)

val FlexShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FlexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        amoled -> AmoledColorScheme
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = FlexShapes,
        content = content
    )
}
