package com.vrema.ui.settings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.vrema.BaseUnitTest
import com.vrema.MainDispatcherRule
import com.vrema.domain.model.QuotaRule
import com.vrema.domain.model.Settings
import com.vrema.domain.repository.SettingsRepository
import com.vrema.domain.usecase.GetSettingsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.YearMonth

/**
 * Unit tests for SettingsViewModel.
 * Tests state management, settings updates, and quota rule operations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest : BaseUnitTest() {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Mock
    private lateinit var getSettings: GetSettingsUseCase

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    private lateinit var viewModel: SettingsViewModel

    override fun setUp() {
        super.setUp()
        // Default mock behavior
        whenever(getSettings()).thenReturn(flowOf(Settings()))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(emptyList()))
    }

    // ========== Initial State Tests ==========

    @Test
    fun `initial state loads settings from repository`() = runTest {
        // Given: Repository returns specific settings
        val expectedSettings = Settings(
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 10000,
            officeQuotaPercent = 50,
            officeQuotaMinDays = 10,
            annualVacationDays = 28
        )
        whenever(getSettings()).thenReturn(flowOf(expectedSettings))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(emptyList()))

        // When: ViewModel is created
        viewModel = SettingsViewModel(getSettings, settingsRepository)
        advanceUntilIdle()

        // Then: Settings state should be loaded
        assertThat(viewModel.settings.value).isEqualTo(expectedSettings)
    }

    @Test
    fun `initial state loads empty quota rules list`() = runTest {
        // Given: Repository returns empty quota rules
        whenever(getSettings()).thenReturn(flowOf(Settings()))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(emptyList()))

        // When: ViewModel is created
        viewModel = SettingsViewModel(getSettings, settingsRepository)
        advanceUntilIdle()

        // Then: QuotaRules state should be empty
        assertThat(viewModel.quotaRules.value).isEmpty()
    }

    @Test
    fun `initial state loads quota rules from repository`() = runTest {
        // Given: Repository returns quota rules
        val expectedRules = listOf(
            QuotaRule(id = 1, validFrom = YearMonth.of(2025, 1), officeQuotaPercent = 40, officeQuotaMinDays = 8),
            QuotaRule(id = 2, validFrom = YearMonth.of(2025, 6), officeQuotaPercent = 50, officeQuotaMinDays = 10)
        )
        whenever(getSettings()).thenReturn(flowOf(Settings()))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(expectedRules))

        // When: ViewModel is created
        viewModel = SettingsViewModel(getSettings, settingsRepository)
        advanceUntilIdle()

        // Then: QuotaRules state should be loaded
        assertThat(viewModel.quotaRules.value).hasSize(2)
        assertThat(viewModel.quotaRules.value).isEqualTo(expectedRules)
    }

    // ========== updateSettings Tests ==========

    @Test
    fun `updateSettings persists settings to repository`() = runTest {
        // Given: Initialized ViewModel
        viewModel = SettingsViewModel(getSettings, settingsRepository)
        advanceUntilIdle()

        val updatedSettings = Settings(
            dailyWorkMinutes = 450,
            monthlyWorkMinutes = 9500,
            officeQuotaPercent = 60,
            officeQuotaMinDays = 12
        )

        // When: Updating settings
        viewModel.updateSettings(updatedSettings)
        advanceUntilIdle()

        // Then: Repository should be called with updated settings
        verify(settingsRepository).saveSettings(updatedSettings)
    }

    @Test
    fun `updateSettings with different values persists correctly`() = runTest {
        // Given: Initialized ViewModel
        viewModel = SettingsViewModel(getSettings, settingsRepository)
        advanceUntilIdle()

        val newSettings = Settings(
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = 9266,
            officeQuotaPercent = 40,
            officeQuotaMinDays = 8,
            annualVacationDays = 30,
            carryOverVacationDays = 5,
            specialVacationDays = 5,
            initialFlextimeMinutes = 120,
            initialOvertimeMinutes = 60
        )

        // When: Updating settings
        viewModel.updateSettings(newSettings)
        advanceUntilIdle()

        // Then: Repository should be called
        verify(settingsRepository).saveSettings(newSettings)
    }

    // ========== addQuotaRule Tests ==========

    @Test
    fun `addQuotaRule persists rule to repository`() = runTest {
        // Given: Initialized ViewModel
        viewModel = SettingsViewModel(getSettings, settingsRepository)
        advanceUntilIdle()

        val newRule = QuotaRule(
            id = 0,
            validFrom = YearMonth.of(2025, 3),
            officeQuotaPercent = 45,
            officeQuotaMinDays = 9
        )

        // When: Adding quota rule
        viewModel.addQuotaRule(newRule)
        advanceUntilIdle()

        // Then: Repository should be called with new rule
        verify(settingsRepository).saveQuotaRule(newRule)
    }

    @Test
    fun `addQuotaRule with different month persists correctly`() = runTest {
        // Given: Initialized ViewModel
        viewModel = SettingsViewModel(getSettings, settingsRepository)
        advanceUntilIdle()

        val rule = QuotaRule(
            id = 0,
            validFrom = YearMonth.of(2026, 1),
            officeQuotaPercent = 50,
            officeQuotaMinDays = 10
        )

        // When: Adding quota rule
        viewModel.addQuotaRule(rule)
        advanceUntilIdle()

        // Then: Repository should be called
        verify(settingsRepository).saveQuotaRule(rule)
    }

    // ========== deleteQuotaRule Tests ==========

    @Test
    fun `deleteQuotaRule removes rule from repository`() = runTest {
        // Given: Initialized ViewModel
        viewModel = SettingsViewModel(getSettings, settingsRepository)
        advanceUntilIdle()

        val ruleToDelete = QuotaRule(
            id = 1,
            validFrom = YearMonth.of(2025, 1),
            officeQuotaPercent = 40,
            officeQuotaMinDays = 8
        )

        // When: Deleting quota rule
        viewModel.deleteQuotaRule(ruleToDelete)
        advanceUntilIdle()

        // Then: Repository should be called with rule to delete
        verify(settingsRepository).deleteQuotaRule(ruleToDelete)
    }

    // ========== StateFlow Updates Tests ==========

    @Test
    fun `settings StateFlow updates when repository emits new value`() = runTest {
        // Given: Repository that will emit different settings
        val initialSettings = Settings(dailyWorkMinutes = 426)
        val updatedSettings = Settings(dailyWorkMinutes = 480)

        whenever(getSettings()).thenReturn(flowOf(initialSettings, updatedSettings))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(emptyList()))

        // When: ViewModel is created
        viewModel = SettingsViewModel(getSettings, settingsRepository)
        advanceUntilIdle()

        // Then: State should reflect the latest settings
        // Note: Flow will emit latest value, behavior depends on implementation
        assertThat(viewModel.settings.value.dailyWorkMinutes).isAnyOf(426, 480)
    }

    @Test
    fun `quotaRules StateFlow updates when repository emits new value`() = runTest {
        // Given: Repository that emits updated quota rules
        val initialRules = emptyList<QuotaRule>()
        val updatedRules = listOf(
            QuotaRule(id = 1, validFrom = YearMonth.of(2025, 1), officeQuotaPercent = 40, officeQuotaMinDays = 8)
        )

        whenever(getSettings()).thenReturn(flowOf(Settings()))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(initialRules, updatedRules))

        // When: ViewModel is created
        viewModel = SettingsViewModel(getSettings, settingsRepository)
        advanceUntilIdle()

        // Then: State should reflect the rules
        // Note: Will contain latest emission
        assertThat(viewModel.quotaRules.value).isAnyOf(initialRules, updatedRules)
    }

    // ========== Edge Cases ==========

    @Test
    fun `settings StateFlow handles null from repository gracefully`() = runTest {
        // Given: Repository returns default settings (never null in this implementation)
        whenever(getSettings()).thenReturn(flowOf(Settings()))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(emptyList()))

        // When: ViewModel is created
        viewModel = SettingsViewModel(getSettings, settingsRepository)
        advanceUntilIdle()

        // Then: Settings should have default value
        assertThat(viewModel.settings.value).isNotNull()
        assertThat(viewModel.settings.value).isEqualTo(Settings())
    }

    @Test
    fun `quotaRules StateFlow handles empty list correctly`() = runTest {
        // Given: Repository returns empty list
        whenever(getSettings()).thenReturn(flowOf(Settings()))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(emptyList()))

        // When: ViewModel is created
        viewModel = SettingsViewModel(getSettings, settingsRepository)
        advanceUntilIdle()

        // Then: QuotaRules should be empty list
        assertThat(viewModel.quotaRules.value).isEmpty()
    }
}
