package com.flex.domain.usecase

import com.flex.domain.model.AnalyticsData
import com.flex.domain.model.DayType
import com.flex.domain.model.LocationDistribution
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeRange
import com.flex.domain.model.TimeSeriesPoint
import com.flex.domain.model.WeeklyWorkHours
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.model.DEFAULT_WORK_DAYS
import com.flex.domain.model.getRuleForDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.IsoFields
import javax.inject.Inject
import kotlin.math.roundToLong

class CalculateAnalyticsUseCase @Inject constructor(
    private val calculateDayWorkTime: CalculateDayWorkTimeUseCase,
    private val calculateFlextimeUseCase: CalculateFlextimeUseCase
) {

    private fun dayWorkTime(day: WorkDay) =
        calculateDayWorkTime(day.timeBlocks, isBusinessTrip = day.dayType == DayType.BUSINESS_TRIP)

    operator fun invoke(
        workDays: List<WorkDay>,
        settings: Settings,
        timeRange: TimeRange,
        workTimeRules: List<WorkTimeRule> = emptyList(),
        priorWorkDays: List<WorkDay> = emptyList()
    ): AnalyticsData {
        // Filter out planned days - only analyze actual work
        val actualWorkDays = workDays.filter { !it.isPlanned }
        val actualPriorDays = priorWorkDays.filter { !it.isPlanned }

        if (actualWorkDays.isEmpty()) {
            return AnalyticsData(
                flextimeSeries = emptyList(),
                overtimeSeries = emptyList(),
                weeklyHours = emptyList(),
                monthlyHours = emptyList(),
                locationDistribution = LocationDistribution(0, 0)
            )
        }

        return AnalyticsData(
            flextimeSeries = calculateFlextimeSeries(actualWorkDays, settings, timeRange, workTimeRules, actualPriorDays),
            overtimeSeries = calculateOvertimeSeries(actualWorkDays, settings, timeRange, workTimeRules, actualPriorDays),
            weeklyHours = calculateWeeklyHours(actualWorkDays),
            monthlyHours = calculateMonthlyHours(actualWorkDays, timeRange),
            locationDistribution = calculateLocationDistribution(actualWorkDays)
        )
    }

    private fun getDailyTarget(date: LocalDate, settings: Settings, workTimeRules: List<WorkTimeRule>): Int {
        return workTimeRules.getRuleForDate(date)?.dailyWorkMinutes ?: settings.dailyWorkMinutes
    }

    private fun calculateFlextimeSeries(
        workDays: List<WorkDay>,
        settings: Settings,
        timeRange: TimeRange,
        workTimeRules: List<WorkTimeRule>,
        priorWorkDays: List<WorkDay> = emptyList()
    ): List<TimeSeriesPoint> {
        val initialFlextime = if (priorWorkDays.isNotEmpty()) {
            calculateFlextimeUseCase(
                priorWorkDays,
                settings,
                null,
                workTimeRules
            ).totalMinutes
        } else {
            settings.initialFlextimeMinutes.toLong()
        }

        return when (timeRange) {
            is TimeRange.Month -> {
                val sortedDays = workDays.sortedBy { it.date }
                var cumulativeFlextime = initialFlextime
                sortedDays.map { day ->
                    cumulativeFlextime += calculateDayFlextime(day, settings, workTimeRules)
                    TimeSeriesPoint(day.date, cumulativeFlextime)
                }
            }
            else -> {
                val monthlyGroups = workDays.groupBy { YearMonth.from(it.date) }.toSortedMap()
                var cumulativeFlextime = initialFlextime
                val series = mutableListOf<TimeSeriesPoint>()
                for ((yearMonth, daysInMonth) in monthlyGroups) {
                    val balance = calculateFlextimeUseCase(daysInMonth, settings, yearMonth, workTimeRules)
                    cumulativeFlextime += balance.earnedMinutes
                    series.add(TimeSeriesPoint(yearMonth.atDay(1), cumulativeFlextime))
                }
                series
            }
        }
    }

    private fun calculateOvertimeSeries(
        workDays: List<WorkDay>,
        settings: Settings,
        timeRange: TimeRange,
        workTimeRules: List<WorkTimeRule>,
        priorWorkDays: List<WorkDay> = emptyList()
    ): List<TimeSeriesPoint> {
        val initialOvertime = if (priorWorkDays.isNotEmpty()) {
            calculateFlextimeUseCase(
                priorWorkDays,
                settings,
                null,
                workTimeRules
            ).overtimeMinutes
        } else {
            settings.initialOvertimeMinutes.toLong()
        }

        return when (timeRange) {
            is TimeRange.Month -> {
                val sortedDays = workDays.sortedBy { it.date }
                var cumulativeOvertime = initialOvertime
                sortedDays.map { day ->
                    cumulativeOvertime += calculateDayOvertime(day, settings, workTimeRules)
                    TimeSeriesPoint(day.date, cumulativeOvertime)
                }
            }
            else -> {
                val monthlyGroups = workDays.groupBy { YearMonth.from(it.date) }.toSortedMap()
                var cumulativeOvertime = initialOvertime
                val series = mutableListOf<TimeSeriesPoint>()
                for ((yearMonth, daysInMonth) in monthlyGroups) {
                    val balance = calculateFlextimeUseCase(daysInMonth, settings, yearMonth, workTimeRules)
                    cumulativeOvertime += balance.earnedOvertimeMinutes
                    series.add(TimeSeriesPoint(yearMonth.atDay(1), cumulativeOvertime))
                }
                series
            }
        }
    }

    private fun calculateDayFlextime(day: WorkDay, settings: Settings, workTimeRules: List<WorkTimeRule>): Long {
        val dailyTarget = getDailyTarget(day.date, settings, workTimeRules)
        return when (day.dayType) {
            DayType.WORK, DayType.BUSINESS_TRIP -> {
                val result = dayWorkTime(day)
                val ruleForDate = workTimeRules.getRuleForDate(day.date)
                val activeWorkDays = ruleForDate?.workDays ?: DEFAULT_WORK_DAYS
                val isNonWorkingDay = day.date.dayOfWeek !in activeWorkDays
                if (isNonWorkingDay) result.netMinutes else result.netMinutes - dailyTarget
            }
            DayType.SATURDAY_BONUS -> calculateDayWorkTime(day.timeBlocks).netMinutes
            DayType.FLEX_DAY -> -dailyTarget.toLong()
            DayType.OVERTIME_DAY -> 0L
            DayType.VACATION, DayType.SPECIAL_VACATION, DayType.SICK_DAY -> 0L
        }
    }

    private fun calculateDayOvertime(day: WorkDay, settings: Settings, workTimeRules: List<WorkTimeRule>): Long {
        val dailyTarget = getDailyTarget(day.date, settings, workTimeRules)
        return when (day.dayType) {
            DayType.SATURDAY_BONUS -> (calculateDayWorkTime(day.timeBlocks).netMinutes * 0.5).roundToLong()
            DayType.OVERTIME_DAY -> -dailyTarget.toLong()
            else -> 0L
        }
    }

    private fun calculateWeeklyHours(workDays: List<WorkDay>): List<WeeklyWorkHours> {
        val weeklyGroups = workDays.groupBy { day ->
            val weekOfYear = day.date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            val year = day.date.get(IsoFields.WEEK_BASED_YEAR)
            year to weekOfYear
        }

        return weeklyGroups.map { (weekKey, daysInWeek) ->
            val (year, weekOfYear) = weekKey
            var totalMinutes = 0L
            var officeMinutes = 0L
            var homeOfficeMinutes = 0L

            for (day in daysInWeek) {
                val isBusinessTrip = day.dayType == DayType.BUSINESS_TRIP
                val adjustedBlocks = if (isBusinessTrip) {
                    day.timeBlocks.sortedBy { it.startTime }
                } else {
                    CalculateDayWorkTimeUseCase.adjustTimeBlocks(day.timeBlocks)
                }
                val result = dayWorkTime(day)
                totalMinutes += result.netMinutes
                val totalGross = result.grossMinutes
                if (totalGross == 0L) continue

                var dayOfficeGross = 0L
                var dayHomeOfficeGross = 0L
                for (block in adjustedBlocks) {
                    val blockEnd = block.endTime ?: continue
                    val blockMinutes = java.time.Duration.between(block.startTime, blockEnd).toMinutes()
                    if (blockMinutes <= 0) continue
                    if (block.location == WorkLocation.OFFICE || isBusinessTrip) {
                        dayOfficeGross += blockMinutes
                    } else {
                        dayHomeOfficeGross += blockMinutes
                    }
                }
                officeMinutes += dayOfficeGross * result.netMinutes / totalGross
                homeOfficeMinutes += dayHomeOfficeGross * result.netMinutes / totalGross
            }

            WeeklyWorkHours(
                weekOfYear = weekOfYear,
                year = year,
                totalMinutes = totalMinutes,
                officeMinutes = officeMinutes,
                homeOfficeMinutes = homeOfficeMinutes
            )
        }.sortedWith(compareBy({ it.year }, { it.weekOfYear }))
    }

    private fun calculateMonthlyHours(workDays: List<WorkDay>, timeRange: TimeRange): List<TimeSeriesPoint> {
        val monthlyGroups = workDays.groupBy { YearMonth.from(it.date) }
        return when (timeRange) {
            is TimeRange.Year -> {
                (1..12).map { month ->
                    val yearMonth = YearMonth.of(timeRange.year, month)
                    val daysInMonth = monthlyGroups[yearMonth] ?: emptyList()
                    val totalMinutes = daysInMonth.sumOf { day ->
                        dayWorkTime(day).netMinutes
                    }
                    TimeSeriesPoint(yearMonth.atDay(1), totalMinutes)
                }
            }
            is TimeRange.Custom -> {
                val startYm = YearMonth.from(timeRange.start)
                val endYm = YearMonth.from(timeRange.end)
                val months = mutableListOf<YearMonth>()
                var curr = startYm
                while (!curr.isAfter(endYm)) {
                    months.add(curr)
                    curr = curr.plusMonths(1)
                }
                months.map { yearMonth ->
                    val daysInMonth = monthlyGroups[yearMonth] ?: emptyList()
                    val totalMinutes = daysInMonth.sumOf { day ->
                        dayWorkTime(day).netMinutes
                    }
                    TimeSeriesPoint(yearMonth.atDay(1), totalMinutes)
                }
            }
            is TimeRange.Month -> {
                val yearMonth = timeRange.yearMonth
                val daysInMonth = monthlyGroups[yearMonth] ?: emptyList()
                val totalMinutes = daysInMonth.sumOf { day ->
                    dayWorkTime(day).netMinutes
                }
                listOf(TimeSeriesPoint(yearMonth.atDay(1), totalMinutes))
            }
        }
    }

    private fun calculateLocationDistribution(workDays: List<WorkDay>): LocationDistribution {
        var officeMinutes = 0L
        var homeOfficeMinutes = 0L

        for (day in workDays) {
            val isBusinessTrip = day.dayType == DayType.BUSINESS_TRIP
            val adjustedBlocks = if (isBusinessTrip) {
                day.timeBlocks.sortedBy { it.startTime }
            } else {
                CalculateDayWorkTimeUseCase.adjustTimeBlocks(day.timeBlocks)
            }
            val result = dayWorkTime(day)
            val totalGross = result.grossMinutes
            if (totalGross == 0L) continue

            var dayOfficeGross = 0L
            var dayHomeOfficeGross = 0L
            for (block in adjustedBlocks) {
                val blockEnd = block.endTime ?: continue
                val blockMinutes = java.time.Duration.between(block.startTime, blockEnd).toMinutes()
                if (blockMinutes <= 0) continue
                if (block.location == WorkLocation.OFFICE || isBusinessTrip) {
                    dayOfficeGross += blockMinutes
                } else {
                    dayHomeOfficeGross += blockMinutes
                }
            }
            officeMinutes += dayOfficeGross * result.netMinutes / totalGross
            homeOfficeMinutes += dayHomeOfficeGross * result.netMinutes / totalGross
        }

        return LocationDistribution(
            officeMinutes = officeMinutes,
            homeOfficeMinutes = homeOfficeMinutes
        )
    }
}
