package com.flex.ui.month

import com.flex.domain.model.DayType
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import java.time.format.DateTimeFormatter

/**
 * Per-block editor state for EditDayDialog.
 */
data class BlockEditorState(
    val startText: String,       // "HH:mm" format
    val endText: String,         // "HH:mm" format
    val durationHours: String,   // hours as string
    val durationMinutes: String, // minutes as string
    val location: WorkLocation
)

/**
 * Represents the initial state for EditDayDialog based on an existing WorkDay.
 */
data class EditDayDialogState(
    val selectedTab: Int,               // 0 = Start/Ende, 1 = Gesamtzeit
    val blocks: List<BlockEditorState>  // one entry per TimeBlock
)

/**
 * Calculates the initial state for EditDayDialog based on all of a WorkDay's timeBlocks.
 */
fun calculateEditDayDialogState(
    workDay: WorkDay,
    dailyWorkMinutes: Int = 426
): EditDayDialogState {
    val defaultStartTime = "08:00"
    val defaultDurationHours = (dailyWorkMinutes / 60).toString()
    val defaultDurationMinutes = (dailyWorkMinutes % 60).toString()
    val defaultEndTime = calculateDefaultEndTime(defaultStartTime, dailyWorkMinutes)
    val defaultLocation = workDay.location

    // For non-work day types, always use Gesamtzeit (duration) tab — no per-block editors needed
    if (workDay.dayType !in listOf(DayType.WORK, DayType.SATURDAY_BONUS)) {
        return EditDayDialogState(
            selectedTab = 1,
            blocks = listOf(
                BlockEditorState(
                    startText = defaultStartTime,
                    endText = "00:00",
                    durationHours = defaultDurationHours,
                    durationMinutes = defaultDurationMinutes,
                    location = defaultLocation
                )
            )
        )
    }

    // No time blocks yet — default to Start/Ende with one default block
    if (workDay.timeBlocks.isEmpty()) {
        return EditDayDialogState(
            selectedTab = 0,
            blocks = listOf(
                BlockEditorState(
                    startText = defaultStartTime,
                    endText = defaultEndTime,
                    durationHours = defaultDurationHours,
                    durationMinutes = defaultDurationMinutes,
                    location = defaultLocation
                )
            )
        )
    }

    // Determine tab from the first block
    val selectedTab = if (workDay.timeBlocks.first().isDuration) 1 else 0

    val blocks = workDay.timeBlocks.map { block ->
        if (block.isDuration && block.endTime != null) {
            val durationMinutes = java.time.temporal.ChronoUnit.MINUTES.between(
                block.startTime, block.endTime
            ).toInt()
            BlockEditorState(
                startText = defaultStartTime,
                endText = block.endTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                durationHours = (durationMinutes / 60).toString(),
                durationMinutes = (durationMinutes % 60).toString(),
                location = block.location
            )
        } else {
            BlockEditorState(
                startText = block.startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                endText = block.endTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "00:00",
                durationHours = defaultDurationHours,
                durationMinutes = defaultDurationMinutes,
                location = block.location
            )
        }
    }

    return EditDayDialogState(selectedTab = selectedTab, blocks = blocks)
}

private fun calculateDefaultEndTime(startTimeStr: String, dailyWorkMinutes: Int): String {
    return try {
        val startTime = java.time.LocalTime.parse(startTimeStr, DateTimeFormatter.ofPattern("HH:mm"))
        startTime.plusMinutes(dailyWorkMinutes.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        "00:00"
    }
}
