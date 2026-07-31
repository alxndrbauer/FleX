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

fun List<WorkTimeRule>.getRuleForDate(date: LocalDate): WorkTimeRule? {
    val ym = YearMonth.from(date)
    return filter { !it.validFrom.isAfter(ym) }.maxByOrNull { it.validFrom }
}

fun List<WorkTimeRule>.getRuleForMonth(ym: YearMonth): WorkTimeRule? {
    return filter { !it.validFrom.isAfter(ym) }.maxByOrNull { it.validFrom }
}
