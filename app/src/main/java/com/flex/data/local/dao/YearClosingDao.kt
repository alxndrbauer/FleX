package com.flex.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flex.data.local.entity.YearClosingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface YearClosingDao {
    @Query("SELECT * FROM year_closings ORDER BY year ASC")
    fun getAllClosings(): Flow<List<YearClosingEntity>>

    @Query("SELECT * FROM year_closings ORDER BY year ASC")
    suspend fun getAllClosingsSync(): List<YearClosingEntity>

    @Query("SELECT * FROM year_closings WHERE year = :year LIMIT 1")
    fun getClosingForYear(year: Int): Flow<YearClosingEntity?>

    @Query("SELECT * FROM year_closings WHERE year = :year LIMIT 1")
    suspend fun getClosingForYearSync(year: Int): YearClosingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(closing: YearClosingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(closings: List<YearClosingEntity>)

    @Query("DELETE FROM year_closings")
    suspend fun deleteAll()
}
