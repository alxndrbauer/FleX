package com.flex.wear

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

private val WearPrimary          = Color(0xFF4DA3FF)
private val WearOnPrimary        = Color(0xFF002F6C)
private val WearSecondary        = Color(0xFF34A853)
private val WearOnSecondary      = Color(0xFF003918)
private val WearTertiary         = Color(0xFFFFC107)
private val WearOnTertiary       = Color(0xFF3F2800)
private val WearError            = Color(0xFFE53935)
private val WearOnError          = Color(0xFF690005)
private val WearBackground       = Color(0xFF000000)
private val WearOnBackground     = Color(0xFFE0E0E0)
private val WearSurfaceContainer = Color(0xFF1A1C1E)
private val WearOnSurface        = Color(0xFFE2E2E6)
private val WearOnSurfaceVariant = Color(0xFF9E9FA8)

private val FleXWearColorScheme = ColorScheme(
    primary             = WearPrimary,
    onPrimary           = WearOnPrimary,
    secondary           = WearSecondary,
    onSecondary         = WearOnSecondary,
    tertiary            = WearTertiary,
    onTertiary          = WearOnTertiary,
    error               = WearError,
    onError             = WearOnError,
    background          = WearBackground,
    onBackground        = WearOnBackground,
    surfaceContainer    = WearSurfaceContainer,
    onSurface           = WearOnSurface,
    onSurfaceVariant    = WearOnSurfaceVariant,
)

@Composable
fun WearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FleXWearColorScheme,
        content = content
    )
}
