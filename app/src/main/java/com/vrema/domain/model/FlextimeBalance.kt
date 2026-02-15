package com.vrema.domain.model

data class FlextimeBalance(
    val initialMinutes: Int = 0,
    val earnedMinutes: Long = 0,
    val totalMinutes: Long = 0,
    val targetMinutes: Long = 0,
    val overtimeMinutes: Long = 0
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
}
