package com.vrema.domain.usecase

import com.vrema.domain.model.AnalyticsData
import com.vrema.domain.model.LocationDistribution
import com.vrema.domain.model.Settings
import com.vrema.domain.model.TimeRange
import com.vrema.domain.model.TimeSeriesPoint
import com.vrema.domain.model.WeeklyWorkHours
import com.vrema.domain.model.WorkDay
import com.vrema.domain.model.WorkLocation
import java.time.YearMonth
import java.time.temporal.ChronoField
import javax.inject.Inject

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
            flextimeSeries = calculateFlextimeSeries(actualWorkDays, settings),
            overtimeSeries = calculateOvertimeSeries(actualWorkDays, settings),
            weeklyHours = calculateWeeklyHours(actualWorkDays),
            monthlyHours = calculateMonthlyHours(actualWorkDays),
            locationDistribution = calculateLocationDistribution(actualWorkDays)
        )
    }

    private fun calculateFlextimeSeries(
        workDays: List<WorkDay>,
        settings: Settings
    ): List<TimeSeriesPoint> {
        val monthlyGroups = workDays
            .groupBy { YearMonth.from(it.date) }
            .toSortedMap()

        var cumulativeFlextime = 0L
        val series = mutableListOf<TimeSeriesPoint>()

        for ((yearMonth, daysInMonth) in monthlyGroups) {
            val balance = calculateFlextimeUseCase(daysInMonth, settings, yearMonth)
            cumulativeFlextime += balance.totalMinutes
            series.add(TimeSeriesPoint(yearMonth, cumulativeFlextime))
        }

        return series
    }

    private fun calculateOvertimeSeries(
        workDays: List<WorkDay>,
        settings: Settings
    ): List<TimeSeriesPoint> {
        val monthlyGroups = workDays
            .groupBy { YearMonth.from(it.date) }
            .toSortedMap()

        var cumulativeOvertime = 0L
        val series = mutableListOf<TimeSeriesPoint>()

        for ((yearMonth, daysInMonth) in monthlyGroups) {
            val balance = calculateFlextimeUseCase(daysInMonth, settings, yearMonth)
            cumulativeOvertime += balance.overtimeMinutes
            series.add(TimeSeriesPoint(yearMonth, cumulativeOvertime))
        }

        return series
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

                for (block in adjustedBlocks) {
                    val blockEnd = block.endTime ?: continue
                    val blockMinutes = java.time.Duration.between(block.startTime, blockEnd).toMinutes()
                    if (blockMinutes <= 0) continue
                    when (block.location) {
                        WorkLocation.OFFICE -> officeMinutes += blockMinutes
                        WorkLocation.HOME_OFFICE -> homeOfficeMinutes += blockMinutes
                    }
                }
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
            TimeSeriesPoint(yearMonth, totalMinutes)
        }
    }

    private fun calculateLocationDistribution(workDays: List<WorkDay>): LocationDistribution {
        var officeMinutes = 0L
        var homeOfficeMinutes = 0L

        for (day in workDays) {
            val adjustedBlocks = CalculateDayWorkTimeUseCase.adjustTimeBlocks(day.timeBlocks)
            for (block in adjustedBlocks) {
                val blockEnd = block.endTime ?: continue
                val blockMinutes = java.time.Duration.between(block.startTime, blockEnd).toMinutes()
                if (blockMinutes <= 0) continue
                when (block.location) {
                    WorkLocation.OFFICE -> officeMinutes += blockMinutes
                    WorkLocation.HOME_OFFICE -> homeOfficeMinutes += blockMinutes
                }
            }
        }

        return LocationDistribution(
            officeMinutes = officeMinutes,
            homeOfficeMinutes = homeOfficeMinutes
        )
    }
}
