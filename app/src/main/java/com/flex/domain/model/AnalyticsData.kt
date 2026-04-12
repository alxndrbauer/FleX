package com.flex.domain.model

import java.time.LocalDate
import java.time.YearMonth

data class TimeSeriesPoint(
    val date: LocalDate,
    val value: Long  // minutes
) {
    val yearMonth: YearMonth get() = YearMonth.from(date)
}

data class WeeklyWorkHours(
    val weekOfYear: Int,
    val year: Int,
    val totalMinutes: Long,
    val officeMinutes: Long,
    val homeOfficeMinutes: Long
) {
    val totalHours: Double get() = totalMinutes / 60.0
    val officeHours: Double get() = officeMinutes / 60.0
    val homeOfficeHours: Double get() = homeOfficeMinutes / 60.0
}

data class LocationDistribution(
    val officeMinutes: Long,
    val homeOfficeMinutes: Long
) {
    private val totalMinutes: Long get() = officeMinutes + homeOfficeMinutes
    val officePercent: Double
        get() = if (totalMinutes == 0L) 0.0 else (officeMinutes * 100.0) / totalMinutes
    val homeOfficePercent: Double
        get() = if (totalMinutes == 0L) 0.0 else (homeOfficeMinutes * 100.0) / totalMinutes

    val officeHours: Double get() = officeMinutes / 60.0
    val homeOfficeHours: Double get() = homeOfficeMinutes / 60.0
}

data class WeekComparison(
    val currentWeekMinutes: Long,
    val previousWeekMinutes: Long
) {
    val deltaMinutes: Long get() = currentWeekMinutes - previousWeekMinutes
    val hasData: Boolean get() = currentWeekMinutes > 0 || previousWeekMinutes > 0
}

data class AnalyticsData(
    val flextimeSeries: List<TimeSeriesPoint>,
    val overtimeSeries: List<TimeSeriesPoint>,
    val weeklyHours: List<WeeklyWorkHours>,
    val monthlyHours: List<TimeSeriesPoint>,
    val locationDistribution: LocationDistribution,
    val weekComparison: WeekComparison? = null
) {
    val isEmpty: Boolean
        get() = flextimeSeries.isEmpty() && overtimeSeries.isEmpty() &&
                weeklyHours.isEmpty() && monthlyHours.isEmpty() && weekComparison == null
}
