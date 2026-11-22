package com.flex.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "year_closings")
data class YearClosingEntity(
    @PrimaryKey
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
