package com.vrema.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vrema.data.local.entity.QuotaRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuotaRuleDao {

    @Query("SELECT * FROM quota_rules ORDER BY validFrom ASC")
    fun getAllRules(): Flow<List<QuotaRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: QuotaRuleEntity): Long

    @Delete
    suspend fun delete(rule: QuotaRuleEntity)

    @Query("SELECT * FROM quota_rules ORDER BY validFrom ASC")
    suspend fun getAllRulesSync(): List<QuotaRuleEntity>

    @Query("DELETE FROM quota_rules")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<QuotaRuleEntity>)
}
