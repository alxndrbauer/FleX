package com.flex.data.local.dao

import androidx.room.*
import com.flex.data.local.entity.WorkTimeRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkTimeRuleDao {
    @Query("SELECT * FROM work_time_rules ORDER BY validFrom ASC")
    fun getAllRules(): Flow<List<WorkTimeRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: WorkTimeRuleEntity): Long

    @Delete
    suspend fun delete(rule: WorkTimeRuleEntity)

    @Query("SELECT * FROM work_time_rules ORDER BY validFrom ASC")
    suspend fun getAllRulesSync(): List<WorkTimeRuleEntity>

    @Query("DELETE FROM work_time_rules")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<WorkTimeRuleEntity>)
}
