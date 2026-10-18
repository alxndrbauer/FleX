package com.flex.ui.home.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.drawscope.rotate
import com.flex.R

/**
 * Modern circular progress gauge inspired by Google Health / Fitbit styling.
 * Features a full 360° progress ring with inner tinted surface, smooth animated sweep,
 * pulsing glow during active recording, and integrated sunset Feierabend pill.
 */
@Composable
fun CircularWorkGauge(
    netTimeText: String,
    targetText: String,
    progress: Float,
    isClockRunning: Boolean,
    dailyGoalReached: Boolean,
    statusLabel: String?,
    statusColor: Color,
    modifier: Modifier = Modifier,
    projectedEndTime: String? = null,
    overtimeMinutes: Int = 0,
    sizeDp: Int = 136
) {
    val animatedTotalProgress by animateFloatAsState(
        targetValue = progress.coerceAtLeast(0f),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "gauge_progress"
    )
    val baseProgress = animatedTotalProgress.coerceIn(0f, 1f)
    val overtimeProgress = (animatedTotalProgress - 1f).coerceIn(0f, 1f)

    // Subtle breathing pulse when recording
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseAlpha by if (isClockRunning) {
        infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )
    } else {
        rememberInfiniteTransition(label = "idle").animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(1000)),
            label = "idle_alpha"
        )
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val innerBgColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)

    val arcColors = if (dailyGoalReached) {
        listOf(secondaryColor, primaryColor)
    } else {
        listOf(primaryColor, tertiaryColor)
    }

    Box(
        modifier = modifier.size(sizeDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(sizeDp.dp)) {
            val strokeWidth = 10.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val arcSize = Size(diameter, diameter)

            // Inner circle subtle background fill
            drawCircle(
                color = innerBgColor,
                radius = (diameter - strokeWidth * 0.8f) / 2f,
                center = center
            )

            // Full 360° Background Track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )

            // Pulsing glow when running
            if (isClockRunning && baseProgress > 0f) {
                drawArc(
                    color = primaryColor.copy(alpha = pulseAlpha * 0.35f),
                    startAngle = -90f,
                    sweepAngle = (baseProgress * 360f).coerceAtLeast(4f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth + 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Base Progress Arc (starts at 12 o'clock = -90°)
            if (baseProgress > 0.005f) {
                val brush = Brush.sweepGradient(
                    0.0f to arcColors.first(),
                    0.85f to arcColors.last(),
                    1.0f to arcColors.last(),
                    center = center
                )
                rotate(degrees = -90f, pivot = center) {
                    drawArc(
                        brush = brush,
                        startAngle = 0f,
                        sweepAngle = if (baseProgress >= 1f) 360f else baseProgress * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = if (baseProgress >= 1f) StrokeCap.Square else StrokeCap.Round)
                    )
                }
            }

            // Overtime Overlapping Arc (Google Health style: continues past 360° on layer 2)
            if (overtimeProgress > 0.005f) {
                val overtimeBrush = Brush.sweepGradient(
                    0.0f to tertiaryColor,
                    0.6f to secondaryColor,
                    1.0f to tertiaryColor,
                    center = center
                )
                rotate(degrees = -90f, pivot = center) {
                    drawArc(
                        brush = overtimeBrush,
                        startAngle = 0f,
                        sweepAngle = (overtimeProgress * 360f).coerceAtMost(360f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth * 1.1f, cap = StrokeCap.Round)
                    )
                }
            }
        }

        // Inner stats column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            // Header / Status
            if (isClockRunning) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (overtimeMinutes > 0) secondaryColor else primaryColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (overtimeMinutes > 0) {
                            val otH = overtimeMinutes / 60
                            val otM = overtimeMinutes % 60
                            if (otH > 0) "+${otH}h ${otM}m" else "+${otM}m"
                        } else "Läuft",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (overtimeMinutes > 0) secondaryColor else primaryColor
                    )
                }
            } else if (overtimeMinutes > 0) {
                val otH = overtimeMinutes / 60
                val otM = overtimeMinutes % 60
                val otText = if (otH > 0) "+${otH}h ${otM}m" else "+${otM}m"
                Text(
                    text = "$otText Überstunden",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = secondaryColor
                )
            } else if (dailyGoalReached) {
                Text(
                    text = "Erreicht ✓",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = secondaryColor
                )
            } else {
                Text(
                    text = "Tagesziel",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Main Net Time
            Text(
                text = netTimeText,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    letterSpacing = (-0.5f).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Soll Target subtitle
            if (targetText.isNotBlank()) {
                Text(
                    text = "von $targetText",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Bottom chip: Feierabend prognosis OR percentage
            if (projectedEndTime != null && isClockRunning) {
                Spacer(modifier = Modifier.height(3.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = secondaryColor.copy(alpha = 0.16f),
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        secondaryColor.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_sunset),
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = secondaryColor
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "~$projectedEndTime",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = secondaryColor
                        )
                    }
                }
            } else if (targetText.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                val pct = (progress * 100).toInt()
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (dailyGoalReached) secondaryColor.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "$pct %",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = if (dailyGoalReached) secondaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
