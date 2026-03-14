package com.flex.data.repository

import com.google.common.truth.Truth.assertThat
import com.flex.BaseUnitTest
import com.flex.data.local.dao.QuotaRuleDao
import com.flex.data.local.dao.SettingsDao
import com.flex.data.local.entity.QuotaRuleEntity
import com.flex.data.local.entity.SettingsEntity
import com.flex.domain.model.QuotaRule
import com.flex.domain.model.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.YearMonth

/**
 * Unit tests for SettingsRepositoryImpl.
 * Tests repository operations with mocked DAOs (no real database).
 */
class SettingsRepositoryImplTest : BaseUnitTest() {

    @Mock
    private lateinit var settingsDao: SettingsDao

    @Mock
    private lateinit var quotaRuleDao: QuotaRuleDao

    private lateinit var repository: SettingsRepositoryImpl

    @BeforeEach
    override fun setUp() {
        super.setUp()
        repository = SettingsRepositoryImpl(settingsDao, quotaRuleDao)
    }

    // ========== Settings Tests ==========

    @Test
    fun `getSettings returns mapped Settings when entity exists`() = runTest {
        // Given: DAO returns a SettingsEntity
        val entity = SettingsEntity(
            id = 1L,
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 10000,
            officeQuotaPercent = 50,
            officeQuotaMinDays = 10,
            initialFlextimeMinutes = 120,
            initialOvertimeMinutes = 60,
            annualVacationDays = 25,
            carryOverVacationDays = 5,
            specialVacationDays = 3,
            settingsYear = 2025
        )
        whenever(settingsDao.getSettings()).thenReturn(flowOf(entity))

        // When: Getting settings
        val result = repository.getSettings().first()

        // Then: Should return correctly mapped Settings
        assertThat(result.id).isEqualTo(1L)
        assertThat(result.dailyWorkMinutes).isEqualTo(480)
        assertThat(result.monthlyWorkMinutes).isEqualTo(10000)
        assertThat(result.officeQuotaPercent).isEqualTo(50)
        assertThat(result.officeQuotaMinDays).isEqualTo(10)
        assertThat(result.initialFlextimeMinutes).isEqualTo(120)
        assertThat(result.initialOvertimeMinutes).isEqualTo(60)
        assertThat(result.annualVacationDays).isEqualTo(25)
        assertThat(result.carryOverVacationDays).isEqualTo(5)
        assertThat(result.specialVacationDays).isEqualTo(3)
        assertThat(result.settingsYear).isEqualTo(2025)
    }

    @Test
    fun `getSettings returns default Settings when entity is null`() = runTest {
        // Given: DAO returns null (no settings saved yet)
        whenever(settingsDao.getSettings()).thenReturn(flowOf(null))

        // When: Getting settings
        val result = repository.getSettings().first()

        // Then: Should return default Settings
        assertThat(result).isEqualTo(Settings())
    }

    @Test
    fun `saveSettings persists Settings as SettingsEntity`() = runTest {
        // Given: A Settings to save
        val settings = Settings(
            id = 1L,
            dailyWorkMinutes = 450,
            monthlyWorkMinutes = 9500,
            officeQuotaPercent = 60,
            officeQuotaMinDays = 12,
            initialFlextimeMinutes = -30,
            initialOvertimeMinutes = 90,
            annualVacationDays = 28,
            carryOverVacationDays = 8,
            specialVacationDays = 4,
            settingsYear = 2024
        )

        // When: Saving settings
        repository.saveSettings(settings)

        // Then: DAO should be called with correct SettingsEntity
        verify(settingsDao).insert(
            SettingsEntity(
                id = 1L,
                dailyWorkMinutes = 450,
                monthlyWorkMinutes = 9500,
                officeQuotaPercent = 60,
                officeQuotaMinDays = 12,
                initialFlextimeMinutes = -30,
                initialOvertimeMinutes = 90,
                annualVacationDays = 28,
                carryOverVacationDays = 8,
                specialVacationDays = 4,
                settingsYear = 2024
            )
        )
    }

