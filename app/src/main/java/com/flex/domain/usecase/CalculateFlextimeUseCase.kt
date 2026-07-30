package com.flex.domain.usecase

import com.flex.domain.model.DayType
import com.flex.domain.model.FlextimeBalance
import com.flex.domain.model.PublicHolidays
import com.flex.domain.model.Settings
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkTimeRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToLong
import javax.inject.Inject

class CalculateFlextimeUseCase @Inject constructor(
    private val calculateDayWorkTime: CalculateDayWorkTimeUseCase
) {

    operator fun invoke(
        workDays: List<WorkDay>,
        settings: Settings,
        yearMonth: YearMonth? = null,
        workTimeRules: List<WorkTimeRule> = emptyList()
    ): FlextimeBalance {
        var earnedMinutes = 0L
        var overtimeMinutes = 0L

        fun getDailyTarget(date: LocalDate): Int {
            return workTimeRules
                .filter { !it.validFrom.isAfter(date) }
                .maxByOrNull { it.validFrom }
                ?.dailyWorkMinutes ?: settings.dailyWorkMinutes
        }

        for (day in workDays) {
            val dailyTarget = getDailyTarget(day.date)
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
                        earnedMinutes += result.netMinutes - dailyTarget
                    }
                }
                DayType.SATURDAY_BONUS -> {
                    val result = calculateDayWorkTime(day.timeBlocks)
                    earnedMinutes += result.netMinutes // volle Arbeitszeit auf Gleitzeit
                    overtimeMinutes += (result.netMinutes * 0.5).roundToLong() // 50% Bonus auf Überstunden
                }
                DayType.FLEX_DAY -> {
                    earnedMinutes += -dailyTarget.toLong() // voller Tag wird abgezogen
                }
                DayType.OVERTIME_DAY -> {
                    overtimeMinutes += -dailyTarget.toLong()
                    // earnedMinutes bleibt unberührt → Gleitzeit neutral
                }
                DayType.VACATION, DayType.SPECIAL_VACATION, DayType.SICK_DAY -> {
                    // neutral
                }
            }
        }

        val total = settings.initialFlextimeMinutes + earnedMinutes

        // Dynamic target: actual working days (Mon-Fri minus holidays) × daily target for each date
        var targetMinutes = 0L
        if (yearMonth != null) {
            for (day in 1..yearMonth.lengthOfMonth()) {
                val date = yearMonth.atDay(day)
                if (date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
                    && !PublicHolidays.isHoliday(date)) {
                    targetMinutes += getDailyTarget(date)
                }
            }
        }

        return FlextimeBalance(
            initialMinutes = settings.initialFlextimeMinutes,
            earnedMinutes = earnedMinutes,
            totalMinutes = total,
            targetMinutes = targetMinutes,
            overtimeMinutes = settings.initialOvertimeMinutes + overtimeMinutes,
            earnedOvertimeMinutes = overtimeMinutes
        )
    }
}
