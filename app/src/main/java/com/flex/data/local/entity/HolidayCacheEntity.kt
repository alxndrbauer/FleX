package com.flex.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "holiday_cache")
data class HolidayCacheEntity(
    @PrimaryKey
    val id: String,           // "${date}_${federalState}" e.g. "2026-01-01_HH"
    val date: String,         // "YYYY-MM-DD"
    val name: String,         // holiday name
    val federalState: String, // "HH", "BY" etc.
    val year: Int             // for query convenience
)
