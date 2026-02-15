package com.vrema.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vrema.data.local.entity.WorkDayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkDayDao {

    @Query("SELECT * FROM work_days WHERE date BETWEEN :startDate AND :endDate ORDER BY date")
    fun getWorkDaysBetween(startDate: String, endDate: String): Flow<List<WorkDayEntity>>

    @Query("SELECT * FROM work_days WHERE date = :date LIMIT 1")
    fun getWorkDayByDate(date: String): Flow<WorkDayEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workDay: WorkDayEntity): Long

    @Update
    suspend fun update(workDay: WorkDayEntity)

    @Delete
    suspend fun delete(workDay: WorkDayEntity)

    @Query("UPDATE work_days SET isPlanned = 0 WHERE date >= :startDate AND date <= :endDate AND isPlanned = 1")
    suspend fun confirmPlannedDays(startDate: String, endDate: String)

    @Query("SELECT * FROM work_days ORDER BY date")
    suspend fun getAllWorkDays(): List<WorkDayEntity>

    @Query("DELETE FROM work_days")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(workDays: List<WorkDayEntity>)
}
