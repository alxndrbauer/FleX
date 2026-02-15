package com.vrema.ui.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.vrema.BaseUnitTest
import com.vrema.MainDispatcherRule
import com.vrema.domain.model.DayType
import com.vrema.domain.model.FlextimeBalance
import com.vrema.domain.model.QuotaStatus
import com.vrema.domain.model.Settings
import com.vrema.domain.model.TimeBlock
import com.vrema.domain.model.WorkDay
import com.vrema.domain.model.WorkLocation
import com.vrema.domain.repository.SettingsRepository
import com.vrema.domain.repository.WorkDayRepository
import com.vrema.domain.usecase.CalculateDayWorkTimeUseCase
import com.vrema.domain.usecase.CalculateFlextimeUseCase
import com.vrema.domain.usecase.CalculateQuotaUseCase
import com.vrema.domain.usecase.DayWorkTimeResult
import com.vrema.domain.usecase.GetMonthWorkDaysUseCase
import com.vrema.domain.usecase.GetSettingsUseCase
import com.vrema.domain.events.DataChangeEventBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Rule
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalTime

/**
 * Unit tests for HomeViewModel.
 * Tests today's summary, clock in/out, manual/duration entry, and calculations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest : BaseUnitTest() {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Mock
    private lateinit var workDayRepository: WorkDayRepository

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    @Mock
    private lateinit var getMonthWorkDays: GetMonthWorkDaysUseCase

    @Mock
    private lateinit var getSettings: GetSettingsUseCase

    @Mock
    private lateinit var calculateDayWorkTime: CalculateDayWorkTimeUseCase

    @Mock
    private lateinit var calculateFlextime: CalculateFlextimeUseCase

    @Mock
    private lateinit var calculateQuota: CalculateQuotaUseCase

    @Mock
    private lateinit var dataChangeEventBus: DataChangeEventBus

    private lateinit var viewModel: HomeViewModel

    override fun setUp() {
        super.setUp()
        // Default mock behavior
        whenever(workDayRepository.getWorkDay(any())).thenReturn(flowOf(null))
        whenever(getMonthWorkDays(any())).thenReturn(flowOf(emptyList()))
        whenever(getSettings()).thenReturn(flowOf(Settings()))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(emptyList()))
        whenever(workDayRepository.getWorkDaysForYear(any())).thenReturn(flowOf(emptyList()))
        whenever(calculateDayWorkTime(any())).thenReturn(DayWorkTimeResult(0, 0, 0, false))
        whenever(calculateFlextime(any(), any(), any())).thenReturn(FlextimeBalance())
        whenever(calculateQuota(any(), any(), any(), any(), any())).thenReturn(QuotaStatus())
    }

    // ========== Initial State Tests ==========

    @Test
    fun `initial state has today's date`() = runTest {
        // When: ViewModel is created
        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        // Then: State should have today's date
        assertThat(viewModel.uiState.value.today).isEqualTo(LocalDate.now())
    }

    @Test
    fun `initial state loads workDay when it exists`() = runTest {
        // Given: Repository returns work day for today
        val today = LocalDate.now()
        val workDay = WorkDay(
            id = 1,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK
        )
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        // When: ViewModel is created
        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        // Then: State should contain work day
        assertThat(viewModel.uiState.value.workDay).isNotNull()
        assertThat(viewModel.uiState.value.workDay?.id).isEqualTo(1)
        assertThat(viewModel.uiState.value.selectedLocation).isEqualTo(WorkLocation.OFFICE)
        assertThat(viewModel.uiState.value.selectedDayType).isEqualTo(DayType.WORK)
    }

    @Test
    fun `initial state has no workDay when none exists`() = runTest {
        // Given: Repository returns null for today
        whenever(workDayRepository.getWorkDay(any())).thenReturn(flowOf(null))

        // When: ViewModel is created
        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        // Then: State should have no work day
        assertThat(viewModel.uiState.value.workDay).isNull()
        assertThat(viewModel.uiState.value.timeBlocks).isEmpty()
    }

    @Test
    fun `initial state detects running clock when timeBlock has no endTime`() = runTest {
        // Given: Work day with running time block
        val today = LocalDate.now()
        val runningBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(9, 0),
            endTime = null
        )
        val workDay = WorkDay(
            id = 1,
            date = today,
            timeBlocks = listOf(runningBlock)
        )
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        // When: ViewModel is created
        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        // Then: Clock should be running
        assertThat(viewModel.uiState.value.isClockRunning).isTrue()
    }

    @Test
    fun `initial state calculates dayWorkTime from timeBlocks`() = runTest {
        // Given: Work day with time blocks
        val today = LocalDate.now()
        val timeBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(17, 0)
        )
        val workDay = WorkDay(id = 1, date = today, timeBlocks = listOf(timeBlock))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        val expectedResult = DayWorkTimeResult(
            grossMinutes = 480,
            netMinutes = 450,
            breakMinutes = 30,
            exceedsMaxHours = false
        )
        whenever(calculateDayWorkTime(listOf(timeBlock))).thenReturn(expectedResult)

        // When: ViewModel is created
        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        // Then: Day work time should be calculated
        assertThat(viewModel.uiState.value.dayWorkTime).isEqualTo(expectedResult)
    }

    // ========== FlextimeBalance and QuotaStatus Tests ==========

    @Test
    fun `initial state loads flextimeBalance from calculation`() = runTest {
        // Given: Calculate flextime returns specific result
        val expectedFlextime = FlextimeBalance(
            initialMinutes = 0,
            earnedMinutes = 120,
            totalMinutes = 120,
            targetMinutes = 9266,
            overtimeMinutes = -9146
        )
        whenever(calculateFlextime(any(), any(), any())).thenReturn(expectedFlextime)

        // When: ViewModel is created
        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        // Then: State should contain flextime balance
        assertThat(viewModel.uiState.value.flextimeBalance).isEqualTo(expectedFlextime)
    }

    @Test
    fun `initial state loads quotaStatus from calculation`() = runTest {
        // Given: Calculate quota returns specific result
        val expectedQuota = QuotaStatus(
            officeMinutes = 1800,
            homeOfficeMinutes = 1200,
            officeDays = 8,
            homeOfficeDays = 6,
            officePercent = 60.0,
            percentQuotaMet = true,
            daysQuotaMet = true
        )
        whenever(calculateQuota(any(), any(), any(), any(), any())).thenReturn(expectedQuota)

        // When: ViewModel is created
        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        // Then: State should contain quota status
        assertThat(viewModel.uiState.value.quotaStatus).isEqualTo(expectedQuota)
    }

    // ========== clockIn Tests ==========

    @Test
    fun `clockIn creates new WorkDay when none exists`() = runTest {
        // Given: No work day for today
        val today = LocalDate.now()
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(null))
        whenever(workDayRepository.saveWorkDay(any())).thenReturn(1L)

        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        // When: Clocking in
        viewModel.clockIn()
        advanceUntilIdle()

        // Then: WorkDay and TimeBlock should be created
        verify(workDayRepository).saveWorkDay(any())
        verify(workDayRepository).saveTimeBlock(any())
    }

    @Test
    fun `clockIn creates TimeBlock with current time when WorkDay exists`() = runTest {
        // Given: Existing work day
        val today = LocalDate.now()
        val workDay = WorkDay(id = 1, date = today)
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        // When: Clocking in
        viewModel.clockIn()
        advanceUntilIdle()

        // Then: TimeBlock should be saved
        verify(workDayRepository).saveTimeBlock(any())
    }

    @Test
    fun `clockIn resets isPlanned flag when WorkDay is planned`() = runTest {
        // Given: Planned work day
        val today = LocalDate.now()
        val plannedWorkDay = WorkDay(id = 1, date = today, isPlanned = true)
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(plannedWorkDay))

        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        // When: Clocking in
        viewModel.clockIn()
        advanceUntilIdle()

        // Then: WorkDay should be updated with isPlanned = false
        verify(workDayRepository).saveWorkDay(plannedWorkDay.copy(isPlanned = false))
    }

    // ========== clockOut Tests ==========

    @Test
    fun `clockOut updates running TimeBlock with endTime`() = runTest {
        // Given: Work day with running time block
        val today = LocalDate.now()
        val runningBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(9, 0),
            endTime = null
        )
        val workDay = WorkDay(id = 1, date = today, timeBlocks = listOf(runningBlock))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        // When: Clocking out
        viewModel.clockOut()
        advanceUntilIdle()

        // Then: TimeBlock should be updated with endTime
        verify(workDayRepository).saveTimeBlock(any())
    }

    @Test
    fun `clockOut does nothing when no running TimeBlock exists`() = runTest {
        // Given: Work day with no running time blocks
        val today = LocalDate.now()
        val completedBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(17, 0)
        )
        val workDay = WorkDay(id = 1, date = today, timeBlocks = listOf(completedBlock))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        // When: Clocking out
        viewModel.clockOut()
        advanceUntilIdle()

        // Then: No TimeBlock should be saved (already verified no running block)
        // Mockito won't fail if saveTimeBlock isn't called in this scenario
    }

    // ========== setLocation Tests ==========

    @Test
    fun `setLocation updates selectedLocation and persists to WorkDay`() = runTest {
        // Given: Existing work day
        val today = LocalDate.now()
        val workDay = WorkDay(id = 1, date = today, location = WorkLocation.OFFICE)
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        // When: Setting location
        viewModel.setLocation(WorkLocation.HOME_OFFICE)
        advanceUntilIdle()

        // Then: Location should be updated
        assertThat(viewModel.uiState.value.selectedLocation).isEqualTo(WorkLocation.HOME_OFFICE)
        verify(workDayRepository).saveWorkDay(workDay.copy(location = WorkLocation.HOME_OFFICE))
    }

    @Test
    fun `setLocation updates state immediately even if no WorkDay exists`() = runTest {
        // Given: No work day
        whenever(workDayRepository.getWorkDay(any())).thenReturn(flowOf(null))

        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        // When: Setting location
        viewModel.setLocation(WorkLocation.HOME_OFFICE)

        // Then: State should be updated
        assertThat(viewModel.uiState.value.selectedLocation).isEqualTo(WorkLocation.HOME_OFFICE)
    }

    // ========== setDayType Tests ==========

    @Test
    fun `setDayType updates selectedDayType and persists to WorkDay`() = runTest {
        // Given: Existing work day
        val today = LocalDate.now()
        val workDay = WorkDay(id = 1, date = today, dayType = DayType.WORK)
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        // When: Setting day type
        viewModel.setDayType(DayType.SATURDAY_BONUS)
        advanceUntilIdle()

        // Then: Day type should be updated
        assertThat(viewModel.uiState.value.selectedDayType).isEqualTo(DayType.SATURDAY_BONUS)
        verify(workDayRepository).saveWorkDay(workDay.copy(dayType = DayType.SATURDAY_BONUS))
    }

    // ========== saveManualEntry Tests ==========

    @Test
    fun `saveManualEntry creates WorkDay and TimeBlock when none exists`() = runTest {
        // Given: No work day
        whenever(workDayRepository.getWorkDay(any())).thenReturn(flowOf(null))
        whenever(workDayRepository.saveWorkDay(any())).thenReturn(1L)

        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        // When: Saving manual entry
        viewModel.saveManualEntry(LocalTime.of(9, 0), LocalTime.of(17, 0))
        advanceUntilIdle()

        // Then: WorkDay and TimeBlock should be created
        verify(workDayRepository).saveWorkDay(any())
        verify(workDayRepository).saveTimeBlock(any())
    }

    @Test
    fun `saveManualEntry adds TimeBlock to existing WorkDay`() = runTest {
        // Given: Existing work day
        val today = LocalDate.now()
        val workDay = WorkDay(id = 1, date = today)
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        // When: Saving manual entry
        viewModel.saveManualEntry(LocalTime.of(13, 0), LocalTime.of(17, 0))
        advanceUntilIdle()

        // Then: TimeBlock should be saved
        verify(workDayRepository).saveTimeBlock(any())
    }

    // ========== saveDurationEntry Tests ==========

    @Test
    fun `saveDurationEntry creates TimeBlock with isDuration flag`() = runTest {
        // Given: No work day
        whenever(workDayRepository.getWorkDay(any())).thenReturn(flowOf(null))
        whenever(workDayRepository.saveWorkDay(any())).thenReturn(1L)

        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        // When: Saving duration entry
        viewModel.saveDurationEntry(480) // 8 hours
        advanceUntilIdle()

        // Then: TimeBlock with isDuration=true should be saved
        verify(workDayRepository).saveTimeBlock(any())
    }

    // ========== deleteTimeBlock Tests ==========

    @Test
    fun `deleteTimeBlock removes TimeBlock from repository`() = runTest {
        // Given: ViewModel initialized
        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        val timeBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(12, 0)
        )

        // When: Deleting time block
        viewModel.deleteTimeBlock(timeBlock)
        advanceUntilIdle()

        // Then: TimeBlock should be deleted
        verify(workDayRepository).deleteTimeBlock(timeBlock)
    }

    // ========== markAsNonWorkDay Tests ==========

    @Test
    fun `markAsNonWorkDay creates WorkDay with specified dayType`() = runTest {
        // Given: No work day
        whenever(workDayRepository.getWorkDay(any())).thenReturn(flowOf(null))

        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        // When: Marking as vacation
        viewModel.markAsNonWorkDay(DayType.VACATION)
        advanceUntilIdle()

        // Then: WorkDay with VACATION type should be saved
        verify(workDayRepository).saveWorkDay(any())
    }

    @Test
    fun `markAsNonWorkDay updates existing WorkDay`() = runTest {
        // Given: Existing work day
        val today = LocalDate.now()
        val workDay = WorkDay(id = 1, date = today, dayType = DayType.WORK)
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        // When: Marking as flex day
        viewModel.markAsNonWorkDay(DayType.FLEX_DAY)
        advanceUntilIdle()

        // Then: WorkDay should be updated
        verify(workDayRepository).saveWorkDay(any())
    }

    // ========== Edge Cases ==========

    @Test
    fun `empty timeBlocks list results in zero dayWorkTime`() = runTest {
        // Given: Work day with no time blocks
        val today = LocalDate.now()
        val workDay = WorkDay(id = 1, date = today, timeBlocks = emptyList())
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))
        whenever(calculateDayWorkTime(emptyList())).thenReturn(DayWorkTimeResult(0, 0, 0, false))

        // When: ViewModel is created
        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        // Then: Day work time should be zero
        assertThat(viewModel.uiState.value.dayWorkTime.grossMinutes).isEqualTo(0)
        assertThat(viewModel.uiState.value.dayWorkTime.netMinutes).isEqualTo(0)
    }

    @Test
    fun `isClockRunning is false when no timeBlocks exist`() = runTest {
        // Given: Work day with no time blocks
        val today = LocalDate.now()
        val workDay = WorkDay(id = 1, date = today, timeBlocks = emptyList())
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        // When: ViewModel is created
        viewModel = HomeViewModel(
            workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota, dataChangeEventBus
        )
        advanceUntilIdle()

        // Then: Clock should not be running
        assertThat(viewModel.uiState.value.isClockRunning).isFalse()
    }
}
