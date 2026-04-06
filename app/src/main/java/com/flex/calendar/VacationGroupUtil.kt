package com.flex.calendar

import com.flex.domain.model.DayType
import com.flex.domain.model.WorkDay

/**
 * Utility for grouping consecutive vacation days into runs.
 *
 * Two vacation days are considered consecutive when their calendar dates are ≤ 3 days apart.
 * This bridges weekends (Friday → Monday = 3 days) without merging genuinely separate
 * vacation periods.
 *
 * Only days of the *same* [DayType] are grouped together. Input must therefore already be
 * filtered to a single type before calling [groupConsecutiveRuns].
 */
object VacationGroupUtil {

    val VACATION_TYPES = setOf(DayType.VACATION, DayType.SPECIAL_VACATION)

    /**
     * Groups [days] (all assumed to be the same [DayType]) into consecutive runs.
     * A new run starts whenever the gap between two adjacent dates exceeds 3 calendar days.
     *
     * @return List of runs, each run sorted ascending by date.
     */
    fun groupConsecutiveRuns(days: List<WorkDay>): List<List<WorkDay>> {
        val sorted = days.sortedBy { it.date }
        val runs = mutableListOf<MutableList<WorkDay>>()
        var current = mutableListOf<WorkDay>()
        for (day in sorted) {
            if (current.isEmpty() || day.date.toEpochDay() - current.last().date.toEpochDay() <= 3) {
                current.add(day)
            } else {
                runs.add(current)
                current = mutableListOf(day)
            }
        }
        if (current.isNotEmpty()) runs.add(current)
        return runs
    }
}
