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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flex.R

/** Stroke width of the work ring. */
private val strokeWidthDp = 24.dp
/** Width of the overtime band as a share of the ring width; it is centred on the ring, inset evenly from both edges. */
private val overtimeBandRatio = 0.6f
/** Outline on both edges of the light ring over the overtime part, wrapping around its rounded tip. */
private val overtimeOutlineWidth = 3.dp

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

    // Theme palette: primary ring, tertiary overtime band (contrasts with primary), primaryContainer centre
    val colors = MaterialTheme.colorScheme
    val ringColor = colors.primary
    val overtimeBandColor = colors.tertiary
    val overtimeOutlineColor = colors.surface
    val centerColor = colors.primaryContainer
    val trackColor = colors.surfaceContainerHighest
    val textColor = colors.onPrimaryContainer
    val overtimeTextColor = colors.onTertiary

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
            val strokeWidth = strokeWidthDp.toPx()
            // Ring fills the layout box, so its outer edge touches the box edges
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            // Inner circle: dark green centre
            drawCircle(
                color = centerColor,
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
                    color = ringColor.copy(alpha = pulseAlpha * 0.35f),
                    startAngle = -90f,
                    sweepAngle = (baseProgress * 360f).coerceAtLeast(4f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth + 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Base progress arc: solid ring colour, starts at 12 o'clock = -90°
            if (baseProgress > 0.005f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = baseProgress * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Overtime band: solid dark green, centred on the ring, starts at 12 o'clock and runs clockwise
            if (overtimeProgress > 0.005f || overtimeMinutes > 0) {
                val sweep = (overtimeProgress * 360f).coerceIn(42f, 360f)
                // 12 o'clock in Compose angles (-90° = top); a positive sweep runs clockwise
                val start = -90f
                val bandWidth = strokeWidth * overtimeBandRatio
                val bandCenterRadius = diameter / 2f
                val outlineWidth = overtimeOutlineWidth.toPx()

                // Round caps extend half the band width past each end; shorten the drawn arc so the rounded ends
                // sit exactly on the nominal start and end angles. Outline and band use the same drawn range.
                val capSweep = Math.toDegrees((bandWidth / 2f / bandCenterRadius).toDouble()).toFloat()
                val drawStart = start + capSweep
                val drawSweep = sweep - 2f * capSweep

                // Dark outline: outer edge of the light ring over the overtime part plus its rounded tip (open at 12 o'clock),
                // and a full circle on the inner edge of the light ring.
                val ringHalf = strokeWidth / 2f
                val outerRadius = bandCenterRadius + ringHalf
                val innerRadius = bandCenterRadius - ringHalf
                val tipAngle = start + sweep
                val tipRad = Math.toRadians(tipAngle.toDouble())
                val tipX = center.x + bandCenterRadius * Math.cos(tipRad).toFloat()
                val tipY = center.y + bandCenterRadius * Math.sin(tipRad).toFloat()
                // Tapered outer edge: width grows linearly from 0.1 px at 12 o'clock to the full outline width at the tip.
                // The band is centred on the outer ring edge, so it spans outerRadius ± width / 2.
                val taperSteps = 120
                val startWidthPx = 0.1f
                val taperPath = androidx.compose.ui.graphics.Path()
                for (i in 0..taperSteps) {
                    val t = i / taperSteps.toFloat()
                    val w = (outlineWidth * t).coerceAtLeast(startWidthPx)
                    val rad = Math.toRadians((start + sweep * t).toDouble())
                    val cos = Math.cos(rad).toFloat()
                    val sin = Math.sin(rad).toFloat()
                    val outX = center.x + (outerRadius + w / 2f) * cos
                    val outY = center.y + (outerRadius + w / 2f) * sin
                    if (i == 0) taperPath.moveTo(outX, outY) else taperPath.lineTo(outX, outY)
                }
                for (i in taperSteps downTo 0) {
                    val t = i / taperSteps.toFloat()
                    val w = (outlineWidth * t).coerceAtLeast(startWidthPx)
                    val rad = Math.toRadians((start + sweep * t).toDouble())
                    val cos = Math.cos(rad).toFloat()
                    val sin = Math.sin(rad).toFloat()
                    taperPath.lineTo(center.x + (outerRadius - w / 2f) * cos, center.y + (outerRadius - w / 2f) * sin)
                }
                taperPath.close()
                drawPath(path = taperPath, color = overtimeOutlineColor)

                // Rounded tip cap at the clockwise end, full outline width
                val tipCapPath = androidx.compose.ui.graphics.Path().apply {
                    arcTo(
                        Rect(tipX - ringHalf, tipY - ringHalf, tipX + ringHalf, tipY + ringHalf),
                        tipAngle, 180f, forceMoveTo = true
                    )
                }
                drawPath(
                    path = tipCapPath,
                    color = overtimeOutlineColor,
                    style = Stroke(width = outlineWidth, cap = StrokeCap.Butt)
                )
                drawCircle(
                    color = overtimeOutlineColor,
                    radius = innerRadius,
                    center = center,
                    style = Stroke(width = outlineWidth)
                )
                drawArc(
                    color = overtimeBandColor,
                    startAngle = drawStart,
                    sweepAngle = drawSweep,
                    useCenter = false,
                    topLeft = Offset(center.x - bandCenterRadius, center.y - bandCenterRadius),
                    size = Size(bandCenterRadius * 2f, bandCenterRadius * 2f),
                    style = Stroke(width = bandWidth, cap = StrokeCap.Round)
                )

                // Subtle live pulse on the band while running
                if (isClockRunning) {
                    drawArc(
                        color = overtimeBandColor.copy(alpha = pulseAlpha * 0.35f),
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth + 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Overtime text: on the band's centreline, centred on the band, reading counter-clockwise
                if (overtimeMinutes > 0) {
                    val otH = overtimeMinutes / 60
                    val otM = overtimeMinutes % 60
                    val otText = if (otH > 0) "+${otH}h ${otM}m" else "+${otM}m"
                    otTextPaint.textSize = 10.sp.toPx()
                    otTextPaint.color = overtimeTextColor.toArgb()
                    otTextPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    otTextPaint.textAlign = Paint.Align.LEFT

                    // Text reads upright only if its path runs clockwise in the top half and counter-clockwise in the bottom half.
                    // Pick the direction from the band's middle so the label is never upside down.
                    val bandMiddle = start + sweep / 2f
                    val readsClockwise = kotlin.math.sin(Math.toRadians(bandMiddle.toDouble())) < 0.0
                    val textArcPath = Path().apply {
                        addArc(
                            topLeft.x, topLeft.y,
                            topLeft.x + diameter, topLeft.y + diameter,
                            if (readsClockwise) bandMiddle - 45f else bandMiddle + 45f,
                            if (readsClockwise) 90f else -90f
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
                color = textColor
            )

            // Target subtitle
            if (targetText.isNotBlank()) {
                Text(
                    text = "von $targetText",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = textColor
                )
            }

            // Bottom chip: Feierabend prognosis OR percentage
            if (projectedEndTime != null && isClockRunning) {
                Spacer(modifier = Modifier.height(3.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = ringColor.copy(alpha = 0.16f)
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
                            tint = ringColor
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "~$projectedEndTime",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = ringColor
                        )
                    }
                }
            } else if (dailyGoalReached) {
                Spacer(modifier = Modifier.height(2.dp))
                val pct = (progress * 100).toInt()
                Surface(
                    shape = RoundedCornerShape(50),
                    color = ringColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "$pct % ✓",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = ringColor,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp)
                    )
                }
            } else if (isClockRunning) {
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = ringColor.copy(alpha = 0.15f)
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
                                .background(ringColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Läuft",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = ringColor
                        )
                    }
                }
            } else if (targetText.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                val pct = (progress * 100).toInt()
                Surface(
                    shape = RoundedCornerShape(50),
                    color = ringColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "$pct %",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = ringColor,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp)
                    )
                }
            }
        }
    }
}
