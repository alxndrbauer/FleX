package com.flex.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flex.ui.navigation.LocalFloatingNavVisible

/**
 * Modern Material 3 styled SnackbarHost matching the app's theme and surface hierarchy.
 * Automatically animates its bottom padding so it floats cleanly above the FloatingNavigationBar.
 */
@Composable
fun FlexSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    extraBottomPadding: Dp = 0.dp
) {
    val isNavVisible = LocalFloatingNavVisible.current
    val navBottomPadding by animateDpAsState(
        targetValue = if (isNavVisible) 88.dp else 16.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "snackbar_nav_padding"
    )

    SnackbarHost(
        hostState = hostState,
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = navBottomPadding + extraBottomPadding)
    ) { snackbarData ->
        Snackbar(
            snackbarData = snackbarData,
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            actionColor = MaterialTheme.colorScheme.primary,
            actionContentColor = MaterialTheme.colorScheme.primary,
            dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                RoundedCornerShape(16.dp)
            )
        )
    }
}
