package com.flex.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

object CircularWorkGaugeBitmapRenderer {

    fun renderGauge(
        netTimeText: String,
        targetText: String,
        prognosisText: String?,
        progress: Float,
        isClockRunning: Boolean,
        dailyGoalReached: Boolean,
        isDark: Boolean,
        sizePx: Int = 280
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val strokeWidth = sizePx * 0.11f
        val radius = (sizePx - strokeWidth) / 2f
        val centerX = sizePx / 2f
        val centerY = sizePx / 2f

        // 1. Inner circle subtle background fill matching Google Health / Image 2
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (dailyGoalReached) {
                Color.argb(28, 15, 157, 88)
            } else if (isDark) {
                Color.argb(35, 255, 255, 255)
            } else {
                Color.argb(20, 60, 90, 140)
            }
        }
        canvas.drawCircle(centerX, centerY, radius - strokeWidth / 2f, innerPaint)

        // 2. Track Ring (soft light-grey or subtle dark)
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            color = if (isDark) {
                Color.argb(45, 255, 255, 255)
            } else {
                Color.rgb(230, 232, 238)
            }
        }
        val bounds = RectF(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
        canvas.drawArc(bounds, 0f, 360f, false, trackPaint)

        // 3. Progress Arc with Rounded End Caps matching Image 2
        val clampedProgress = progress.coerceIn(0f, 1f)
        if (clampedProgress > 0.005f) {
            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                strokeCap = Paint.Cap.ROUND
                color = if (dailyGoalReached) {
                    Color.rgb(15, 157, 88) // Emerald
                } else if (isDark) {
                    Color.rgb(150, 185, 235) // Rich soft blue
                } else {
                    Color.rgb(56, 85, 145) // Deep slate-blue matching Image 2
                }
            }
            val sweepAngle = clampedProgress * 360f
            canvas.drawArc(bounds, -90f, sweepAngle, false, progressPaint)
        }

        // 4. Center Typography matching Image 2
        val onSurfaceColor = if (isDark) Color.WHITE else Color.rgb(28, 27, 31)
        val onSurfaceVariantColor = if (isDark) Color.rgb(202, 196, 208) else Color.rgb(73, 69, 79)

        // Line 1: Main Net Time (e.g. "15min" or "7h 30m")
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onSurfaceColor
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val timeY = if (prognosisText != null) centerY - sizePx * 0.04f else centerY + sizePx * 0.04f
        canvas.drawText(netTimeText, centerX, timeY, timePaint)

        // Line 2: Target Text (e.g. "von 7h 0min")
        val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onSurfaceVariantColor
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.095f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText(targetText, centerX, timeY + sizePx * 0.13f, targetPaint)

        // Line 3: Pill Badge matching Image 2 (e.g. "3 %" or "~17:00")
        if (prognosisText != null) {
            val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (dailyGoalReached) {
                    if (isDark) Color.rgb(159, 212, 126) else Color.rgb(15, 157, 88)
                } else if (isDark) {
                    Color.rgb(180, 210, 245)
                } else {
                    Color.rgb(38, 70, 130)
                }
                textAlign = Paint.Align.CENTER
                textSize = sizePx * 0.08f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val textWidth = pillTextPaint.measureText(prognosisText)
            val pillHeight = sizePx * 0.11f
            val pillWidth = textWidth + sizePx * 0.10f
            val pillRadius = pillHeight / 2f
            val pillY = timeY + sizePx * 0.23f

            val pillRect = RectF(
                centerX - pillWidth / 2f,
                pillY - pillHeight / 2f,
                centerX + pillWidth / 2f,
                pillY + pillHeight / 2f
            )

            val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = if (dailyGoalReached) {
                    if (isDark) Color.argb(60, 15, 157, 88) else Color.argb(35, 15, 157, 88)
                } else if (isDark) {
                    Color.argb(55, 60, 100, 160)
                } else {
                    Color.argb(35, 56, 106, 180)
                }
            }

            canvas.drawRoundRect(pillRect, pillRadius, pillRadius, pillBgPaint)

            val fontMetrics = pillTextPaint.fontMetrics
            val textBaselineY = pillRect.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(prognosisText, centerX, textBaselineY, pillTextPaint)
        }

        return bitmap
    }
}
