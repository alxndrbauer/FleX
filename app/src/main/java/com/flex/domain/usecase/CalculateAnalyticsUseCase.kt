package com.flex.domain.usecase

import com.flex.domain.model.AnalyticsData
import com.flex.domain.model.DayType
import com.flex.domain.model.LocationDistribution
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeRange
import com.flex.domain.model.TimeSeriesPoint
import com.flex.domain.model.WeekComparison
import com.flex.domain.model.WeeklyWorkHours
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoField
import java.time.temporal.IsoFields
import javax.inject.Inject
import kotlin.math.roundToLong

class CalculateAnalyticsUseCase @Inject constructor(
    private val calculateDayWorkTime: CalculateDayWorkTimeUseCase,
    private val calculateFlextimeUseCase: CalculateFlextimeUseCase
) {

    operator fun invoke(
        workDays: List<WorkDay>,
        settings: Settings,
        timeRange: TimeRange
    ): AnalyticsData {
        // Filter out planned days - only analyze actual work
        val actualWorkDays = workDays.filter { !it.isPlanned }

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
            flextimeSeries = calculateFlextimeSeries(actualWorkDays, settings, timeRange),
            overtimeSeries = calculateOvertimeSeries(actualWorkDays, settings, timeRange),
            weeklyHours = calculateWeeklyHours(actualWorkDays),
            monthlyHours = calculateMonthlyHours(actualWorkDays),
            locationDistribution = calculateLocationDistribution(actualWorkDays),
            weekComparison = calculateWeekComparison(actualWorkDays)
        )
    }

    private fun calculateFlextimeSeries(
        workDays: List<WorkDay>,
        settings: Settings,
        timeRange: TimeRange
    ): List<TimeSeriesPoint> {
        return when (timeRange) {
            is TimeRange.Month -> {
                val sortedDays = workDays.sortedBy { it.date }
                var cumulativeFlextime = settings.initialFlextimeMinutes.toLong()
                sortedDays.map { day ->
                    cumulativeFlextime += calculateDayFlextime(day, settings)
                    TimeSeriesPoint(day.date, cumulativeFlextime)
                }
            }
            else -> {
                val monthlyGroups = workDays.groupBy { YearMonth.from(it.date) }.toSortedMap()
                var cumulativeFlextime = settings.initialFlextimeMinutes.toLong()
                val series = mutableListOf<TimeSeriesPoint>()
                for ((yearMonth, daysInMonth) in monthlyGroups) {
                    val balance = calculateFlextimeUseCase(daysInMonth, settings, yearMonth)
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
        timeRange: TimeRange
    ): List<TimeSeriesPoint> {
        return when (timeRange) {
            is TimeRange.Month -> {
                val sortedDays = workDays.sortedBy { it.date }
                var cumulativeOvertime = settings.initialOvertimeMinutes.toLong()
                sortedDays.map { day ->
                    cumulativeOvertime += calculateDayOvertime(day, settings)
                    TimeSeriesPoint(day.date, cumulativeOvertime)
                }
            }
            else -> {
                val monthlyGroups = workDays.groupBy { YearMonth.from(it.date) }.toSortedMap()
                var cumulativeOvertime = settings.initialOvertimeMinutes.toLong()
                val series = mutableListOf<TimeSeriesPoint>()
                for ((yearMonth, daysInMonth) in monthlyGroups) {
                    val balance = calculateFlextimeUseCase(daysInMonth, settings, yearMonth)
                    cumulativeOvertime += balance.earnedOvertimeMinutes
                    series.add(TimeSeriesPoint(yearMonth.atDay(1), cumulativeOvertime))
                }
                series
            }
        }
    }

    private fun calculateDayFlextime(day: WorkDay, settings: Settings): Long {
        return when (day.dayType) {
            DayType.WORK -> {
                val result = calculateDayWorkTime(day.timeBlocks)
                val isWeekend = day.date.dayOfWeek == DayOfWeek.SATURDAY || day.date.dayOfWeek == DayOfWeek.SUNDAY
                if (isWeekend) result.netMinutes else result.netMinutes - settings.dailyWorkMinutes
            }
            DayType.SATURDAY_BONUS -> calculateDayWorkTime(day.timeBlocks).netMinutes
            DayType.FLEX_DAY -> -settings.dailyWorkMinutes.toLong()
            DayType.OVERTIME_DAY -> 0L
            DayType.VACATION, DayType.SPECIAL_VACATION, DayType.SICK_DAY -> 0L
        }
    }

    private fun calculateDayOvertime(day: WorkDay, settings: Settings): Long {
        return when (day.dayType) {
            DayType.SATURDAY_BONUS -> (calculateDayWorkTime(day.timeBlocks).netMinutes * 0.5).roundToLong()
            DayType.OVERTIME_DAY -> -settings.dailyWorkMinutes.toLong()
            else -> 0L
        }
    }

    private fun calculateWeeklyHours(workDays: List<WorkDay>): List<WeeklyWorkHours> {
        val weeklyGroups = workDays.groupBy { day ->
            val weekOfYear = day.date.get(ChronoField.ALIGNED_WEEK_OF_YEAR)
            val year = day.date.year
            year to weekOfYear
        }

        return weeklyGroups.map { (weekKey, daysInWeek) ->
            val (year, weekOfYear) = weekKey
            var totalMinutes = 0L
            var officeMinutes = 0L
            var homeOfficeMinutes = 0L

            for (day in daysInWeek) {
                val adjustedBlocks = CalculateDayWorkTimeUseCase.adjustTimeBlocks(day.timeBlocks)
                val result = calculateDayWorkTime(day.timeBlocks)
                totalMinutes += result.netMinutes
                val totalGross = result.grossMinutes
                if (totalGross == 0L) continue

                var dayOfficeGross = 0L
                var dayHomeOfficeGross = 0L
                for (block in adjustedBlocks) {
                    val blockEnd = block.endTime ?: continue
                    val blockMinutes = java.time.Duration.between(block.startTime, blockEnd).toMinutes()
                    if (blockMinutes <= 0) continue
                    when (block.location) {
                        WorkLocation.OFFICE -> dayOfficeGross += blockMinutes
                        WorkLocation.HOME_OFFICE -> dayHomeOfficeGross += blockMinutes
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

    private fun calculateMonthlyHours(workDays: List<WorkDay>): List<TimeSeriesPoint> {
        val monthlyGroups = workDays
            .groupBy { YearMonth.from(it.date) }
            .toSortedMap()

        return monthlyGroups.map { (yearMonth, daysInMonth) ->
            val totalMinutes = daysInMonth.sumOf { day ->
                calculateDayWorkTime(day.timeBlocks).netMinutes
            }
            TimeSeriesPoint(yearMonth.atDay(1), totalMinutes)
        }
    }

    private fun calculateWeekComparison(workDays: List<WorkDay>): WeekComparison? {
        if (workDays.isEmpty()) return null
        val today = LocalDate.now()
        val currentIsoWeek = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val currentIsoYear = today.get(IsoFields.WEEK_BASED_YEAR)
        val prevDate = today.minusWeeks(1)
        val prevIsoWeek = prevDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val prevIsoYear = prevDate.get(IsoFields.WEEK_BASED_YEAR)

        val currentMinutes = workDays
            .filter {
                it.date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) == currentIsoWeek &&
                it.date.get(IsoFields.WEEK_BASED_YEAR) == currentIsoYear
            }
            .sumOf { calculateDayWorkTime(it.timeBlocks).netMinutes }
        val prevMinutes = workDays
            .filter {
                it.date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) == prevIsoWeek &&
                it.date.get(IsoFields.WEEK_BASED_YEAR) == prevIsoYear
            }
            .sumOf { calculateDayWorkTime(it.timeBlocks).netMinutes }

        return if (currentMinutes == 0L && prevMinutes == 0L) null
        else WeekComparison(currentMinutes, prevMinutes)
    }

    private fun calculateLocationDistribution(workDays: List<WorkDay>): LocationDistribution {
        var officeMinutes = 0L
        var homeOfficeMinutes = 0L

        for (day in workDays) {
            val adjustedBlocks = CalculateDayWorkTimeUseCase.adjustTimeBlocks(day.timeBlocks)
            val result = calculateDayWorkTime(day.timeBlocks)
            val totalGross = result.grossMinutes
            if (totalGross == 0L) continue

            var dayOfficeGross = 0L
            var dayHomeOfficeGross = 0L
            for (block in adjustedBlocks) {
                val blockEnd = block.endTime ?: continue
                val blockMinutes = java.time.Duration.between(block.startTime, blockEnd).toMinutes()
                if (blockMinutes <= 0) continue
                when (block.location) {
                    WorkLocation.OFFICE -> dayOfficeGross += blockMinutes
                    WorkLocation.HOME_OFFICE -> dayHomeOfficeGross += blockMinutes
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
