package com.flex.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flex.data.local.entity.CalendarEventEntity

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events WHERE workDayId = :workDayId LIMIT 1")
    suspend fun getByWorkDayId(workDayId: Long): CalendarEventEntity?

    @Query("SELECT * FROM calendar_events")
    suspend fun getAll(): List<CalendarEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CalendarEventEntity)

    @Query("DELETE FROM calendar_events WHERE workDayId = :workDayId")
    suspend fun deleteByWorkDayId(workDayId: Long)

    /** Returns all DB mappings that share the same calendar event (e.g. a vacation group). */
    @Query("SELECT * FROM calendar_events WHERE calendarEventId = :calendarEventId")
    suspend fun getByCalendarEventId(calendarEventId: Long): List<CalendarEventEntity>

    /** Removes all DB mappings for a calendar event (used when deleting a group event). */
    @Query("DELETE FROM calendar_events WHERE calendarEventId = :calendarEventId")
    suspend fun deleteByCalendarEventId(calendarEventId: Long)
}
