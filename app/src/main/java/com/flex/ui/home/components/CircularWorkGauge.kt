package com.flex.ui.home.components

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flex.R

/**
 * Modern circular progress gauge inspired by Google Health / Fitbit styling.
 * Features a thick 360° progress ring with inner tinted surface, smooth animated sweep,
 * hours embedded directly into the ring stroke, overtime arc with cutout and crisp border,
 * Google Health style overtime badge at 6 o'clock, and center hero percentage with sunset pill.
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
    sizeDp: Int = 152
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
    val cardBgColor = MaterialTheme.colorScheme.surfaceContainerLow

    // Google Health styled colors:
    val healthGoalReachedColor = Color(0xFF0F9D58) // Deep emerald green
    val healthOvertimeColor = Color(0xFF25D366)    // Vibrant neon mint green
    val healthOvertimeEndColor = Color(0xFF34D399) // Smooth mint transition
    val innerBgColor = if (dailyGoalReached) {
        Color(0xFF0F9D58).copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.50f)
    }

    val arcColors = if (dailyGoalReached) {
        listOf(healthGoalReachedColor, healthGoalReachedColor)
    } else {
        listOf(primaryColor, tertiaryColor)
    }

    val otTextPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    Box(
        modifier = modifier.size(sizeDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(sizeDp.dp)) {
            // Normal ring and overlapping ring have the exact same stroke width
            val strokeWidth = 17.5.dp.toPx()
            val borderWidth = strokeWidth + 2.5.dp.toPx()
            val cutoutWidth = borderWidth + 2.dp.toPx()

            // Diameter matches outer dimension so ring touches top and bottom line of cards exactly
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            val overtimeSweep = (overtimeProgress * 360f).coerceAtMost(360f)

            // Inner circle subtle background fill
            drawCircle(
                color = innerBgColor,
                radius = (diameter - strokeWidth) / 2f - 1.dp.toPx(),
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

            // Pulsing glow when running and not yet reached goal
            if (isClockRunning && baseProgress > 0f && !dailyGoalReached) {
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
                if (dailyGoalReached) {
                    // Full 360° closed emerald ring
                    drawArc(
                        color = healthGoalReachedColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )
                } else {
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
                            sweepAngle = baseProgress * 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            // Overtime Overlapping Arc (Layer 2: exact same width as normal ring + crisp border & cutout)
            if (overtimeProgress > 0.005f || overtimeMinutes > 0) {
                val sweep = (overtimeProgress * 360f).coerceIn(42f, 360f)
                val start = -105f

                // 1. Cutout layer in card background color (separates overlapping ring from base ring with a gap)
                drawArc(
                    color = cardBgColor,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = cutoutWidth, cap = StrokeCap.Round)
                )

                // 2. Crisp border outline (Rahmen um das Bogen-Ende und die Kanten)
                drawArc(
                    color = Color.White.copy(alpha = 0.95f),
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = borderWidth, cap = StrokeCap.Round)
                )

                // 3. Core overtime arc (exact same width as base ring)
                val overtimeBrush = Brush.sweepGradient(
                    0.0f to healthOvertimeColor,
                    0.6f to healthOvertimeEndColor,
                    1.0f to healthOvertimeColor,
                    center = center
                )
                rotate(degrees = start, pivot = center) {
                    drawArc(
                        brush = overtimeBrush,
                        startAngle = 0f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // 4. Subtle live pulse at tip if running in overtime
                if (isClockRunning) {
                    drawArc(
                        color = healthOvertimeColor.copy(alpha = pulseAlpha * 0.35f),
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth + 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // 5. Overtime text: curved inside Layer 2 along the stroke centerline (strictly 2 layers, no extra badge)
                if (overtimeMinutes > 0) {
                    val otH = overtimeMinutes / 60
                    val otM = overtimeMinutes % 60
                    val otText = if (otH > 0) "+${otH}h ${otM}m" else "+${otM}m"
                    otTextPaint.textSize = 10.sp.toPx()
                    otTextPaint.color = android.graphics.Color.parseColor("#063319") // High-contrast dark emerald on vibrant mint
                    otTextPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    otTextPaint.textAlign = Paint.Align.LEFT

                    // Center text at the top of the ring (~12 o'clock, -88°)
                    val centerAngle = -88f
                    val textArcPath = Path().apply {
                        addArc(
                            topLeft.x, topLeft.y,
                            topLeft.x + diameter, topLeft.y + diameter,
                            centerAngle - 45f, 90f
                        )
                    }
                    val arcLen = (90f / 360f) * (Math.PI.toFloat() * diameter)
                    val textWidth = otTextPaint.measureText(otText)
                    val hOffset = (arcLen - textWidth) / 2f
                    val fm = otTextPaint.fontMetrics
                    val vOffset = -(fm.ascent + fm.descent) / 2f

                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawTextOnPath(
                            otText,
                            textArcPath,
                            hOffset,
                            vOffset,
                            otTextPaint
                        )
                    }
                }
            }
        }

        // Inner stats column: Main worked time + target + Feierabend sunset chip or percentage
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            // Main worked net time
            Text(
                text = netTimeText,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp,
                    letterSpacing = (-0.5f).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Target subtitle
            if (targetText.isNotBlank()) {
                Text(
                    text = "von $targetText",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Bottom chip: Feierabend prognosis OR percentage
            if (projectedEndTime != null && isClockRunning) {
                Spacer(modifier = Modifier.height(3.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = secondaryColor.copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
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
            } else if (dailyGoalReached) {
                Spacer(modifier = Modifier.height(2.dp))
                val pct = (progress * 100).toInt()
                Surface(
                    shape = RoundedCornerShape(50),
                    color = secondaryColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "$pct % ✓",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = secondaryColor,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp)
                    )
                }
            } else if (isClockRunning) {
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = primaryColor.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(primaryColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Läuft",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = primaryColor
                        )
                    }
                }
            } else if (targetText.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                val pct = (progress * 100).toInt()
                Surface(
                    shape = RoundedCornerShape(50),
                    color = primaryColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "$pct %",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = primaryColor,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp)
                    )
                }
            }
        }
    }
}
