package com.flex.domain.model

import java.time.LocalDate
import java.time.YearMonth

data class WorkTimeRule(
    val id: Long = 0,
    val validFrom: YearMonth,
    val dailyWorkMinutes: Int,
    val monthlyWorkMinutes: Int
)

fun List<WorkTimeRule>.getRuleForDate(date: LocalDate): WorkTimeRule? {
    val ym = YearMonth.from(date)
    return filter { !it.validFrom.isAfter(ym) }.maxByOrNull { it.validFrom }
}

fun List<WorkTimeRule>.getRuleForMonth(ym: YearMonth): WorkTimeRule? {
    return filter { !it.validFrom.isAfter(ym) }.maxByOrNull { it.validFrom }
}
