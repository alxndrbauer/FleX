package com.flex.domain.usecase

import com.flex.domain.model.DayType
import com.flex.domain.model.PublicHolidays
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.model.DEFAULT_WORK_DAYS
import com.flex.domain.model.getRuleForDate
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuildPrognosisDaysUseCase @Inject constructor() {
    operator fun invoke(
        month: YearMonth,
        existingDays: List<WorkDay>,
        settings: Settings,
        workTimeRules: List<WorkTimeRule> = emptyList()
    ): List<WorkDay> {
        val existingByDate = existingDays.associateBy { it.date }
        val allDays = mutableListOf<WorkDay>()

        fun getDailyTarget(date: LocalDate): Int {
            return workTimeRules.getRuleForDate(date)?.dailyWorkMinutes ?: settings.dailyWorkMinutes
        }

        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val existing = existingByDate[date]
            val dailyTarget = getDailyTarget(date)

            if (existing != null) {
                if (existing.timeBlocks.isEmpty() &&
                    existing.dayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS)
                ) {
                    val start = LocalTime.of(8, 0)
                    val end = start.plusMinutes(dailyTarget.toLong())
                    allDays.add(existing.copy(
                        timeBlocks = listOf(TimeBlock(workDayId = existing.id, startTime = start, endTime = end, isDuration = true, location = existing.location))
                    ))
                } else {
                    allDays.add(existing)
                }
            } else {
                val activeRule = workTimeRules.getRuleForDate(date)
                val activeWorkDays = activeRule?.workDays ?: DEFAULT_WORK_DAYS
                if (date.dayOfWeek in activeWorkDays && !PublicHolidays.isHoliday(date)) {
                    val start = LocalTime.of(8, 0)
                    val end = start.plusMinutes(dailyTarget.toLong())
                    allDays.add(WorkDay(
                        date = date,
                        location = WorkLocation.HOME_OFFICE,
                        dayType = DayType.WORK,
                        isPlanned = true,
                        timeBlocks = listOf(TimeBlock(workDayId = 0, startTime = start, endTime = end, isDuration = true, location = WorkLocation.HOME_OFFICE))
                    ))
                }
            }
        }
        return allDays
    }
}
