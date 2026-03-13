package com.flex.ui.month

import com.flex.domain.model.DayType
import com.flex.domain.model.WorkDay
import java.time.format.DateTimeFormatter

/**
 * Represents the initial state for EditDayDialog based on an existing WorkDay.
 *
 * This data class encapsulates the UI state that should be displayed when editing a work day,
 * including selected tab, time fields, and duration values.
 */
data class EditDayDialogState(
    val selectedTab: Int,        // 0 = Start/Ende, 1 = Gesamtzeit
    val startText: String,       // "HH:mm" format
    val endText: String,         // "HH:mm" format
    val durationHours: String,   // hours as string
    val durationMinutes: String  // minutes as string
)

/**
 * Calculates the initial state for EditDayDialog based on an existing WorkDay's timeBlocks.
 *
 * This function determines:
 * - Which tab should be selected (0: Start/Ende or 1: Gesamtzeit)
 * - What start/end times or duration values to display
 *
 * Logic:
 * - For WORK or SATURDAY_BONUS day types:
 *   - If the first timeBlock has isDuration=true, show Gesamtzeit tab (tab 1)
 *   - If the first timeBlock has isDuration=false, show Start/Ende tab (tab 0)
 *   - If no timeBlocks, default to Start/Ende tab (tab 0)
 * - For other day types (VACATION, SPECIAL_VACATION, FLEX_DAY), always show Gesamtzeit tab (tab 1)
 *
 * @param workDay The WorkDay to extract state from
 * @param dailyWorkMinutes The default daily work minutes (defaults to 426 = 7h 6m)
 * @return EditDayDialogState with initial UI values
 */
fun calculateEditDayDialogState(
    workDay: WorkDay,
    dailyWorkMinutes: Int = 426
): EditDayDialogState {
    // Default values
    val defaultStartTime = "08:00"
    val defaultDurationHours = (dailyWorkMinutes / 60).toString()
    val defaultDurationMinutes = (dailyWorkMinutes % 60).toString()

    // Determine selected tab and extract values from first timeBlock if it exists
    val (selectedTab, startText, endText, durationHours, durationMinutes) = when {
        // For non-work day types, always use Gesamtzeit (duration) tab
        workDay.dayType !in listOf(DayType.WORK, DayType.SATURDAY_BONUS) -> {
            Tuple5(
                selectedTab = 1,
                startText = defaultStartTime,
                endText = "00:00",  // Not used in duration mode
                durationHours = defaultDurationHours,
                durationMinutes = defaultDurationMinutes
            )
        }

        // For WORK and SATURDAY_BONUS days
        workDay.timeBlocks.isEmpty() -> {
            // No time blocks - default to Start/Ende tab with defaults
            Tuple5(
                selectedTab = 0,
                startText = defaultStartTime,
                endText = calculateDefaultEndTime(defaultStartTime, dailyWorkMinutes),
                durationHours = defaultDurationHours,
                durationMinutes = defaultDurationMinutes
            )
        }

        else -> {
            // Has time blocks - use the first one
            val firstBlock = workDay.timeBlocks.first()

            if (firstBlock.isDuration && firstBlock.endTime != null) {
                // Duration mode: extract hours and minutes from the time difference
                val durationMinutes = java.time.temporal.ChronoUnit.MINUTES.between(
                    firstBlock.startTime,
                    firstBlock.endTime
                ).toInt()
                val hours = durationMinutes / 60
                val minutes = durationMinutes % 60

                Tuple5(
                    selectedTab = 1,
                    startText = defaultStartTime,
                    endText = firstBlock.endTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                    durationHours = hours.toString(),
                    durationMinutes = minutes.toString()
                )
            } else {
                // Start/Ende mode: extract start and end times
                val startFormatted = firstBlock.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                val endFormatted = firstBlock.endTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "00:00"

                Tuple5(
                    selectedTab = 0,
                    startText = startFormatted,
                    endText = endFormatted,
                    durationHours = defaultDurationHours,
                    durationMinutes = defaultDurationMinutes
                )
            }
        }
    }

    return EditDayDialogState(
        selectedTab = selectedTab,
        startText = startText,
        endText = endText,
        durationHours = durationHours,
        durationMinutes = durationMinutes
    )
}

/**
 * Helper function to calculate the default end time based on start time and daily work minutes.
 *
 * @param startTimeStr Start time as "HH:mm" string
 * @param dailyWorkMinutes Minutes to add to start time
 * @return End time as "HH:mm" string
 */
private fun calculateDefaultEndTime(startTimeStr: String, dailyWorkMinutes: Int): String {
    return try {
        val startTime = java.time.LocalTime.parse(startTimeStr, DateTimeFormatter.ofPattern("HH:mm"))
        val endTime = startTime.plusMinutes(dailyWorkMinutes.toLong())
        endTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        "00:00"
    }
}

/**
 * Temporary data class for tuple returns (Kotlin doesn't have built-in Tuple5).
 * Used internally for better readability in calculateEditDayDialogState.
 */
private data class Tuple5(
    val selectedTab: Int,
    val startText: String,
    val endText: String,
    val durationHours: String,
    val durationMinutes: String
)
