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
}
