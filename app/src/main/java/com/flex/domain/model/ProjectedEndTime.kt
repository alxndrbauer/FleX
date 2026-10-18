package com.flex.domain.model

import java.time.LocalTime

data class ProjectedEndTime(
    val projectedEndTime: LocalTime,
    val projectedEndTimeWithoutBreak: LocalTime,
    val pendingBreakMinutes: Long,
    val takenBreakMinutes: Long,
    val requiredBreakMinutes: Long,
    val remainingNetMinutes: Long
) {
    val hasPendingBreak: Boolean get() = pendingBreakMinutes > 0L

    fun formatWithBreakHint(): String = if (hasPendingBreak) {
        "${formatTime(projectedEndTime)} (ohne Pause ${formatTime(projectedEndTimeWithoutBreak)})"
    } else {
        formatTime(projectedEndTime)
    }

    fun formatCompact(): String = formatTime(projectedEndTime)

    companion object {
        fun formatTime(time: LocalTime): String =
            "%02d:%02d".format(time.hour, time.minute)
    }
}
