package com.flex.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quota_rules")
data class QuotaRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val validFrom: String, // YearMonth as "2026-04"
    val officeQuotaPercent: Int,
    val officeQuotaMinDays: Int
)
