package com.flex.domain.model

/**
 * Immutable snapshot of a closed calendar year's balances and vacation entitlements.
 */
data class YearClosing(
    val year: Int,
    val annualVacationDays: Int,
    val carryOverVacationDays: Int,
    val specialVacationDays: Int,
    val specialVacationPaidOut: Boolean,
    val startFlextimeMinutes: Int,
    val startOvertimeMinutes: Int,
    val endFlextimeMinutes: Int,
    val endOvertimeMinutes: Int,
    val usedVacationDays: Int,
    val carryOverToNextYearManual: Boolean = false,
    val closedAt: Long = 0L
)
