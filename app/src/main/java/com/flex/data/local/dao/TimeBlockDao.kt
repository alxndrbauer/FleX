package com.flex.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.flex.data.local.entity.TimeBlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeBlockDao {

    @Query("SELECT * FROM time_blocks WHERE workDayId = :workDayId ORDER BY startTime")
    fun getTimeBlocksForDay(workDayId: Long): Flow<List<TimeBlockEntity>>

    @Query("SELECT * FROM time_blocks WHERE workDayId IN (:workDayIds) ORDER BY startTime")
    suspend fun getTimeBlocksForDays(workDayIds: List<Long>): List<TimeBlockEntity>

    @Query("SELECT * FROM time_blocks WHERE workDayId IN (:workDayIds) ORDER BY startTime")
    fun getTimeBlocksForDaysFlow(workDayIds: List<Long>): Flow<List<TimeBlockEntity>>

    @Query("SELECT * FROM time_blocks ORDER BY startTime")
    fun getAllTimeBlocksFlow(): Flow<List<TimeBlockEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(timeBlock: TimeBlockEntity): Long

    @Update
    suspend fun update(timeBlock: TimeBlockEntity)

    @Delete
    suspend fun delete(timeBlock: TimeBlockEntity)

    @Query("DELETE FROM time_blocks WHERE workDayId = :workDayId")
    suspend fun deleteAllForDay(workDayId: Long)

    @Query("SELECT * FROM time_blocks ORDER BY startTime")
    suspend fun getAllTimeBlocks(): List<TimeBlockEntity>

    @Query("DELETE FROM time_blocks")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(timeBlocks: List<TimeBlockEntity>)
}
