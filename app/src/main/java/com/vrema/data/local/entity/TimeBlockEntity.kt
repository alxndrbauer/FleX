package com.vrema.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "time_blocks",
    foreignKeys = [
        ForeignKey(
            entity = WorkDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["workDayId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workDayId"])]
)
data class TimeBlockEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workDayId: Long,
    val startTime: String, // ISO LocalTime string
    val endTime: String? = null, // null = running
    val isDuration: Boolean = false // true = total duration entry, no break deduction
)