    @Test
    fun `saveSettings correctly maps initialOvertimeMinutes`() = runTest {
        // Given: Settings with specific initialOvertimeMinutes
        val settings = Settings(
            id = 1L,
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = 9252,
            officeQuotaPercent = 40,
            officeQuotaMinDays = 8,
            initialFlextimeMinutes = 0,
            initialOvertimeMinutes = 180, // 3 hours - testing this field
            annualVacationDays = 30,
            carryOverVacationDays = 0,
            specialVacationDays = 5,
            settingsYear = 2025
        )

        // When: Saving settings
        repository.saveSettings(settings)

        // Then: Verify initialOvertimeMinutes is mapped correctly
        val expectedEntity = SettingsEntity(
            id = 1L,
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = 9252,
            officeQuotaPercent = 40,
            officeQuotaMinDays = 8,
            initialFlextimeMinutes = 0,
            initialOvertimeMinutes = 180,
            annualVacationDays = 30,
            carryOverVacationDays = 0,
            specialVacationDays = 5,
            settingsYear = 2025
        )
        verify(settingsDao).insert(expectedEntity)
    }

    @Test
    fun `saveSettings preserves settingsYear correctly`() = runTest {
        // Given: Settings with settingsYear = 2026
        val settings = Settings(
            id = 1L,
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = 9252,
            officeQuotaPercent = 40,
            officeQuotaMinDays = 8,
            initialFlextimeMinutes = 0,
            initialOvertimeMinutes = 0,
            annualVacationDays = 30,
            carryOverVacationDays = 0,
            specialVacationDays = 5,
            settingsYear = 2026  // Testing this field
        )

        // When: Saving settings
        repository.saveSettings(settings)

        // Then: Verify settingsYear is persisted
        val expectedEntity = SettingsEntity(
            id = 1L,
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = 9252,
            officeQuotaPercent = 40,
            officeQuotaMinDays = 8,
            initialFlextimeMinutes = 0,
            initialOvertimeMinutes = 0,
            annualVacationDays = 30,
            carryOverVacationDays = 0,
            specialVacationDays = 5,
            settingsYear = 2026
        )
        verify(settingsDao).insert(expectedEntity)
    }

    // ========== QuotaRule Tests ==========

    @Test
    fun `getQuotaRules returns mapped QuotaRule list`() = runTest {
        // Given: DAO returns QuotaRuleEntities
        val entities = listOf(
            QuotaRuleEntity(
                id = 1L,
                validFrom = "2025-01",
                officeQuotaPercent = 40,
                officeQuotaMinDays = 8
            ),
            QuotaRuleEntity(
                id = 2L,
                validFrom = "2025-06",
                officeQuotaPercent = 50,
                officeQuotaMinDays = 10
            )
        )
        whenever(quotaRuleDao.getAllRules()).thenReturn(flowOf(entities))

        // When: Getting quota rules
        val result = repository.getQuotaRules().first()

        // Then: Should return correctly mapped QuotaRules
        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo(1L)
        assertThat(result[0].validFrom).isEqualTo(YearMonth.of(2025, 1))
        assertThat(result[0].officeQuotaPercent).isEqualTo(40)
        assertThat(result[0].officeQuotaMinDays).isEqualTo(8)

        assertThat(result[1].id).isEqualTo(2L)
        assertThat(result[1].validFrom).isEqualTo(YearMonth.of(2025, 6))
        assertThat(result[1].officeQuotaPercent).isEqualTo(50)
        assertThat(result[1].officeQuotaMinDays).isEqualTo(10)
    }

    @Test
    fun `getQuotaRules returns empty list when no rules exist`() = runTest {
        // Given: DAO returns empty list
        whenever(quotaRuleDao.getAllRules()).thenReturn(flowOf(emptyList()))

        // When: Getting quota rules
        val result = repository.getQuotaRules().first()

        // Then: Should return empty list
        assertThat(result).isEmpty()
    }

