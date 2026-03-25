package com.flex.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.flex.data.local.FlexDatabase
import com.flex.domain.model.QuotaRule
import com.flex.domain.model.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryIntegrationTest {

    private lateinit var database: FlexDatabase
    private lateinit var settingsRepository: SettingsRepositoryImpl

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            FlexDatabase::class.java
        ).build()

        settingsRepository = SettingsRepositoryImpl(
            database.settingsDao(),
            database.quotaRuleDao()
        )
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun saveSettings_getSettings_roundTrip() = runTest {
        // Create settings with all fields
        val settings = Settings(
            id = 1,
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = 9266,
            officeQuotaPercent = 40,
            officeQuotaMinDays = 8,
            initialFlextimeMinutes = 120,
            initialOvertimeMinutes = 60,
            annualVacationDays = 30,
            carryOverVacationDays = 5,
            specialVacationDays = 5,
            settingsYear = 2026
        )

        // Save settings
        settingsRepository.saveSettings(settings)

        // Retrieve settings
        val retrieved = settingsRepository.getSettings().first()

        // Verify all fields
        assertThat(retrieved).isNotNull()
        assertThat(retrieved.id).isEqualTo(1)
        assertThat(retrieved.dailyWorkMinutes).isEqualTo(426)
        assertThat(retrieved.monthlyWorkMinutes).isEqualTo(9266)
        assertThat(retrieved.officeQuotaPercent).isEqualTo(40)
        assertThat(retrieved.officeQuotaMinDays).isEqualTo(8)
        assertThat(retrieved.initialFlextimeMinutes).isEqualTo(120)
        assertThat(retrieved.initialOvertimeMinutes).isEqualTo(60)
        assertThat(retrieved.annualVacationDays).isEqualTo(30)
        assertThat(retrieved.carryOverVacationDays).isEqualTo(5)
        assertThat(retrieved.specialVacationDays).isEqualTo(5)
        assertThat(retrieved.settingsYear).isEqualTo(2026)
    }

    @Test
    fun saveSettings_allFieldsPersisted() = runTest {
        // Test with different values to ensure proper mapping
        val settings = Settings(
            id = 1,
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 10000,
            officeQuotaPercent = 50,
            officeQuotaMinDays = 10,
            initialFlextimeMinutes = -120, // Negative flextime
            initialOvertimeMinutes = 240,
            annualVacationDays = 28,
            carryOverVacationDays = 10,
            specialVacationDays = 3,
            settingsYear = 2025
        )

        settingsRepository.saveSettings(settings)
        val retrieved = settingsRepository.getSettings().first()

        assertThat(retrieved.dailyWorkMinutes).isEqualTo(480)
        assertThat(retrieved.monthlyWorkMinutes).isEqualTo(10000)
        assertThat(retrieved.officeQuotaPercent).isEqualTo(50)
        assertThat(retrieved.officeQuotaMinDays).isEqualTo(10)
        assertThat(retrieved.initialFlextimeMinutes).isEqualTo(-120)
        assertThat(retrieved.initialOvertimeMinutes).isEqualTo(240)
        assertThat(retrieved.annualVacationDays).isEqualTo(28)
        assertThat(retrieved.carryOverVacationDays).isEqualTo(10)
        assertThat(retrieved.specialVacationDays).isEqualTo(3)
        assertThat(retrieved.settingsYear).isEqualTo(2025)
    }

    @Test
    fun saveSettings_initialOvertimeMinutes_persisted() = runTest {
        // Specifically test initialOvertimeMinutes field (added in migration v4 -> v5)
        val settings = Settings(
            id = 1,
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = 9266,
            officeQuotaPercent = 40,
            officeQuotaMinDays = 8,
            initialFlextimeMinutes = 0,
            initialOvertimeMinutes = 180, // 3 hours initial overtime
            annualVacationDays = 30,
            carryOverVacationDays = 0,
            specialVacationDays = 5,
            settingsYear = 2026
        )

        settingsRepository.saveSettings(settings)
        val retrieved = settingsRepository.getSettings().first()

        assertThat(retrieved.initialOvertimeMinutes).isEqualTo(180)
    }

    @Test
    fun saveSettings_update_replacesExisting() = runTest {
        // Save initial settings
        val initial = Settings(
            id = 1,
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = 9266,
            officeQuotaPercent = 40,
            officeQuotaMinDays = 8,
            initialFlextimeMinutes = 100,
            initialOvertimeMinutes = 50,
            annualVacationDays = 30,
            carryOverVacationDays = 0,
            specialVacationDays = 5,
            settingsYear = 2026
        )
        settingsRepository.saveSettings(initial)

        // Update settings
        val updated = initial.copy(
            initialFlextimeMinutes = 200,
            initialOvertimeMinutes = 100,
            officeQuotaPercent = 50
        )
        settingsRepository.saveSettings(updated)

        // Retrieve and verify updated values
        val retrieved = settingsRepository.getSettings().first()
        assertThat(retrieved.initialFlextimeMinutes).isEqualTo(200)
        assertThat(retrieved.initialOvertimeMinutes).isEqualTo(100)
        assertThat(retrieved.officeQuotaPercent).isEqualTo(50)
    }

    @Test
    fun getSettings_noData_returnsDefault() = runTest {
        // No settings saved, should return default Settings()
        val retrieved = settingsRepository.getSettings().first()

        assertThat(retrieved).isNotNull()
        assertThat(retrieved.id).isEqualTo(1)
        assertThat(retrieved.dailyWorkMinutes).isEqualTo(426)
        assertThat(retrieved.initialFlextimeMinutes).isEqualTo(0)
        assertThat(retrieved.initialOvertimeMinutes).isEqualTo(0)
    }

    @Test
    fun quotaRule_saveGetDelete() = runTest {
        // Create quota rule
        val rule = QuotaRule(
            id = 0,
            validFrom = YearMonth.of(2026, 1),
            officeQuotaPercent = 50,
            officeQuotaMinDays = 10
        )

        // Save and get ID
        val ruleId = settingsRepository.saveQuotaRule(rule)
        assertThat(ruleId).isGreaterThan(0)

        // Retrieve all rules
        val rules = settingsRepository.getQuotaRules().first()
        assertThat(rules).hasSize(1)
        assertThat(rules[0].validFrom).isEqualTo(YearMonth.of(2026, 1))
        assertThat(rules[0].officeQuotaPercent).isEqualTo(50)
        assertThat(rules[0].officeQuotaMinDays).isEqualTo(10)

        // Delete rule
        settingsRepository.deleteQuotaRule(rules[0])

        // Verify deleted
        val afterDelete = settingsRepository.getQuotaRules().first()
        assertThat(afterDelete).isEmpty()
    }

    @Test
    fun quotaRule_multipleRules_orderedByDate() = runTest {
        // Insert rules in random order
        val rule1 = QuotaRule(id = 0, validFrom = YearMonth.of(2026, 3), officeQuotaPercent = 50, officeQuotaMinDays = 10)
        val rule2 = QuotaRule(id = 0, validFrom = YearMonth.of(2026, 1), officeQuotaPercent = 40, officeQuotaMinDays = 8)
        val rule3 = QuotaRule(id = 0, validFrom = YearMonth.of(2026, 6), officeQuotaPercent = 60, officeQuotaMinDays = 12)

        settingsRepository.saveQuotaRule(rule1)
        settingsRepository.saveQuotaRule(rule2)
        settingsRepository.saveQuotaRule(rule3)

        val rules = settingsRepository.getQuotaRules().first()
        assertThat(rules).hasSize(3)
    }

    @Test
    fun getQuotaRuleForMonth_returnsCorrectRule() = runTest {
        // Insert rules
        val rule1 = QuotaRule(id = 0, validFrom = YearMonth.of(2026, 1), officeQuotaPercent = 40, officeQuotaMinDays = 8)
        val rule2 = QuotaRule(id = 0, validFrom = YearMonth.of(2026, 6), officeQuotaPercent = 50, officeQuotaMinDays = 10)
        val rule3 = QuotaRule(id = 0, validFrom = YearMonth.of(2026, 9), officeQuotaPercent = 60, officeQuotaMinDays = 12)

        settingsRepository.saveQuotaRule(rule1)
        settingsRepository.saveQuotaRule(rule2)
        settingsRepository.saveQuotaRule(rule3)

        val allRules = settingsRepository.getQuotaRules().first()

        // Test different months
        val ruleForFeb = settingsRepository.getQuotaRuleForMonth(YearMonth.of(2026, 2), allRules)
        assertThat(ruleForFeb).isNotNull()
        assertThat(ruleForFeb?.officeQuotaPercent).isEqualTo(40) // Uses rule1

        val ruleForJul = settingsRepository.getQuotaRuleForMonth(YearMonth.of(2026, 7), allRules)
        assertThat(ruleForJul).isNotNull()
        assertThat(ruleForJul?.officeQuotaPercent).isEqualTo(50) // Uses rule2

        val ruleForDec = settingsRepository.getQuotaRuleForMonth(YearMonth.of(2026, 12), allRules)
        assertThat(ruleForDec).isNotNull()
        assertThat(ruleForDec?.officeQuotaPercent).isEqualTo(60) // Uses rule3

        // Before first rule
        val ruleForDec2025 = settingsRepository.getQuotaRuleForMonth(YearMonth.of(2025, 12), allRules)
        assertThat(ruleForDec2025).isNull()
    }

    @Test
    fun getQuotaRuleForMonth_exactMatch() = runTest {
        val rule = QuotaRule(id = 0, validFrom = YearMonth.of(2026, 5), officeQuotaPercent = 45, officeQuotaMinDays = 9)
        settingsRepository.saveQuotaRule(rule)

        val allRules = settingsRepository.getQuotaRules().first()

        // Exact match
        val ruleForMay = settingsRepository.getQuotaRuleForMonth(YearMonth.of(2026, 5), allRules)
        assertThat(ruleForMay).isNotNull()
        assertThat(ruleForMay?.officeQuotaPercent).isEqualTo(45)
    }

    @Test
    fun quotaRule_persistence() = runTest {
        // Save rule
        val rule = QuotaRule(
            id = 0,
            validFrom = YearMonth.of(2026, 3),
            officeQuotaPercent = 55,
            officeQuotaMinDays = 11
        )
        val ruleId = settingsRepository.saveQuotaRule(rule)

        // Retrieve and verify persistence
        val rules = settingsRepository.getQuotaRules().first()
        assertThat(rules).hasSize(1)

        val retrieved = rules[0]
        assertThat(retrieved.id).isEqualTo(ruleId)
        assertThat(retrieved.validFrom).isEqualTo(YearMonth.of(2026, 3))
        assertThat(retrieved.officeQuotaPercent).isEqualTo(55)
        assertThat(retrieved.officeQuotaMinDays).isEqualTo(11)
    }

    @Test
    fun settingsAndQuotaRules_independentPersistence() = runTest {
        // Save settings
        val settings = Settings(
            id = 1,
            dailyWorkMinutes = 450,
            monthlyWorkMinutes = 9500,
            officeQuotaPercent = 45,
            officeQuotaMinDays = 9,
            initialFlextimeMinutes = 150,
            initialOvertimeMinutes = 75,
            annualVacationDays = 28,
            carryOverVacationDays = 3,
            specialVacationDays = 4,
            settingsYear = 2026
        )
        settingsRepository.saveSettings(settings)

        // Save quota rules
        val rule1 = QuotaRule(id = 0, validFrom = YearMonth.of(2026, 1), officeQuotaPercent = 40, officeQuotaMinDays = 8)
        val rule2 = QuotaRule(id = 0, validFrom = YearMonth.of(2026, 7), officeQuotaPercent = 50, officeQuotaMinDays = 10)
        settingsRepository.saveQuotaRule(rule1)
        settingsRepository.saveQuotaRule(rule2)

        // Verify both are persisted independently
        val retrievedSettings = settingsRepository.getSettings().first()
        val retrievedRules = settingsRepository.getQuotaRules().first()

        assertThat(retrievedSettings.dailyWorkMinutes).isEqualTo(450)
        assertThat(retrievedRules).hasSize(2)
    }
}
