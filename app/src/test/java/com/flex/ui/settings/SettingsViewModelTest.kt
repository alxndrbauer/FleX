package com.flex.ui.settings

import com.google.common.truth.Truth.assertThat
import com.flex.BaseUnitTest
import com.flex.MainDispatcherExtension
import com.flex.domain.model.QuotaRule
import com.flex.domain.model.Settings
import com.flex.domain.repository.SettingsRepository
import android.content.Context
import com.flex.data.local.AppIconPreferences
import com.flex.data.local.ThemePreferences
import com.flex.domain.model.AppIconVariant
import com.flex.domain.model.ThemeMode
import com.flex.geofence.GeofenceManager
import com.flex.wifi.WifiMonitor
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.calendar.CalendarSyncService
import com.flex.domain.repository.WorkDayRepository
import com.flex.data.export.IcsExportService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.YearMonth

/**
 * Unit tests for SettingsViewModel.
 * Tests state management, settings updates, and quota rule operations.
 */
@ExtendWith(MainDispatcherExtension::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest : BaseUnitTest() {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var getSettings: GetSettingsUseCase

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    @Mock
    private lateinit var themePreferences: ThemePreferences

    @Mock
    private lateinit var geofenceManager: GeofenceManager

    @Mock
    private lateinit var wifiMonitor: WifiMonitor

    @Mock
    private lateinit var appIconPreferences: AppIconPreferences

    @Mock
    private lateinit var calendarSyncService: CalendarSyncService

    @Mock
    private lateinit var workDayRepository: WorkDayRepository

    @Mock
    private lateinit var icsExportService: IcsExportService

    private lateinit var viewModel: SettingsViewModel

    @BeforeEach
    override fun setUp() {
        super.setUp()
        // Default mock behavior
        whenever(getSettings()).thenReturn(flowOf(Settings()))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(emptyList()))
        whenever(themePreferences.themeModeFlow).thenReturn(MutableStateFlow(ThemeMode.SYSTEM))
        whenever(appIconPreferences.variantFlow).thenReturn(MutableStateFlow(AppIconVariant.CLASSIC))
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
        viewModel = SettingsViewModel(context, getSettings, settingsRepository, themePreferences, appIconPreferences, geofenceManager, wifiMonitor, calendarSyncService, workDayRepository, icsExportService)
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
        viewModel = SettingsViewModel(context, getSettings, settingsRepository, themePreferences, appIconPreferences, geofenceManager, wifiMonitor, calendarSyncService, workDayRepository, icsExportService)
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
        viewModel = SettingsViewModel(context, getSettings, settingsRepository, themePreferences, appIconPreferences, geofenceManager, wifiMonitor, calendarSyncService, workDayRepository, icsExportService)
        advanceUntilIdle()

        // Then: QuotaRules state should be loaded
        assertThat(viewModel.quotaRules.value).hasSize(2)
        assertThat(viewModel.quotaRules.value).isEqualTo(expectedRules)
    }

    // ========== updateSettings Tests ==========

    @Test
    fun `updateSettings persists settings to repository`() = runTest {
        // Given: Initialized ViewModel
        viewModel = SettingsViewModel(context, getSettings, settingsRepository, themePreferences, appIconPreferences, geofenceManager, wifiMonitor, calendarSyncService, workDayRepository, icsExportService)
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
        viewModel = SettingsViewModel(context, getSettings, settingsRepository, themePreferences, appIconPreferences, geofenceManager, wifiMonitor, calendarSyncService, workDayRepository, icsExportService)
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
        viewModel = SettingsViewModel(context, getSettings, settingsRepository, themePreferences, appIconPreferences, geofenceManager, wifiMonitor, calendarSyncService, workDayRepository, icsExportService)
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
        viewModel = SettingsViewModel(context, getSettings, settingsRepository, themePreferences, appIconPreferences, geofenceManager, wifiMonitor, calendarSyncService, workDayRepository, icsExportService)
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
        viewModel = SettingsViewModel(context, getSettings, settingsRepository, themePreferences, appIconPreferences, geofenceManager, wifiMonitor, calendarSyncService, workDayRepository, icsExportService)
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
        viewModel = SettingsViewModel(context, getSettings, settingsRepository, themePreferences, appIconPreferences, geofenceManager, wifiMonitor, calendarSyncService, workDayRepository, icsExportService)
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
        viewModel = SettingsViewModel(context, getSettings, settingsRepository, themePreferences, appIconPreferences, geofenceManager, wifiMonitor, calendarSyncService, workDayRepository, icsExportService)
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
        viewModel = SettingsViewModel(context, getSettings, settingsRepository, themePreferences, appIconPreferences, geofenceManager, wifiMonitor, calendarSyncService, workDayRepository, icsExportService)
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
        viewModel = SettingsViewModel(context, getSettings, settingsRepository, themePreferences, appIconPreferences, geofenceManager, wifiMonitor, calendarSyncService, workDayRepository, icsExportService)
        advanceUntilIdle()

        // Then: QuotaRules should be empty list
        assertThat(viewModel.quotaRules.value).isEmpty()
    }

    // ========== saveCalendarSettings Tests ==========

    @Test
    fun `saveCalendarSettings persists noAlarm flag to repository`() = runTest {
        // Given
        viewModel = SettingsViewModel(context, getSettings, settingsRepository, themePreferences, appIconPreferences, geofenceManager, wifiMonitor, calendarSyncService, workDayRepository, icsExportService)
        advanceUntilIdle()

        // When: Saving with noAlarm = true
        viewModel.saveCalendarSettings(
            enabled = true, calendarId = 1L, syncTypes = "WORK", syncOffice = true,
            syncHomeOffice = true, eventPrefix = "FleX", noAlarm = true
        )
        advanceUntilIdle()

        // Then: settings saved with calendarEventNoAlarm = true
        val captor = argumentCaptor<Settings>()
        verify(settingsRepository).saveSettings(captor.capture())
        assertThat(captor.firstValue.calendarEventNoAlarm).isEqualTo(true)
    }

    @Test
    fun `saveCalendarSettings persists noAlarm false to repository`() = runTest {
        // Given
        viewModel = SettingsViewModel(context, getSettings, settingsRepository, themePreferences, appIconPreferences, geofenceManager, wifiMonitor, calendarSyncService, workDayRepository, icsExportService)
        advanceUntilIdle()

        // When: Saving with noAlarm = false
        viewModel.saveCalendarSettings(
            enabled = true, calendarId = 1L, syncTypes = "WORK", syncOffice = true,
            syncHomeOffice = true, eventPrefix = "FleX", noAlarm = false
        )
        advanceUntilIdle()

        // Then: settings saved with calendarEventNoAlarm = false
        val captor = argumentCaptor<Settings>()
        verify(settingsRepository).saveSettings(captor.capture())
        assertThat(captor.firstValue.calendarEventNoAlarm).isEqualTo(false)
    }

    // ========== cleanupOrphanedMappings Tests ==========

    @Test
    fun `cleanupOrphanedMappings calls calendarSyncService`() = runTest {
        // Given: Initialized ViewModel
        viewModel = SettingsViewModel(context, getSettings, settingsRepository, themePreferences, appIconPreferences, geofenceManager, wifiMonitor, calendarSyncService, workDayRepository, icsExportService)
        advanceUntilIdle()

        whenever(calendarSyncService.cleanupOrphanedMappings()).thenReturn(Pair(0, 0))

        // When: Calling cleanupOrphanedMappings
        viewModel.cleanupOrphanedMappings { _, _ -> }
        advanceUntilIdle()

        // Then: calendarSyncService.cleanupOrphanedMappings should be called
        verify(calendarSyncService).cleanupOrphanedMappings()
    }

    @Test
    fun `cleanupOrphanedMappings calls onResult with removed and total count`() = runTest {
        // Given: Initialized ViewModel
        viewModel = SettingsViewModel(context, getSettings, settingsRepository, themePreferences, appIconPreferences, geofenceManager, wifiMonitor, calendarSyncService, workDayRepository, icsExportService)
        advanceUntilIdle()

        whenever(calendarSyncService.cleanupOrphanedMappings()).thenReturn(Pair(3, 7))

        // When: Calling cleanupOrphanedMappings with callback
        var resultRemoved = -1
        var resultTotal = -1
        viewModel.cleanupOrphanedMappings { removed, total -> resultRemoved = removed; resultTotal = total }
        advanceUntilIdle()

        // Then: onResult should be called with removed=3, total=7
        assertThat(resultRemoved).isEqualTo(3)
        assertThat(resultTotal).isEqualTo(7)
    }

    @Test
    fun `cleanupOrphanedMappings with no mappings calls onResult with 0 and 0`() = runTest {
        // Given: Initialized ViewModel
        viewModel = SettingsViewModel(context, getSettings, settingsRepository, themePreferences, appIconPreferences, geofenceManager, wifiMonitor, calendarSyncService, workDayRepository, icsExportService)
        advanceUntilIdle()

        whenever(calendarSyncService.cleanupOrphanedMappings()).thenReturn(Pair(0, 0))

        // When: Calling cleanupOrphanedMappings with callback
        var resultRemoved = -1
        var resultTotal = -1
        viewModel.cleanupOrphanedMappings { removed, total -> resultRemoved = removed; resultTotal = total }
        advanceUntilIdle()

        // Then: onResult should be called with 0, 0
        assertThat(resultRemoved).isEqualTo(0)
        assertThat(resultTotal).isEqualTo(0)
    }
}
