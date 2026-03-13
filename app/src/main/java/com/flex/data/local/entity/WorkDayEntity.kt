package com.flex.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "work_days",
    indices = [Index(value = ["date"], unique = true)]
)
data class WorkDayEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // ISO LocalDate string
    val location: String, // WorkLocation name
    val dayType: String, // DayType name
    val isPlanned: Boolean = false,
    val note: String? = null
)
