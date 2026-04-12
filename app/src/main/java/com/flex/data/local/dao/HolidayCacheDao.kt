package com.flex.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flex.data.local.entity.HolidayCacheEntity

@Dao
interface HolidayCacheDao {

    @Query("SELECT * FROM holiday_cache WHERE year = :year AND federalState = :federalState")
    suspend fun getHolidays(year: Int, federalState: String): List<HolidayCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(holidays: List<HolidayCacheEntity>)

    @Query("DELETE FROM holiday_cache WHERE year = :year AND federalState = :federalState")
    suspend fun deleteForYearAndState(year: Int, federalState: String)
}
