package com.vrema.domain.usecase

import com.vrema.domain.model.DayType
import com.vrema.domain.model.FlextimeBalance
import com.vrema.domain.model.PublicHolidays
import com.vrema.domain.model.Settings
import com.vrema.domain.model.WorkDay
import java.time.DayOfWeek
import java.time.YearMonth
import kotlin.math.roundToLong
import javax.inject.Inject

class CalculateFlextimeUseCase @Inject constructor(
    private val calculateDayWorkTime: CalculateDayWorkTimeUseCase
) {

    operator fun invoke(workDays: List<WorkDay>, settings: Settings, yearMonth: YearMonth? = null): FlextimeBalance {
        var earnedMinutes = 0L
        var overtimeMinutes = 0L

        for (day in workDays) {
            when (day.dayType) {
                DayType.WORK -> {
                    val result = calculateDayWorkTime(day.timeBlocks)
                    // Check if this is a weekend day (Saturday or Sunday)
                    val isWeekend = day.date.dayOfWeek == DayOfWeek.SATURDAY || day.date.dayOfWeek == DayOfWeek.SUNDAY

                    if (isWeekend) {
                        // Weekend work: count FULL time as flextime (it's extra over normal Mon-Fri work)
                        earnedMinutes += result.netMinutes
                    } else {
                        // Weekday work: count only extra hours over daily target
                        earnedMinutes += result.netMinutes - settings.dailyWorkMinutes
                    }
                }
                DayType.SATURDAY_BONUS -> {
                    val result = calculateDayWorkTime(day.timeBlocks)
                    earnedMinutes += result.netMinutes // volle Arbeitszeit auf Gleitzeit
                    overtimeMinutes += (result.netMinutes * 0.5).roundToLong() // 50% Bonus auf Überstunden
                }
                DayType.FLEX_DAY -> {
                    earnedMinutes += -settings.dailyWorkMinutes.toLong() // voller Tag wird abgezogen
                }
                DayType.VACATION, DayType.SPECIAL_VACATION -> {
                    // neutral
                }
            }
        }

        val total = settings.initialFlextimeMinutes + earnedMinutes

        // Dynamic target: actual working days (Mon-Fri minus holidays) × dailyWorkMinutes
        val targetMinutes = if (yearMonth != null) {
            var workingDays = 0
            for (day in 1..yearMonth.lengthOfMonth()) {
                val date = yearMonth.atDay(day)
                if (date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
                    && !PublicHolidays.isHoliday(date)) {
                    workingDays++
                }
            }
            workingDays.toLong() * settings.dailyWorkMinutes
        } else {
            0L
        }

        return FlextimeBalance(
            initialMinutes = settings.initialFlextimeMinutes,
            earnedMinutes = earnedMinutes,
            totalMinutes = total,
            targetMinutes = targetMinutes,
            overtimeMinutes = settings.initialOvertimeMinutes + overtimeMinutes
        )
    }
}
