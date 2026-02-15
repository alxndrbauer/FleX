package com.vrema.domain.repository

import com.vrema.domain.model.QuotaRule
import com.vrema.domain.model.Settings
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface SettingsRepository {
    fun getSettings(): Flow<Settings>
    suspend fun saveSettings(settings: Settings)
    fun getQuotaRules(): Flow<List<QuotaRule>>
    suspend fun saveQuotaRule(rule: QuotaRule): Long
    suspend fun deleteQuotaRule(rule: QuotaRule)
    fun getQuotaRuleForMonth(yearMonth: YearMonth, rules: List<QuotaRule>): QuotaRule?
}
