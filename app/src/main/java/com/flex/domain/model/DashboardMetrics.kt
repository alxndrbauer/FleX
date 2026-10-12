package com.flex.domain.model

import java.time.LocalDate
import java.time.LocalTime

data class DashboardForecast(
    val quotaPacingText: String,
    val isQuotaMet: Boolean,
    val projectedFlextimeText: String? = null,
    val plannedOfficeDays: Int = 0,
    val plannedHomeOfficeDays: Int = 0,
    val projectedOfficeDays: Int = 0,
    val projectedOfficePercent: Double = 0.0,
    val isProjectedQuotaMet: Boolean = false,
    val projectedQuotaText: String = ""
)

enum class UpcomingEventType {
    VACATION,
    FLEX_DAY,
    BUSINESS_TRIP,
    PUBLIC_HOLIDAY
}

data class UpcomingEvent(
    val date: LocalDate,
    val endDate: LocalDate? = null,
    val title: String,
    val daysCount: Int = 1,
    val eventType: UpcomingEventType
)

data class WorkHabits(
    val avgStartTime: LocalTime? = null,
    val avgEndTime: LocalTime? = null,
    val avgNetMinutes: Long = 0L,
    val avgBreakMinutes: Long = 0L
)

data class CurrentWeekStatus(
    val weekNumber: Int,
    val workedMinutes: Long = 0L,
    val targetMinutes: Long = 0L,
    val officeDays: Int = 0,
    val homeOfficeDays: Int = 0
)

data class AbsenceSummary(
    val vacationDays: Int = 0,
    val specialVacationDays: Int = 0,
    val flexDays: Int = 0,
    val sickDays: Int = 0,
    val businessTripDays: Int = 0
)

enum class FlextimeChartPeriod {
    MONTH,
    YEAR
}

