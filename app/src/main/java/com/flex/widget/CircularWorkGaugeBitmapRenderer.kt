package com.flex.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin

/**
 * Colours of the gauge as ARGB ints. The caller resolves them from the theme (GlanceTheme),
 * mirroring the roles used in CircularWorkGauge (Compose).
 */
data class GaugeColors(
    val ring: Int,
    val overtimeBand: Int,
    val center: Int,
    val track: Int,
    val text: Int,
    val outline: Int,
    val overtimeText: Int,
)

/**
 * Renders the work gauge as a bitmap for the home widget.
 * Mirrors CircularWorkGauge (Compose): light ring, overtime band from 12 o'clock clockwise,
 * outline on the outer edge (tapering from 0.1 px to full width) with a rounded tip,
 * a full inner circle outline, and the overtime label on the band.
 * isDark, isClockRunning and dailyGoalReached are kept for the existing call sites; the design does not use them.
 */
object CircularWorkGaugeBitmapRenderer {

    // Proportions of the 152 dp in-app gauge (CircularWorkGauge sizeDp default)
    private const val REFERENCE_SIZE_DP = 152f
    private const val STROKE_DP = 24f
    private const val OUTLINE_DP = 3f
    private const val OVERTIME_TEXT_DP = 10f
    // Overtime band width as a share of the ring width (CircularWorkGauge overtimeBandRatio)
    private const val BAND_RATIO = 0.6f

    fun renderGauge(
        netTimeText: String,
        targetText: String,
        prognosisText: String?,
        progress: Float,
        overtimeProgress: Float,
        isClockRunning: Boolean,
        dailyGoalReached: Boolean,
        isDark: Boolean,
        sizePx: Int = 280,
        overtimeText: String? = null,
        colors: GaugeColors
    ): Bitmap {
        val ringColor = colors.ring
        val overtimeBandColor = colors.overtimeBand
        val centerColor = colors.center
        val trackColor = colors.track
        val textColor = colors.text
        val outlineColor = colors.outline
        val overtimeTextColor = colors.overtimeText

        val dpScale = sizePx / REFERENCE_SIZE_DP
        val strokeWidth = STROKE_DP * dpScale
        val outlineWidth = OUTLINE_DP * dpScale
        // Padding for the outer outline, which sits half outside the ring's outer edge.
        // Without it the outline is cut off at the bitmap edge.
        val pad = outlineWidth / 2f + 1f
        val bitmapSizePx = sizePx + (pad * 2f).toInt()
        val bitmap = Bitmap.createBitmap(bitmapSizePx, bitmapSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val centerX = bitmapSizePx / 2f
        val centerY = bitmapSizePx / 2f
        // Ring keeps the same size as before; the bitmap grows by the outline padding around it
        val diameter = sizePx - strokeWidth
        val radius = diameter / 2f
        val bounds = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        // 1. Inner dark green centre
        canvas.drawCircle(centerX, centerY, radius - strokeWidth / 2f - 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = centerColor
        })

        // 2. Track ring
        canvas.drawArc(bounds, 0f, 360f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            color = trackColor
        })

