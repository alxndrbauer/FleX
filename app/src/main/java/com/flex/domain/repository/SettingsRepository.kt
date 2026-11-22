package com.flex.domain.repository

import com.flex.domain.model.QuotaRule
import com.flex.domain.model.Settings
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.model.YearClosing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import java.time.LocalDate
import java.time.YearMonth

interface SettingsRepository {
    fun getSettings(): Flow<Settings>
    suspend fun saveSettings(settings: Settings)
    fun getQuotaRules(): Flow<List<QuotaRule>>
    suspend fun saveQuotaRule(rule: QuotaRule): Long
    suspend fun deleteQuotaRule(rule: QuotaRule)
    fun getQuotaRuleForMonth(yearMonth: YearMonth, rules: List<QuotaRule>): QuotaRule?
    fun getWorkTimeRules(): Flow<List<WorkTimeRule>>
    suspend fun saveWorkTimeRule(rule: WorkTimeRule): Long
    suspend fun deleteWorkTimeRule(rule: WorkTimeRule)
    fun getWorkTimeRuleForDate(date: LocalDate, rules: List<WorkTimeRule>): WorkTimeRule?
    fun getYearClosings(): Flow<List<YearClosing>> = flowOf(emptyList())
    fun getYearClosing(year: Int): Flow<YearClosing?> = flowOf(null)
    suspend fun saveYearClosing(closing: YearClosing) {}
    suspend fun applyYearClosing(closing: YearClosing, newSettings: Settings) {
        saveYearClosing(closing)
        saveSettings(newSettings)
    }
}

fun SettingsRepository.observeYearClosingsOrEmpty(): Flow<List<YearClosing>> {
    val flow = runCatching { getYearClosings() }.getOrNull() ?: return flowOf(emptyList())
    return flow.onStart { emit(emptyList()) }.distinctUntilChanged()
}