    @Test
    fun `saveQuotaRule persists QuotaRule as QuotaRuleEntity and returns ID`() = runTest {
        // Given: A QuotaRule to save
        val rule = QuotaRule(
            id = 0L,
            validFrom = YearMonth.of(2025, 3),
            officeQuotaPercent = 45,
            officeQuotaMinDays = 9
        )
        val expectedId = 5L
        whenever(quotaRuleDao.insert(
            QuotaRuleEntity(
                id = 0L,
                validFrom = "2025-03",
                officeQuotaPercent = 45,
                officeQuotaMinDays = 9
            )
        )).thenReturn(expectedId)

        // When: Saving quota rule
        val resultId = repository.saveQuotaRule(rule)

        // Then: Should return the ID from DAO
        assertThat(resultId).isEqualTo(expectedId)
        verify(quotaRuleDao).insert(
            QuotaRuleEntity(
                id = 0L,
                validFrom = "2025-03",
                officeQuotaPercent = 45,
                officeQuotaMinDays = 9
            )
        )
    }

    @Test
    fun `deleteQuotaRule removes QuotaRule from persistence`() = runTest {
        // Given: A QuotaRule to delete
        val rule = QuotaRule(
            id = 3L,
            validFrom = YearMonth.of(2025, 1),
            officeQuotaPercent = 40,
            officeQuotaMinDays = 8
        )

        // When: Deleting quota rule
        repository.deleteQuotaRule(rule)

        // Then: DAO delete should be called with correct entity
        verify(quotaRuleDao).delete(
            QuotaRuleEntity(
                id = 3L,
                validFrom = "2025-01",
                officeQuotaPercent = 40,
                officeQuotaMinDays = 8
            )
        )
    }

    @Test
    fun `getQuotaRuleForMonth returns most recent rule valid for given month`() {
        // Given: Rules with different validFrom dates
        val rules = listOf(
            QuotaRule(
                id = 1L,
                validFrom = YearMonth.of(2025, 1),
                officeQuotaPercent = 40,
                officeQuotaMinDays = 8
            ),
            QuotaRule(
                id = 2L,
                validFrom = YearMonth.of(2025, 6),
                officeQuotaPercent = 50,
                officeQuotaMinDays = 10
            ),
            QuotaRule(
                id = 3L,
                validFrom = YearMonth.of(2025, 9),
                officeQuotaPercent = 60,
                officeQuotaMinDays = 12
            )
        )

        // When: Getting rule for August 2025 (month between June and September)
        val result = repository.getQuotaRuleForMonth(YearMonth.of(2025, 8), rules)

        // Then: Should return the June rule (most recent valid)
        assertThat(result).isNotNull()
        assertThat(result?.id).isEqualTo(2L)
        assertThat(result?.validFrom).isEqualTo(YearMonth.of(2025, 6))
    }

    @Test
    fun `getQuotaRuleForMonth returns null when no valid rule exists`() {
        // Given: Rules that start after the requested month
        val rules = listOf(
            QuotaRule(
                id = 1L,
                validFrom = YearMonth.of(2025, 6),
                officeQuotaPercent = 50,
                officeQuotaMinDays = 10
            )
        )

        // When: Getting rule for January 2025 (before any rule)
        val result = repository.getQuotaRuleForMonth(YearMonth.of(2025, 1), rules)

        // Then: Should return null
        assertThat(result).isNull()
    }

    @Test
    fun `getQuotaRuleForMonth returns rule when month exactly matches validFrom`() {
        // Given: Rule starting in June
        val rules = listOf(
            QuotaRule(
                id = 1L,
                validFrom = YearMonth.of(2025, 6),
                officeQuotaPercent = 50,
                officeQuotaMinDays = 10
            )
        )

        // When: Getting rule for June 2025 (exact match)
        val result = repository.getQuotaRuleForMonth(YearMonth.of(2025, 6), rules)

        // Then: Should return the rule
        assertThat(result).isNotNull()
        assertThat(result?.id).isEqualTo(1L)
    }
}
