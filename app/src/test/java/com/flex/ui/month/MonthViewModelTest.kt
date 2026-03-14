package com.flex.ui.month

import com.google.common.truth.Truth.assertThat
import com.flex.BaseUnitTest
import com.flex.MainDispatcherExtension
import com.flex.domain.model.DayType
import com.flex.domain.model.FlextimeBalance
import com.flex.domain.model.QuotaStatus
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.DayWorkTimeResult
import com.flex.domain.usecase.GetMonthWorkDaysUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.domain.events.DataChangeEventBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * Unit tests for MonthViewModel.
 * Tests month navigation, day editing, saving/deleting work days, and calculations.
 */
@ExtendWith(MainDispatcherExtension::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MonthViewModelTest : BaseUnitTest() {

    @Mock
    private lateinit var getMonthWorkDays: GetMonthWorkDaysUseCase

    @Mock
    private lateinit var getSettings: GetSettingsUseCase

    @Mock
    private lateinit var workDayRepository: WorkDayRepository

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    @Mock
    private lateinit var calculateDayWorkTime: CalculateDayWorkTimeUseCase

    @Mock
    private lateinit var calculateQuota: CalculateQuotaUseCase

    @Mock
    private lateinit var calculateFlextime: CalculateFlextimeUseCase

    @Mock
    private lateinit var dataChangeEventBus: DataChangeEventBus

    private lateinit var viewModel: MonthViewModel

    @BeforeEach
    override fun setUp() {
        super.setUp()
        // Default mock behavior
        whenever(getMonthWorkDays(any())).thenReturn(flowOf(emptyList()))
        whenever(getSettings()).thenReturn(flowOf(Settings()))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(emptyList()))
        whenever(workDayRepository.getWorkDaysForYear(any())).thenReturn(flowOf(emptyList()))
        whenever(calculateQuota(any(), any(), any(), any(), any())).thenReturn(QuotaStatus())
        whenever(calculateFlextime(any(), any(), any())).thenReturn(FlextimeBalance())
        whenever(calculateDayWorkTime(any())).thenReturn(DayWorkTimeResult(0, 0, 0, false))
    }

    // ========== Initial State Tests ==========

    @Test
    fun `initial state uses current YearMonth`() = runTest {
        // When: ViewModel is created
        viewModel = MonthViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateDayWorkTime, calculateQuota, calculateFlextime, dataChangeEventBus
        )
        advanceUntilIdle()

        // Then: State should have current YearMonth
        assertThat(viewModel.uiState.value.yearMonth).isEqualTo(YearMonth.now())
    }

    @Test
    fun `initial state has empty workDays list`() = runTest {
        // Given: Repository returns empty list
        whenever(getMonthWorkDays(any())).thenReturn(flowOf(emptyList()))

        // When: ViewModel is created
        viewModel = MonthViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateDayWorkTime, calculateQuota, calculateFlextime, dataChangeEventBus
        )
        advanceUntilIdle()

        // Then: State should have empty workDays
        assertThat(viewModel.uiState.value.workDays).isEmpty()
    }

    @Test
    fun `initial state loads workDays from repository`() = runTest {
        // Given: Repository returns work days
        val today = LocalDate.now()
        val workDays = listOf(
            WorkDay(id = 1, date = today, location = WorkLocation.OFFICE, dayType = DayType.WORK)
        )
        whenever(getMonthWorkDays(any())).thenReturn(flowOf(workDays))

        // When: ViewModel is created
        viewModel = MonthViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateDayWorkTime, calculateQuota, calculateFlextime, dataChangeEventBus
        )
        advanceUntilIdle()

        // Then: State should have work days
        assertThat(viewModel.uiState.value.workDays).hasSize(1)
        assertThat(viewModel.uiState.value.workDays[0].id).isEqualTo(1)
    }

    // ========== Navigation Tests ==========

    @Test
    fun `previousMonth updates yearMonth to previous month`() = runTest {
        // Given: ViewModel initialized
        viewModel = MonthViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateDayWorkTime, calculateQuota, calculateFlextime, dataChangeEventBus
        )
        advanceUntilIdle()

        val initialMonth = viewModel.uiState.value.yearMonth

        // When: Navigate to previous month
        viewModel.previousMonth()
        advanceUntilIdle()

        // Then: YearMonth should be one month earlier
        assertThat(viewModel.uiState.value.yearMonth).isEqualTo(initialMonth.minusMonths(1))
    }

    @Test
    fun `nextMonth updates yearMonth to next month`() = runTest {
        // Given: ViewModel initialized
        viewModel = MonthViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateDayWorkTime, calculateQuota, calculateFlextime, dataChangeEventBus
        )
        advanceUntilIdle()

        val initialMonth = viewModel.uiState.value.yearMonth

        // When: Navigate to next month
        viewModel.nextMonth()
        advanceUntilIdle()

        // Then: YearMonth should be one month later
        assertThat(viewModel.uiState.value.yearMonth).isEqualTo(initialMonth.plusMonths(1))
    }

    @Test
    fun `previousMonth then nextMonth returns to original month`() = runTest {
        // Given: ViewModel initialized
        viewModel = MonthViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateDayWorkTime, calculateQuota, calculateFlextime, dataChangeEventBus
        )
        advanceUntilIdle()

        val originalMonth = viewModel.uiState.value.yearMonth

        // When: Navigate backward then forward
        viewModel.previousMonth()
        advanceUntilIdle()
        viewModel.nextMonth()
        advanceUntilIdle()

        // Then: Should be back to original month
        assertThat(viewModel.uiState.value.yearMonth).isEqualTo(originalMonth)
    }

    // ========== selectDay Tests ==========

    @Test
    fun `selectDay sets editingDay when day exists`() = runTest {
        // Given: ViewModel with existing work day
        val today = LocalDate.now()
        val workDay = WorkDay(
            id = 1,
            date = today,
            location = WorkLocation.OFFICE,
            timeBlocks = listOf(TimeBlock(workDayId = 1, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(17, 0)))
        )
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = MonthViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateDayWorkTime, calculateQuota, calculateFlextime, dataChangeEventBus
        )
        advanceUntilIdle()

        // When: Selecting a day
        viewModel.selectDay(today)
        advanceUntilIdle()

        // Then: editingDay should be set
        assertThat(viewModel.uiState.value.editingDay).isNotNull()
        assertThat(viewModel.uiState.value.editingDay?.id).isEqualTo(1)
        assertThat(viewModel.uiState.value.editingTimeBlocks).hasSize(1)
    }

    @Test
    fun `selectDay sets editingDay to new WorkDay when day does not exist`() = runTest {
        // Given: ViewModel with no work day for selected date
        val date = LocalDate.of(2026, 2, 10)
        whenever(workDayRepository.getWorkDay(date)).thenReturn(flowOf(null))

        viewModel = MonthViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateDayWorkTime, calculateQuota, calculateFlextime, dataChangeEventBus
        )
        advanceUntilIdle()

        // When: Selecting a day
        viewModel.selectDay(date)
        advanceUntilIdle()

        // Then: editingDay should be new WorkDay
        assertThat(viewModel.uiState.value.editingDay).isNotNull()
        assertThat(viewModel.uiState.value.editingDay?.id).isEqualTo(0)
        assertThat(viewModel.uiState.value.editingDay?.date).isEqualTo(date)
        assertThat(viewModel.uiState.value.editingTimeBlocks).isEmpty()
    }

    // ========== clearEditing Tests ==========

    @Test
    fun `clearEditing sets editingDay to null`() = runTest {
        // Given: ViewModel with editing day set
        val today = LocalDate.now()
        val workDay = WorkDay(id = 1, date = today)
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = MonthViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateDayWorkTime, calculateQuota, calculateFlextime, dataChangeEventBus
        )
        advanceUntilIdle()

        viewModel.selectDay(today)
        advanceUntilIdle()

        // When: Clearing editing
        viewModel.clearEditing()

        // Then: editingDay should be null
        assertThat(viewModel.uiState.value.editingDay).isNull()
        assertThat(viewModel.uiState.value.editingTimeBlocks).isEmpty()
    }

    // ========== saveDay Tests ==========

    @Test
    fun `saveDay persists WorkDay with correct data`() = runTest {
        // Given: ViewModel initialized
        val date = LocalDate.of(2026, 2, 15)
        whenever(workDayRepository.getWorkDay(date)).thenReturn(flowOf(null))
        whenever(workDayRepository.saveWorkDay(any())).thenReturn(1L)

        viewModel = MonthViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateDayWorkTime, calculateQuota, calculateFlextime, dataChangeEventBus
        )
        advanceUntilIdle()

        viewModel.selectDay(date)
        advanceUntilIdle()

        val timeBlocks = listOf(
            TimeBlockInput(LocalTime.of(9, 0), LocalTime.of(12, 0), WorkLocation.OFFICE, isDuration = false),
            TimeBlockInput(LocalTime.of(13, 0), LocalTime.of(17, 0), WorkLocation.OFFICE, isDuration = false)
        )

        // When: Saving day
        viewModel.saveDay(
            date = date,
            dayType = DayType.WORK,
            note = "Test note",
            timeBlocks = timeBlocks
        )
        advanceUntilIdle()

        // Then: Repository should be called
        verify(workDayRepository).saveWorkDay(any())
        verify(workDayRepository, org.mockito.kotlin.times(2)).saveTimeBlock(any())
    }

    @Test
    fun `saveDay deletes existing time blocks before saving new ones`() = runTest {
        // Given: ViewModel with existing work day and time blocks
        val date = LocalDate.of(2026, 2, 15)
        val existingTimeBlock = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 0))
        val existingWorkDay = WorkDay(
            id = 1,
            date = date,
            timeBlocks = listOf(existingTimeBlock)
        )
        whenever(workDayRepository.getWorkDay(date)).thenReturn(flowOf(existingWorkDay))
        whenever(workDayRepository.saveWorkDay(any())).thenReturn(1L)

        viewModel = MonthViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateDayWorkTime, calculateQuota, calculateFlextime, dataChangeEventBus
        )
        advanceUntilIdle()

        viewModel.selectDay(date)
        advanceUntilIdle()

        val newTimeBlocks = listOf(
            TimeBlockInput(LocalTime.of(9, 0), LocalTime.of(17, 0), WorkLocation.HOME_OFFICE, isDuration = false)
        )

        // When: Saving day with new time blocks
        viewModel.saveDay(
            date = date,
            dayType = DayType.WORK,
            note = null,
            timeBlocks = newTimeBlocks
        )
        advanceUntilIdle()

        // Then: Existing time blocks should be deleted
        verify(workDayRepository).deleteTimeBlock(existingTimeBlock)
        verify(workDayRepository).saveTimeBlock(any())
    }

    @Test
    fun `saveDay clears editing state after save`() = runTest {
        // Given: ViewModel with editing day
        val date = LocalDate.of(2026, 2, 15)
        whenever(workDayRepository.getWorkDay(date)).thenReturn(flowOf(null))
        whenever(workDayRepository.saveWorkDay(any())).thenReturn(1L)

        viewModel = MonthViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateDayWorkTime, calculateQuota, calculateFlextime, dataChangeEventBus
        )
        advanceUntilIdle()

        viewModel.selectDay(date)
        advanceUntilIdle()

        // When: Saving day
        viewModel.saveDay(
            date = date,
            dayType = DayType.WORK,
            note = null,
            timeBlocks = listOf(TimeBlockInput(LocalTime.of(9, 0), LocalTime.of(17, 0), WorkLocation.OFFICE, isDuration = false))
        )
        advanceUntilIdle()

        // Then: Editing state should be cleared
        assertThat(viewModel.uiState.value.editingDay).isNull()
        assertThat(viewModel.uiState.value.editingTimeBlocks).isEmpty()
    }

    // ========== deleteDay Tests ==========

    @Test
    fun `deleteDay removes WorkDay and TimeBlocks from repository`() = runTest {
        // Given: ViewModel initialized
        val timeBlock1 = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(12, 0))
        val timeBlock2 = TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(13, 0), endTime = LocalTime.of(17, 0))
        val workDay = WorkDay(
            id = 1,
            date = LocalDate.now(),
            timeBlocks = listOf(timeBlock1, timeBlock2)
        )

        viewModel = MonthViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateDayWorkTime, calculateQuota, calculateFlextime, dataChangeEventBus
        )
        advanceUntilIdle()

        // When: Deleting day
        viewModel.deleteDay(workDay)
        advanceUntilIdle()

        // Then: All time blocks and work day should be deleted
        verify(workDayRepository).deleteTimeBlock(timeBlock1)
        verify(workDayRepository).deleteTimeBlock(timeBlock2)
        verify(workDayRepository).deleteWorkDay(workDay)
    }

    @Test
    fun `deleteDay clears editing state`() = runTest {
        // Given: ViewModel with editing state
        val today = LocalDate.now()
        val workDay = WorkDay(id = 1, date = today)
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = MonthViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateDayWorkTime, calculateQuota, calculateFlextime, dataChangeEventBus
        )
        advanceUntilIdle()

        viewModel.selectDay(today)
        advanceUntilIdle()

        // When: Deleting day
        viewModel.deleteDay(workDay)
        advanceUntilIdle()

        // Then: Editing state should be cleared
        assertThat(viewModel.uiState.value.editingDay).isNull()
        assertThat(viewModel.uiState.value.editingTimeBlocks).isEmpty()
    }

    // ========== Calculations Tests ==========

    @Test
    fun `state includes calculated quota status`() = runTest {
        // Given: Calculate quota returns specific result
        val expectedQuota = QuotaStatus(
            officeMinutes = 1200,
            officeDays = 8,
            percentQuotaMet = true,
            daysQuotaMet = true
        )
        whenever(calculateQuota(any(), any(), any(), any(), any())).thenReturn(expectedQuota)

        // When: ViewModel is created
        viewModel = MonthViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateDayWorkTime, calculateQuota, calculateFlextime, dataChangeEventBus
        )
        advanceUntilIdle()

        // Then: State should contain calculated quota
        assertThat(viewModel.uiState.value.prognosisQuota).isEqualTo(expectedQuota)
    }

    @Test
    fun `state includes calculated flextime balance`() = runTest {
        // Given: Calculate flextime returns specific result
        val expectedFlextime = FlextimeBalance(
            initialMinutes = 0,
            earnedMinutes = 120,
            totalMinutes = 120,
            targetMinutes = 0,
            overtimeMinutes = 120
        )
        whenever(calculateFlextime(any(), any(), any())).thenReturn(expectedFlextime)

        // When: ViewModel is created
        viewModel = MonthViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateDayWorkTime, calculateQuota, calculateFlextime, dataChangeEventBus
        )
        advanceUntilIdle()

        // Then: State should contain calculated flextime
        assertThat(viewModel.uiState.value.prognosisFlextime).isEqualTo(expectedFlextime)
    }

    // ========== Edge Cases ==========

    @Test
    fun `empty timeBlocks list is handled correctly in saveDay`() = runTest {
        // Given: ViewModel initialized
        val date = LocalDate.of(2026, 2, 15)
        whenever(workDayRepository.getWorkDay(date)).thenReturn(flowOf(null))
        whenever(workDayRepository.saveWorkDay(any())).thenReturn(1L)

        viewModel = MonthViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateDayWorkTime, calculateQuota, calculateFlextime, dataChangeEventBus
        )
        advanceUntilIdle()

        viewModel.selectDay(date)
        advanceUntilIdle()

        // When: Saving day with no time blocks
        viewModel.saveDay(
            date = date,
            dayType = DayType.VACATION,
            note = null,
            timeBlocks = emptyList()
        )
        advanceUntilIdle()

        // Then: WorkDay should still be saved
        verify(workDayRepository).saveWorkDay(any())
    }

    @Test
    fun `deleteDay with no timeBlocks completes successfully`() = runTest {
        // Given: WorkDay with no time blocks
        val workDay = WorkDay(
            id = 1,
            date = LocalDate.now(),
            dayType = DayType.VACATION,
            timeBlocks = emptyList()
        )

        viewModel = MonthViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateDayWorkTime, calculateQuota, calculateFlextime, dataChangeEventBus
        )
        advanceUntilIdle()

        // When: Deleting day
        viewModel.deleteDay(workDay)
        advanceUntilIdle()

        // Then: Only work day should be deleted
        verify(workDayRepository).deleteWorkDay(workDay)
    }
}
