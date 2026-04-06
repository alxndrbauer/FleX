package com.flex.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey val workDayId: Long,
    val calendarEventId: Long,
    val calendarId: Long
)
