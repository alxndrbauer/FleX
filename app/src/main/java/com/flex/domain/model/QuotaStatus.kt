package com.flex.domain.model

data class QuotaStatus(
    val officeMinutes: Long = 0,
    val homeOfficeMinutes: Long = 0,
    val officeDays: Int = 0,
    val homeOfficeDays: Int = 0,
    val officePercent: Double = 0.0,
    val percentQuotaMet: Boolean = false,
    val daysQuotaMet: Boolean = false,
    val remainingWorkDays: Int = 0,
    val requiredOfficeDaysForQuota: Int = 0
) {
    val quotaMet: Boolean get() = percentQuotaMet || daysQuotaMet
}
