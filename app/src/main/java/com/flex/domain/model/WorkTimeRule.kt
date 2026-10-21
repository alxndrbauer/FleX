package com.flex.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

val DEFAULT_WORK_DAYS: Set<DayOfWeek> = setOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
)

data class WorkTimeRule(
    val id: Long = 0,
    val validFrom: YearMonth,
    val dailyWorkMinutes: Int,
    val monthlyWorkMinutes: Int,
    val workDays: Set<DayOfWeek> = DEFAULT_WORK_DAYS
)

fun getDailyWorkTargetMinutes(
    date: LocalDate,
    dayType: DayType?,
    defaultDailyWorkMinutes: Int,
    rule: WorkTimeRule?
): Int {
    if (dayType != null && dayType !in setOf(DayType.WORK, DayType.BUSINESS_TRIP)) return 0

    val workDays = rule?.workDays ?: DEFAULT_WORK_DAYS
    if (date.dayOfWeek !in workDays) return 0

    return rule?.dailyWorkMinutes ?: defaultDailyWorkMinutes
}

fun List<WorkTimeRule>.getRuleForDate(date: LocalDate): WorkTimeRule? {
    val ym = YearMonth.from(date)
    return filter { !it.validFrom.isAfter(ym) }.maxByOrNull { it.validFrom }
}

fun List<WorkTimeRule>.getRuleForMonth(ym: YearMonth): WorkTimeRule? {
    return filter { !it.validFrom.isAfter(ym) }.maxByOrNull { it.validFrom }
}
