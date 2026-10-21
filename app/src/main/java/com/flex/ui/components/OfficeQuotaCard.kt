package com.flex.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun Color.toVibrantOverlap(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (red * 255).toInt().coerceIn(0, 255),
        (green * 255).toInt().coerceIn(0, 255),
        (blue * 255).toInt().coerceIn(0, 255),
        hsv
    )
    // Make the overlap arc noticeably brighter and vibrant within the exact same hue family
    hsv[2] = (hsv[2] + 0.35f).coerceIn(0f, 1f)
    hsv[1] = (hsv[1] * 0.70f).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor((alpha * 255).toInt().coerceIn(0, 255), hsv))
}

@Composable
fun MiniProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    overlapColor: Color? = null,
    strokeWidthDp: Float = 2.5f
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceAtLeast(0f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "mini_ring_progress"
    )

    // Dynamically derive the overlap color from the bar's own color, keeping it 100% harmonious with the design
    val effectiveOverlapColor = remember(color, overlapColor) {
        overlapColor ?: color.toVibrantOverlap()
    }
    val outlineColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(1.5.dp)) {
            val strokePx = strokeWidthDp.dp.toPx()
            val borderPx = strokePx + 1.2.dp.toPx()
            val diameter = size.minDimension - strokePx
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)
            val arcSize = Size(diameter, diameter)

            // Background track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx)
            )

            // Base progress arc
            val baseSweep = (animatedProgress.coerceIn(0f, 1f)) * 360f
            if (baseSweep > 0.5f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = if (animatedProgress >= 1f) 360f else baseSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(
                        width = strokePx,
                        cap = if (animatedProgress >= 1f) StrokeCap.Square else StrokeCap.Round
                    )
                )
            }

            // Overtime / overlap lap if progress > 1f (Google Health style)
            if (animatedProgress > 1f) {
                val excessSweep = ((animatedProgress - 1f).coerceIn(0f, 1f)) * 360f
                if (excessSweep > 0.5f) {
                    // Crisp outline for rounded cap separation
                    drawArc(
                        color = outlineColor,
                        startAngle = -90f,
                        sweepAngle = excessSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = borderPx, cap = StrokeCap.Round)
                    )

                    // Core overlap arc in vibrant accent color derived from base color
                    drawArc(
                        color = effectiveOverlapColor,
                        startAngle = -90f,
                        sweepAngle = excessSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            }
        }

        if (animatedProgress >= 1f) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(11.dp)
            )
        }
    }
}

@Composable
fun OfficeQuotaCard(
    officePercent: Double,
    targetPercent: Int,
    isMet: Boolean,
    officeDays: Int,
    requiredDays: Int,
    officeMinutes: Long,
    requiredOfficeMinutes: Long,
    modifier: Modifier = Modifier,
    title: String = "Büro-Präsenz",
    subtitle: String = "Monatsquote",
    tooltipTitle: String = TOOLTIP_OFFICE_QUOTA_TITLE,
    tooltipText: String = TOOLTIP_OFFICE_QUOTA,
    isLive: Boolean = false,
    statusTextOverride: String? = null
) {
    val rawPctProgress = if (targetPercent > 0)
        (officePercent / targetPercent.toDouble()).toFloat().coerceIn(0f, 1f)
    else 0f
    val animPct by animateFloatAsState(rawPctProgress, tween(600), label = "pctProgress")

    val daysProgress = if (requiredDays > 0) officeDays.toFloat() / requiredDays else 0f
    val hoursProgress = if (requiredOfficeMinutes > 0) officeMinutes.toFloat() / requiredOfficeMinutes else 0f

    val officeH = officeMinutes / 60
    val officeM = officeMinutes % 60
    val reqH = requiredOfficeMinutes / 60
    val reqM = requiredOfficeMinutes % 60

    val statusText = statusTextOverride ?: if (isMet) "Quote erfüllt ✓" else "Ausstehend"

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Icon + Title + Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isMet) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = null,
                            tint = if (isMet) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            InfoTooltip(title = tooltipTitle, text = tooltipText)
                        }
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isMet) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.wrapContentSize()
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isMet) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Large percentage & target label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${"%.1f".format(officePercent)}%",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5f).sp
                        ),
                        color = if (isMet) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                    )
                    if (isLive) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "LIVE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "Ziel: ${targetPercent}%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Modern progress bar
            LinearProgressIndicator(
                progress = { animPct },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (isMet) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Side-by-side metric tiles (Tage & Stunden)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Tage",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "$officeDays von $requiredDays",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        MiniProgressRing(
                            progress = daysProgress,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Stunden",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${officeH}h ${officeM}m / ${reqH}h ${reqM}m",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        MiniProgressRing(
                            progress = hoursProgress,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}
