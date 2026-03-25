package com.flex.domain.model

import java.time.YearMonth

data class QuotaRule(
    val id: Long = 0,
    val validFrom: YearMonth,
    val officeQuotaPercent: Int,
    val officeQuotaMinDays: Int
)
