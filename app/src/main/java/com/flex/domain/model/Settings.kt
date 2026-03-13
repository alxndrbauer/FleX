package com.flex.domain.model

data class Settings(
    val id: Long = 1,
    val dailyWorkMinutes: Int = 426,
    val monthlyWorkMinutes: Int = 9266,
    val officeQuotaPercent: Int = 40,
    val officeQuotaMinDays: Int = 8,
    val initialFlextimeMinutes: Int = 0,
    val initialOvertimeMinutes: Int = 0,
    val annualVacationDays: Int = 30,
    val carryOverVacationDays: Int = 0,
    val specialVacationDays: Int = 5,
    val settingsYear: Int = java.time.Year.now().value
)
