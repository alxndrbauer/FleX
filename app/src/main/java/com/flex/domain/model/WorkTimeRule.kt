package com.flex.domain.model

import java.time.LocalDate

data class WorkTimeRule(
    val id: Long = 0,
    val validFrom: LocalDate,
    val dailyWorkMinutes: Int,
    val monthlyWorkMinutes: Int
)
