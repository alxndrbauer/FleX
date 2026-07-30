package com.flex.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "work_time_rules")
data class WorkTimeRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val validFrom: String,
    val dailyWorkMinutes: Int,
    val monthlyWorkMinutes: Int
)
