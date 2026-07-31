package com.flex.domain.model

import java.time.LocalDate

data class WorkTimeRule(
    val id: Long = 0,
    val validFrom: LocalDate,
    val dailyWorkMinutes: Int,
    val monthlyWorkMinutes: Int
)

fun List<WorkTimeRule>.getRuleForDate(date: LocalDate): WorkTimeRule? {
    return filter { !it.validFrom.isAfter(date) }.maxByOrNull { it.validFrom }
}

fun List<WorkTimeRule>.getRuleForMonth(ym: java.time.YearMonth): WorkTimeRule? {
    return filter { !it.validFrom.isAfter(ym.atEndOfMonth()) }.maxByOrNull { it.validFrom }
}
