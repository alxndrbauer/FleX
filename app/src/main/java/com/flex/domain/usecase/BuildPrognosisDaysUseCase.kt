package com.flex.domain.usecase

import com.flex.domain.model.DayType
import com.flex.domain.model.PublicHolidays
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuildPrognosisDaysUseCase @Inject constructor() {
    operator fun invoke(
        month: YearMonth,
        existingDays: List<WorkDay>,
        settings: Settings
    ): List<WorkDay> {
        val existingByDate = existingDays.associateBy { it.date }
        val allDays = mutableListOf<WorkDay>()

        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val existing = existingByDate[date]

            if (existing != null) {
                if (existing.timeBlocks.isEmpty() &&
                    existing.dayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS)
                ) {
                    val start = LocalTime.of(8, 0)
                    val end = start.plusMinutes(settings.dailyWorkMinutes.toLong())
                    allDays.add(existing.copy(
                        timeBlocks = listOf(TimeBlock(workDayId = existing.id, startTime = start, endTime = end, isDuration = true, location = existing.location))
                    ))
                } else {
                    allDays.add(existing)
                }
            } else if (date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
                && !PublicHolidays.isHoliday(date)) {
                val start = LocalTime.of(8, 0)
                val end = start.plusMinutes(settings.dailyWorkMinutes.toLong())
                allDays.add(WorkDay(
                    date = date,
                    location = WorkLocation.HOME_OFFICE,
                    dayType = DayType.WORK,
                    isPlanned = true,
                    timeBlocks = listOf(TimeBlock(workDayId = 0, startTime = start, endTime = end, isDuration = true, location = WorkLocation.HOME_OFFICE))
                ))
            }
        }
        return allDays
    }
}
