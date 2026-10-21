package com.flex.widget

/** Progress values for the widget: keep the ring capped while allowing the percentage to grow. */
data class WidgetDailyProgress(
    val progress: Float,
    val percent: Int,
    /** Overtime beyond 100 % as fraction (0..1), drawn as overlapping arc like the home gauge. */
    val overtimeProgress: Float = 0f
) {
    companion object {
        fun calculate(netMinutes: Long, targetMinutes: Long): WidgetDailyProgress {
            val rawProgress = if (targetMinutes > 0) {
                (netMinutes.toDouble() / targetMinutes.toDouble()).toFloat().coerceAtLeast(0f)
            } else {
                0f
            }

            return WidgetDailyProgress(
                progress = rawProgress.coerceIn(0f, 1f),
                percent = (rawProgress * 100).toInt(),
                overtimeProgress = (rawProgress - 1f).coerceIn(0f, 1f)
            )
        }
    }
}
