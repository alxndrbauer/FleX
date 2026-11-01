package com.flex.domain.model

data class FlextimeBalance(
    val initialMinutes: Int = 0,
    val earnedMinutes: Long = 0,
    val totalMinutes: Long = 0,
    val targetMinutes: Long = 0,
    val overtimeMinutes: Long = 0,
    val earnedOvertimeMinutes: Long = 0
) {
    val hours: Long get() = totalMinutes / 60
    val remainingMinutes: Long get() = kotlin.math.abs(totalMinutes % 60)
    val isPositive: Boolean get() = totalMinutes >= 0

    private val overtimeHours: Long get() = overtimeMinutes / 60
    private val overtimeRemainingMinutes: Long get() = kotlin.math.abs(overtimeMinutes % 60)
    val isOvertimePositive: Boolean get() = overtimeMinutes >= 0

    fun formatDisplay(): String {
        val sign = if (totalMinutes < 0) "-" else "+"
        return "$sign${kotlin.math.abs(hours)}h ${remainingMinutes}min"
    }

    fun formatOvertime(): String {
        val sign = if (overtimeMinutes < 0) "-" else "+"
        return "$sign${kotlin.math.abs(overtimeHours)}h ${overtimeRemainingMinutes}min"
    }

    fun formatTarget(): String {
        val h = targetMinutes / 60
        val m = targetMinutes % 60
        return "${h}h ${m}min"
    }

    val exceedsPositiveThreshold: Boolean
        get() = Companion.exceedsPositiveThreshold(totalMinutes)

    val exceedsNegativeThreshold: Boolean
        get() = Companion.exceedsNegativeThreshold(totalMinutes)

    val isOutsideThreshold: Boolean
        get() = Companion.isOutsideThreshold(totalMinutes)

    val thresholdWarningMessage: String?
        get() = Companion.warningMessage(totalMinutes)

    val shortWarningBadge: String?
        get() = Companion.shortWarningBadge(totalMinutes)

    companion object {
        const val WARNING_THRESHOLD_HOURS = 25L
        const val WARNING_THRESHOLD_MINUTES = WARNING_THRESHOLD_HOURS * 60L

        fun exceedsPositiveThreshold(minutes: Long): Boolean =
            minutes > WARNING_THRESHOLD_MINUTES

        fun exceedsNegativeThreshold(minutes: Long): Boolean =
            minutes < -WARNING_THRESHOLD_MINUTES

        fun isOutsideThreshold(minutes: Long): Boolean =
            exceedsPositiveThreshold(minutes) || exceedsNegativeThreshold(minutes)

        fun shortWarningBadge(minutes: Long): String? = when {
            minutes > WARNING_THRESHOLD_MINUTES -> "⚠ > +25h"
            minutes < -WARNING_THRESHOLD_MINUTES -> "⚠ < -25h"
            else -> null
        }

        fun warningMessage(minutes: Long): String? {
            if (minutes > WARNING_THRESHOLD_MINUTES) {
                val diff = minutes - WARNING_THRESHOLD_MINUTES
                val h = diff / 60
                val m = diff % 60
                val diffStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                return "Hinweis: Gleitzeitsaldo liegt über +25h (+$diffStr darüber)"
            }
            if (minutes < -WARNING_THRESHOLD_MINUTES) {
                val diff = kotlin.math.abs(minutes + WARNING_THRESHOLD_MINUTES)
                val h = diff / 60
                val m = diff % 60
                val diffStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                return "Warnung: Gleitzeitsaldo liegt unter -25h (-$diffStr darunter)"
            }
            return null
        }
    }
}