        // 3. Base progress arc from 12 o'clock
        val clampedProgress = progress.coerceIn(0f, 1f)
        if (clampedProgress > 0.005f) {
            canvas.drawArc(bounds, -90f, clampedProgress * 360f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                strokeCap = Paint.Cap.ROUND
                color = ringColor
            })
        }

        // 4. Overtime band: from 12 o'clock clockwise, centred on the ring
        val clampedOvertime = overtimeProgress.coerceIn(0f, 1f)
        if (clampedOvertime > 0.005f) {
            val sweep = (clampedOvertime * 360f).coerceIn(42f, 360f)
            val start = -90f
            val bandWidth = strokeWidth * BAND_RATIO
            // Round caps extend half the band width past each end; shorten the drawn arc so the ends sit on the nominal angles
            val capSweep = Math.toDegrees((bandWidth / 2f / radius).toDouble()).toFloat()
            canvas.drawArc(bounds, start + capSweep, sweep - 2f * capSweep, false, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = bandWidth
                strokeCap = Paint.Cap.ROUND
                color = overtimeBandColor
            })

            // 5. Outer outline: width grows linearly from 0.1 px at 12 o'clock to the full outline width at the tip
            val outerRadius = radius + strokeWidth / 2f
            val innerRadius = radius - strokeWidth / 2f
            val taperSteps = 120
            val startWidthPx = 0.1f
            val taperPath = Path()
            for (i in 0..taperSteps) {
                val t = i / taperSteps.toFloat()
                val w = (outlineWidth * t).coerceAtLeast(startWidthPx)
                val rad = Math.toRadians((start + sweep * t).toDouble())
                val x = centerX + (outerRadius + w / 2f) * cos(rad).toFloat()
                val y = centerY + (outerRadius + w / 2f) * sin(rad).toFloat()
                if (i == 0) taperPath.moveTo(x, y) else taperPath.lineTo(x, y)
            }
            for (i in taperSteps downTo 0) {
                val t = i / taperSteps.toFloat()
                val w = (outlineWidth * t).coerceAtLeast(startWidthPx)
                val rad = Math.toRadians((start + sweep * t).toDouble())
                taperPath.lineTo(
                    centerX + (outerRadius - w / 2f) * cos(rad).toFloat(),
                    centerY + (outerRadius - w / 2f) * sin(rad).toFloat()
                )
            }
            taperPath.close()
            canvas.drawPath(taperPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = outlineColor
            })

            // Rounded tip of the outline at the clockwise end, full outline width
            val tipAngle = start + sweep
            val tipRad = Math.toRadians(tipAngle.toDouble())
            val tipX = centerX + radius * cos(tipRad).toFloat()
            val tipY = centerY + radius * sin(tipRad).toFloat()
            val tipHalf = strokeWidth / 2f
            canvas.drawArc(
                RectF(tipX - tipHalf, tipY - tipHalf, tipX + tipHalf, tipY + tipHalf),
                tipAngle,
                180f,
                false,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    this.strokeWidth = outlineWidth
                    color = outlineColor
                }
            )

            // 6. Full inner circle outline
            canvas.drawCircle(centerX, centerY, innerRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = outlineWidth
                color = outlineColor
            })

            // 7. Overtime label on the band's centreline, same placement as the in-app gauge
            if (overtimeText != null) {
                val otPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = overtimeTextColor
                    textSize = OVERTIME_TEXT_DP * dpScale
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.LEFT
                }
                // Text reads upright only if its path runs clockwise in the top half and counter-clockwise in the bottom half.
                val bandMiddle = start + sweep / 2f
                val readsClockwise = sin(Math.toRadians(bandMiddle.toDouble())) < 0.0
                val textPath = Path().apply {
                    addArc(
                        bounds.left, bounds.top, bounds.right, bounds.bottom,
                        if (readsClockwise) bandMiddle - 45f else bandMiddle + 45f,
                        if (readsClockwise) 90f else -90f
                    )
                }
                val arcLen = (90f / 360f) * (Math.PI.toFloat() * diameter)
                val hOffset = (arcLen - otPaint.measureText(overtimeText)) / 2f
                val fm = otPaint.fontMetrics
                val vOffset = -(fm.ascent + fm.descent) / 2f
                canvas.drawTextOnPath(overtimeText, textPath, hOffset, vOffset, otPaint)
            }
        }

        // 8. Centre typography, fixed light text on the dark green centre
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val timeY = if (prognosisText != null) centerY - sizePx * 0.04f else centerY + sizePx * 0.04f
        canvas.drawText(netTimeText, centerX, timeY, timePaint)

        val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            alpha = 200
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.095f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText(targetText, centerX, timeY + sizePx * 0.13f, targetPaint)

        // 9. Pill badge (prognosis or percentage)
        if (prognosisText != null) {
            val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ringColor
                textAlign = Paint.Align.CENTER
                textSize = sizePx * 0.08f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val pillHeight = sizePx * 0.11f
            val pillWidth = pillTextPaint.measureText(prognosisText) + sizePx * 0.10f
            val pillY = timeY + sizePx * 0.23f
            val pillRect = RectF(
                centerX - pillWidth / 2f,
                pillY - pillHeight / 2f,
                centerX + pillWidth / 2f,
                pillY + pillHeight / 2f
            )
            canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = (ringColor and 0x00FFFFFF) or (35 shl 24)
            })
            val fontMetrics = pillTextPaint.fontMetrics
            val textBaselineY = pillRect.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(prognosisText, centerX, textBaselineY, pillTextPaint)
        }

        return bitmap
    }
}
