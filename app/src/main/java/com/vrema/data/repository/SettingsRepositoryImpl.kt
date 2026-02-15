package com.vrema.data.repository

import com.vrema.data.local.dao.QuotaRuleDao
import com.vrema.data.local.dao.SettingsDao
import com.vrema.data.local.entity.QuotaRuleEntity
import com.vrema.data.local.entity.SettingsEntity
import com.vrema.domain.model.QuotaRule
import com.vrema.domain.model.Settings
import com.vrema.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDao: SettingsDao,
    private val quotaRuleDao: QuotaRuleDao
) : SettingsRepository {

    override fun getSettings(): Flow<Settings> {
        return settingsDao.getSettings().map { entity ->
            entity?.toDomain() ?: Settings()
        }
    }

    override suspend fun saveSettings(settings: Settings) {
        settingsDao.insert(settings.toEntity())
    }

    override fun getQuotaRules(): Flow<List<QuotaRule>> {
        return quotaRuleDao.getAllRules().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveQuotaRule(rule: QuotaRule): Long {
        return quotaRuleDao.insert(rule.toEntity())
    }

    override suspend fun deleteQuotaRule(rule: QuotaRule) {
        quotaRuleDao.delete(rule.toEntity())
    }

    override fun getQuotaRuleForMonth(yearMonth: YearMonth, rules: List<QuotaRule>): QuotaRule? {
        return rules
            .filter { it.validFrom <= yearMonth }
            .maxByOrNull { it.validFrom }
    }

    private fun SettingsEntity.toDomain() = Settings(
        id = id,
        dailyWorkMinutes = dailyWorkMinutes,
        monthlyWorkMinutes = monthlyWorkMinutes,
        officeQuotaPercent = officeQuotaPercent,
        officeQuotaMinDays = officeQuotaMinDays,
        initialFlextimeMinutes = initialFlextimeMinutes,
        initialOvertimeMinutes = initialOvertimeMinutes,
        annualVacationDays = annualVacationDays,
        carryOverVacationDays = carryOverVacationDays,
        specialVacationDays = specialVacationDays,
        settingsYear = settingsYear
    )

    private fun Settings.toEntity() = SettingsEntity(
        id = id,
        dailyWorkMinutes = dailyWorkMinutes,
        monthlyWorkMinutes = monthlyWorkMinutes,
        officeQuotaPercent = officeQuotaPercent,
        officeQuotaMinDays = officeQuotaMinDays,
        initialFlextimeMinutes = initialFlextimeMinutes,
        initialOvertimeMinutes = initialOvertimeMinutes,
        annualVacationDays = annualVacationDays,
        carryOverVacationDays = carryOverVacationDays,
        specialVacationDays = specialVacationDays,
        settingsYear = settingsYear
    )

    private fun QuotaRuleEntity.toDomain() = QuotaRule(
        id = id,
        validFrom = YearMonth.parse(validFrom),
        officeQuotaPercent = officeQuotaPercent,
        officeQuotaMinDays = officeQuotaMinDays
    )

    private fun QuotaRule.toEntity() = QuotaRuleEntity(
        id = id,
        validFrom = validFrom.toString(),
        officeQuotaPercent = officeQuotaPercent,
        officeQuotaMinDays = officeQuotaMinDays
    )
}
