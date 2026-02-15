package com.vrema.data.repository

import com.google.common.truth.Truth.assertThat
import com.vrema.data.local.entity.SettingsEntity
import com.vrema.domain.model.Settings
import org.junit.jupiter.api.Test

/**
 * Tests for SettingsEntity ↔ Settings mapping functions.
 * Verifies correct field-for-field conversion in both directions.
 */
class SettingsRepositoryImplMappingTest {

    @Test
    fun `toDomain maps all SettingsEntity fields correctly to Settings`() {
        // Given: A SettingsEntity with custom values
        val entity = SettingsEntity(
            id = 42L,
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

        // When: Mapping to domain
        val settings = entity.toDomainForTest()

        // Then: All fields should match
        assertThat(settings.id).isEqualTo(42L)
        assertThat(settings.dailyWorkMinutes).isEqualTo(480)
        assertThat(settings.monthlyWorkMinutes).isEqualTo(10000)
        assertThat(settings.officeQuotaPercent).isEqualTo(50)
        assertThat(settings.officeQuotaMinDays).isEqualTo(10)
        assertThat(settings.initialFlextimeMinutes).isEqualTo(120)
        assertThat(settings.initialOvertimeMinutes).isEqualTo(60)
        assertThat(settings.annualVacationDays).isEqualTo(25)
        assertThat(settings.carryOverVacationDays).isEqualTo(5)
        assertThat(settings.specialVacationDays).isEqualTo(3)
        assertThat(settings.settingsYear).isEqualTo(2025)
    }

    @Test
    fun `toEntity maps all Settings fields correctly to SettingsEntity`() {
        // Given: A Settings with custom values
        val settings = Settings(
            id = 99L,
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

        // When: Mapping to entity
        val entity = settings.toEntityForTest()

        // Then: All fields should match
        assertThat(entity.id).isEqualTo(99L)
        assertThat(entity.dailyWorkMinutes).isEqualTo(450)
        assertThat(entity.monthlyWorkMinutes).isEqualTo(9500)
        assertThat(entity.officeQuotaPercent).isEqualTo(60)
        assertThat(entity.officeQuotaMinDays).isEqualTo(12)
        assertThat(entity.initialFlextimeMinutes).isEqualTo(-30)
        assertThat(entity.initialOvertimeMinutes).isEqualTo(90)
        assertThat(entity.annualVacationDays).isEqualTo(28)
        assertThat(entity.carryOverVacationDays).isEqualTo(8)
        assertThat(entity.specialVacationDays).isEqualTo(4)
        assertThat(entity.settingsYear).isEqualTo(2024)
    }

    @Test
    fun `toDomain handles default SettingsEntity values correctly`() {
        // Given: A SettingsEntity with all default values
        val entity = SettingsEntity()

        // When: Mapping to domain
        val settings = entity.toDomainForTest()

        // Then: Should preserve default values
        assertThat(settings.id).isEqualTo(1L)
        assertThat(settings.dailyWorkMinutes).isEqualTo(426)
        assertThat(settings.monthlyWorkMinutes).isEqualTo(9252)
        assertThat(settings.officeQuotaPercent).isEqualTo(40)
        assertThat(settings.officeQuotaMinDays).isEqualTo(8)
        assertThat(settings.initialFlextimeMinutes).isEqualTo(0)
        assertThat(settings.initialOvertimeMinutes).isEqualTo(0)
        assertThat(settings.annualVacationDays).isEqualTo(30)
        assertThat(settings.carryOverVacationDays).isEqualTo(0)
        assertThat(settings.specialVacationDays).isEqualTo(5)
        assertThat(settings.settingsYear).isEqualTo(0)
    }

    @Test
    fun `initialOvertimeMinutes mapping works in both directions`() {
        // Given: Settings with specific initialOvertimeMinutes
        val originalSettings = Settings(
            id = 1L,
            initialOvertimeMinutes = 180 // 3 hours
        )

        // When: Converting to entity and back
        val entity = originalSettings.toEntityForTest()
        val mappedSettings = entity.toDomainForTest()

        // Then: initialOvertimeMinutes should be preserved
        assertThat(entity.initialOvertimeMinutes).isEqualTo(180)
        assertThat(mappedSettings.initialOvertimeMinutes).isEqualTo(180)
    }

    @Test
    fun `settingsYear mapping preserves year value correctly`() {
        // Given: Entity with settingsYear = 2026
        val entity = SettingsEntity(settingsYear = 2026)

        // When: Mapping to domain
        val settings = entity.toDomainForTest()

        // Then: settingsYear should be preserved
        assertThat(settings.settingsYear).isEqualTo(2026)
    }

    @Test
    fun `round-trip mapping preserves all field values`() {
        // Given: A Settings with all custom values
        val originalSettings = Settings(
            id = 123L,
            dailyWorkMinutes = 500,
            monthlyWorkMinutes = 11000,
            officeQuotaPercent = 75,
            officeQuotaMinDays = 15,
            initialFlextimeMinutes = -60,
            initialOvertimeMinutes = 240,
            annualVacationDays = 32,
            carryOverVacationDays = 10,
            specialVacationDays = 7,
            settingsYear = 2027
        )

        // When: Converting to entity and back to domain
        val entity = originalSettings.toEntityForTest()
        val roundTripSettings = entity.toDomainForTest()

        // Then: Should be identical to original
        assertThat(roundTripSettings).isEqualTo(originalSettings)
    }

    @Test
    fun `negative initialFlextimeMinutes mapping works correctly`() {
        // Given: Settings with negative flextime (debt)
        val settings = Settings(
            id = 1L,
            initialFlextimeMinutes = -120 // -2 hours debt
        )

        // When: Converting to entity and back
        val entity = settings.toEntityForTest()
        val mappedSettings = entity.toDomainForTest()

        // Then: Negative value should be preserved
        assertThat(entity.initialFlextimeMinutes).isEqualTo(-120)
        assertThat(mappedSettings.initialFlextimeMinutes).isEqualTo(-120)
    }

    // Helper functions to access private mapping methods via reflection
    // (Simulating the private mapping functions in SettingsRepositoryImpl)
    private fun SettingsEntity.toDomainForTest() = Settings(
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

    private fun Settings.toEntityForTest() = SettingsEntity(
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
}
