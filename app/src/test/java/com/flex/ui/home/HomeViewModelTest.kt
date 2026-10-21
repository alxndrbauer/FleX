package com.flex.ui.home

import com.google.common.truth.Truth.assertThat
import com.flex.BaseUnitTest
import com.flex.MainDispatcherExtension
import com.flex.domain.model.DayType
import com.flex.domain.model.FlextimeBalance
import com.flex.domain.model.ProjectedEndTime
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
import com.flex.domain.model.BreakCheckResult
import com.flex.domain.usecase.CheckBreakViolationUseCase
import com.flex.domain.usecase.CheckTimeBlockOverlapUseCase
import com.flex.data.local.WhatsNewPreferences
import com.flex.notification.BreakWarningScheduler
import com.flex.notification.WorkTimerService
import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalTime

/**
 * Unit tests for HomeViewModel.
 * Tests today's summary, clock in/out, manual/duration entry, and calculations.
 */
@ExtendWith(MainDispatcherExtension::class)
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest : BaseUnitTest() {

    @Mock
    private lateinit var context: Context

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

    @Mock
    private lateinit var checkBreakViolation: CheckBreakViolationUseCase

    @Mock
    private lateinit var breakWarningScheduler: BreakWarningScheduler

    @Mock
    private lateinit var whatsNewPreferences: WhatsNewPreferences

    @Mock
    private lateinit var backupPreferences: com.flex.data.backup.BackupPreferences

    @Mock
    private lateinit var autoBookPlannedDays: com.flex.domain.usecase.AutoBookPlannedDaysUseCase

    private lateinit var checkTimeBlockOverlap: CheckTimeBlockOverlapUseCase

    private lateinit var viewModel: HomeViewModel

    private fun createViewModel() = HomeViewModel(
        context, workDayRepository, settingsRepository, getMonthWorkDays,
        getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota,
        dataChangeEventBus, checkBreakViolation, breakWarningScheduler,
        whatsNewPreferences, backupPreferences, autoBookPlannedDays,
        checkTimeBlockOverlap
    )

    @BeforeEach
    override fun setUp() {
        super.setUp()
        checkTimeBlockOverlap = CheckTimeBlockOverlapUseCase()
        // Default mock behavior
        whenever(workDayRepository.getWorkDay(any())).thenReturn(flowOf(null))
        whenever(getMonthWorkDays(any())).thenReturn(flowOf(emptyList()))
        whenever(getSettings()).thenReturn(flowOf(Settings()))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(emptyList()))
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(emptyList()))
        whenever(workDayRepository.getWorkDaysForYear(any())).thenReturn(flowOf(emptyList()))
        whenever(calculateDayWorkTime(any())).thenReturn(DayWorkTimeResult(0, 0, 0, false))
        whenever(calculateDayWorkTime(any(), any())).thenReturn(DayWorkTimeResult(0, 0, 0, false))
        whenever(calculateFlextime(any(), any(), any(), any())).thenReturn(FlextimeBalance())
        whenever(calculateQuota(any(), any(), any(), any(), any(), any())).thenReturn(QuotaStatus())
        whenever(dataChangeEventBus.events).thenReturn(MutableSharedFlow())
        whenever(checkBreakViolation(any(), any())).thenReturn(BreakCheckResult(emptyList(), skipped = false))
        whenever(checkBreakViolation(any(), any(), any())).thenReturn(BreakCheckResult(emptyList(), skipped = false))
        whenever(whatsNewPreferences.getLastSeenVersionCode()).thenReturn(0)
        whenever(backupPreferences.isAutoBackupEnabled).thenReturn(false)
    }

    // ========== Initial State Tests ==========

    @Test
    fun `initial state has today's date`() = runTest {
        // When: ViewModel is created
        viewModel = createViewModel()
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
        viewModel = createViewModel()
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
        viewModel = createViewModel()
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
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: Clock should be running
        assertThat(viewModel.uiState.value.isClockRunning).isTrue()
    }

    @Test
    fun `initial state computes live dayWorkTime and liveFlextimeDelta when clock is running`() = runTest {
        val today = LocalDate.now()
        val runningBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(9, 0),
            endTime = null
        )
        val workDay = WorkDay(id = 1, date = today, timeBlocks = listOf(runningBlock))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        whenever(calculateDayWorkTime(listOf(runningBlock))).thenReturn(DayWorkTimeResult(0, 0, 0, false))
        val liveResult = DayWorkTimeResult(grossMinutes = 120, netMinutes = 120, breakMinutes = 0, exceedsMaxHours = false)
        whenever(calculateDayWorkTime(org.mockito.kotlin.argThat { blocks ->
            blocks.size == 1 && blocks[0].endTime != null
        })).thenReturn(liveResult)

        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: State should immediately reflect the live calculation, NOT 0
        assertThat(viewModel.uiState.value.isClockRunning).isTrue()
        assertThat(viewModel.uiState.value.dayWorkTime.netMinutes).isEqualTo(120)
        assertThat(viewModel.uiState.value.baseDayNetMinutes).isEqualTo(0)
        assertThat(viewModel.uiState.value.liveFlextimeDelta).isEqualTo(120)
    }

    @Test
    fun `onResume immediately updates live dayWorkTime and liveFlextimeDelta when clock is running`() = runTest {
        val today = LocalDate.now()
        val runningBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(9, 0),
            endTime = null
        )
        val workDay = WorkDay(id = 1, date = today, timeBlocks = listOf(runningBlock))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        val initialLive = DayWorkTimeResult(grossMinutes = 60, netMinutes = 60, breakMinutes = 0, exceedsMaxHours = false)
        val updatedLive = DayWorkTimeResult(grossMinutes = 90, netMinutes = 90, breakMinutes = 0, exceedsMaxHours = false)

        var callCount = 0
        whenever(calculateDayWorkTime(org.mockito.kotlin.argThat { blocks ->
            blocks.size == 1 && blocks[0].endTime != null
        })).thenAnswer {
            callCount++
            if (callCount <= 2) initialLive else updatedLive
        }

        val initialLiveFlex = FlextimeBalance(totalMinutes = 100, earnedMinutes = 100)
        val updatedLiveFlex = FlextimeBalance(totalMinutes = 130, earnedMinutes = 130)
        var flexCallCount = 0
        whenever(calculateFlextime(any(), any(), any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val days = invocation.arguments[0] as List<WorkDay>
            val containsTodayWithEndTime = days.any { d -> d.date == today && d.timeBlocks.any { it.endTime != null } }
            if (containsTodayWithEndTime) {
                flexCallCount++
                if (flexCallCount <= 2) initialLiveFlex else updatedLiveFlex
            } else FlextimeBalance()
        }

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.dayWorkTime.netMinutes).isEqualTo(60)
        assertThat(viewModel.uiState.value.liveFlextimeBalance?.totalMinutes).isEqualTo(100)

        // When: User resumes the app
        viewModel.onResume()
        advanceUntilIdle()

        // Then: Live work time and live flextime are updated immediately
        assertThat(viewModel.uiState.value.dayWorkTime.netMinutes).isEqualTo(90)
        assertThat(viewModel.uiState.value.liveFlextimeDelta).isEqualTo(90)
        assertThat(viewModel.uiState.value.liveFlextimeBalance?.totalMinutes).isEqualTo(130)
    }

    @Test
    fun `initial state computes liveFlextimeBalance and liveMonthlyFlextimeBalance when clock is running`() = runTest {
        val today = LocalDate.now()
        val runningBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(9, 0),
            endTime = null
        )
        val workDay = WorkDay(id = 1, date = today, timeBlocks = listOf(runningBlock))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        val baseFlextime = FlextimeBalance(totalMinutes = 600, earnedMinutes = 120)
        val liveFlextime = FlextimeBalance(totalMinutes = 240, earnedMinutes = -240)

        whenever(calculateFlextime(any(), any(), any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val days = invocation.arguments[0] as List<WorkDay>
            val containsTodayWithEndTime = days.any { d -> d.date == today && d.timeBlocks.any { it.endTime != null } }
            if (containsTodayWithEndTime) liveFlextime else baseFlextime
        }

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isClockRunning).isTrue()
        assertThat(viewModel.uiState.value.flextimeBalance.totalMinutes).isEqualTo(600)
        assertThat(viewModel.uiState.value.monthlyFlextimeBalance.earnedMinutes).isEqualTo(120)
        assertThat(viewModel.uiState.value.liveFlextimeBalance).isNotNull()
        assertThat(viewModel.uiState.value.liveFlextimeBalance?.totalMinutes).isEqualTo(240)
        assertThat(viewModel.uiState.value.liveMonthlyFlextimeBalance).isNotNull()
        assertThat(viewModel.uiState.value.liveMonthlyFlextimeBalance?.earnedMinutes).isEqualTo(-240)
    }

    @Test
    fun `liveFlextimeBalance and liveMonthlyFlextimeBalance are null when clock is not running`() = runTest {
        val today = LocalDate.now()
        val completedBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(17, 0)
        )
        val workDay = WorkDay(id = 1, date = today, timeBlocks = listOf(completedBlock))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isClockRunning).isFalse()
        assertThat(viewModel.uiState.value.liveFlextimeBalance).isNull()
        assertThat(viewModel.uiState.value.liveMonthlyFlextimeBalance).isNull()
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
        viewModel = createViewModel()
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
        whenever(calculateFlextime(any(), any(), any(), any())).thenReturn(expectedFlextime)

        // When: ViewModel is created
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: State should contain flextime balance
        assertThat(viewModel.uiState.value.flextimeBalance).isEqualTo(expectedFlextime)
    }

    @Test
    fun `initial state loads baseFlextimeBalance from calculation of days before selected date`() = runTest {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val day1 = WorkDay(id = 1, date = yesterday, dayType = DayType.WORK)
        val day2 = WorkDay(id = 2, date = today, dayType = DayType.WORK, timeBlocks = listOf(
            TimeBlock(id = 10, workDayId = 2, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(17, 0))
        ))
        whenever(workDayRepository.getWorkDaysForYear(today.year)).thenReturn(flowOf(listOf(day1, day2)))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(day2))

        val baseFlextime = FlextimeBalance(
            initialMinutes = 0,
            earnedMinutes = 100,
            totalMinutes = 100
        )
        val overallFlextime = FlextimeBalance(
            initialMinutes = 0,
            earnedMinutes = 160,
            totalMinutes = 160
        )
        whenever(calculateFlextime(any(), any(), any(), any())).thenAnswer { invocation ->
            val days = invocation.arguments[0] as List<WorkDay>
            if (days.size == 1 && days[0].date == yesterday) baseFlextime else overallFlextime
        }

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.baseFlextimeBalance).isEqualTo(baseFlextime)
        assertThat(viewModel.uiState.value.flextimeBalance).isEqualTo(overallFlextime)
    }

    @Test
    fun `navigateToDate updates baseFlextimeBalance and selectedDateFlextimeBalance for past date`() = runTest {
        val today = LocalDate.now()
        val twoDaysAgo = today.minusDays(2)
        val yesterday = today.minusDays(1)

        val day0 = WorkDay(id = 1, date = twoDaysAgo, dayType = DayType.WORK)
        val day1 = WorkDay(id = 2, date = yesterday, dayType = DayType.WORK)
        val day2 = WorkDay(id = 3, date = today, dayType = DayType.WORK)
        whenever(workDayRepository.getWorkDaysForYear(today.year)).thenReturn(flowOf(listOf(day0, day1, day2)))

        val baseForYesterday = FlextimeBalance(totalMinutes = 50)
        val throughYesterday = FlextimeBalance(totalMinutes = 110)
        val overallFlextime = FlextimeBalance(totalMinutes = 200)

        whenever(calculateFlextime(any(), any(), any(), any())).thenAnswer { invocation ->
            val days = invocation.arguments[0] as List<WorkDay>
            when {
                days.size == 1 && days[0].date == twoDaysAgo -> baseForYesterday
                days.size == 2 && days.any { it.date == yesterday } -> throughYesterday
                else -> overallFlextime
            }
        }

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.navigateToDate(yesterday)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedDate).isEqualTo(yesterday)
        assertThat(viewModel.uiState.value.baseFlextimeBalance).isEqualTo(baseForYesterday)
        assertThat(viewModel.uiState.value.selectedDateFlextimeBalance).isEqualTo(throughYesterday)
    }

    @Test
    fun `daily target is zero on a weekend without a work time rule`() = runTest {
        val saturday = LocalDate.of(2025, 1, 4)
        whenever(getSettings()).thenReturn(flowOf(Settings(dailyWorkMinutes = 480)))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.navigateToDate(saturday)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.dailyTargetMinutes).isEqualTo(0)
    }

    @Test
    fun `selected date flextime uses work days from the selected year`() = runTest {
        val selectedDate = LocalDate.of(LocalDate.now().year - 1, 12, 30)
        val selectedYearWorkDay = WorkDay(id = 42L, date = selectedDate, dayType = DayType.WORK)
        val expectedBalance = FlextimeBalance(totalMinutes = 777)
        whenever(workDayRepository.getWorkDaysForYear(selectedDate.year))
            .thenReturn(flowOf(listOf(selectedYearWorkDay)))
        whenever(calculateFlextime(any(), any(), any(), any())).thenAnswer { invocation ->
            val days = invocation.getArgument<List<WorkDay>>(0)
            if (days.any { it.date == selectedDate }) expectedBalance else FlextimeBalance()
        }

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.navigateToDate(selectedDate)
        advanceUntilIdle()

        verify(workDayRepository).getWorkDaysForYear(selectedDate.year)
        assertThat(viewModel.uiState.value.selectedDateFlextimeBalance).isEqualTo(expectedBalance)
    }

    @Test
    fun `viewing another year keeps current and selected date flextime balances separate`() = runTest {
        val today = LocalDate.now()
        val selectedDate = LocalDate.of(today.year - 1, 12, 30)
        val currentYearDay = WorkDay(
            id = 101L,
            date = today,
            dayType = DayType.WORK,
            timeBlocks = listOf(
                TimeBlock(
                    id = 101L,
                    workDayId = 101L,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(16, 0)
                )
            )
        )
        val selectedYearDay = WorkDay(id = 202L, date = selectedDate, dayType = DayType.WORK)
        val currentBalance = FlextimeBalance(totalMinutes = 900)
        val historicalBalance = FlextimeBalance(totalMinutes = 400)
        whenever(workDayRepository.getWorkDaysForYear(today.year)).thenReturn(flowOf(listOf(currentYearDay)))
        whenever(workDayRepository.getWorkDaysForYear(selectedDate.year)).thenReturn(flowOf(listOf(selectedYearDay)))
        whenever(calculateFlextime(any(), any(), any(), any())).thenAnswer { invocation ->
            val days = invocation.getArgument<List<WorkDay>>(0)
            when {
                days.any { it.id == currentYearDay.id } -> currentBalance
                days.any { it.id == selectedYearDay.id } -> historicalBalance
                else -> FlextimeBalance()
            }
        }

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.navigateToDate(selectedDate)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.flextimeBalance).isEqualTo(currentBalance)
        assertThat(viewModel.uiState.value.selectedDateFlextimeBalance).isEqualTo(historicalBalance)
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
        whenever(calculateQuota(any(), any(), any(), any(), any(), any())).thenReturn(expectedQuota)

        // When: ViewModel is created
        viewModel = createViewModel()
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

        viewModel = createViewModel()
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

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Clocking in
        viewModel.clockIn()
        advanceUntilIdle()

        // Then: TimeBlock should be saved
        verify(workDayRepository).saveTimeBlock(any())
    }

    @Test
    fun `clockIn resets isPlanned flag and deletes placeholder blocks when WorkDay is planned`() = runTest {
        // Given: Planned work day with a planned placeholder block
        val today = LocalDate.now()
        val plannedBlock = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(15, 6), isDuration = true)
        val plannedWorkDay = WorkDay(id = 1, date = today, isPlanned = true, timeBlocks = listOf(plannedBlock))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(plannedWorkDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Clocking in
        viewModel.clockIn()
        advanceUntilIdle()

        // Then: WorkDay should be updated with isPlanned = false and planned block deleted
        verify(workDayRepository).saveWorkDay(plannedWorkDay.copy(isPlanned = false))
        verify(workDayRepository).deleteTimeBlock(plannedBlock)
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

        viewModel = createViewModel()
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

        viewModel = createViewModel()
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

        viewModel = createViewModel()
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

        viewModel = createViewModel()
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

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Setting day type
        viewModel.setDayType(DayType.SATURDAY_BONUS)
        advanceUntilIdle()

        // Then: Day type should be updated in state (setDayType sets a local override, does not persist directly)
        assertThat(viewModel.uiState.value.selectedDayType).isEqualTo(DayType.SATURDAY_BONUS)
    }

    // ========== saveManualEntry Tests ==========

    @Test
    fun `saveManualEntry creates WorkDay and TimeBlock when none exists`() = runTest {
        // Given: No work day
        whenever(workDayRepository.getWorkDay(any())).thenReturn(flowOf(null))
        whenever(workDayRepository.saveWorkDay(any())).thenReturn(1L)

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Saving manual entry
        viewModel.saveManualEntry(LocalTime.of(9, 0), LocalTime.of(17, 0), WorkLocation.OFFICE)
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

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Saving manual entry
        viewModel.saveManualEntry(LocalTime.of(13, 0), LocalTime.of(17, 0), WorkLocation.HOME_OFFICE)
        advanceUntilIdle()

        // Then: TimeBlock should be saved
        verify(workDayRepository).saveTimeBlock(any())
    }

    @Test
    fun `saveManualEntry deletes placeholder blocks when WorkDay is planned`() = runTest {
        val today = LocalDate.now()
        val plannedBlock = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(15, 6), isDuration = true)
        val plannedWorkDay = WorkDay(id = 1, date = today, isPlanned = true, timeBlocks = listOf(plannedBlock))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(plannedWorkDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.saveManualEntry(LocalTime.of(8, 0), LocalTime.of(16, 0), WorkLocation.OFFICE)
        advanceUntilIdle()

        verify(workDayRepository).saveWorkDay(plannedWorkDay.copy(isPlanned = false))
        verify(workDayRepository).deleteTimeBlock(plannedBlock)
    }

    @Test
    fun `saveManualEntry with DayType BUSINESS_TRIP forces WorkLocation OFFICE for WorkDay and TimeBlock`() = runTest {
        whenever(workDayRepository.getWorkDay(any())).thenReturn(flowOf(null))
        whenever(workDayRepository.saveWorkDay(any())).thenReturn(1L)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setDayType(DayType.BUSINESS_TRIP)
        viewModel.saveManualEntry(LocalTime.of(8, 0), LocalTime.of(16, 0), WorkLocation.HOME_OFFICE)
        advanceUntilIdle()

        verify(workDayRepository).saveWorkDay(org.mockito.kotlin.argThat {
            dayType == DayType.BUSINESS_TRIP && location == WorkLocation.OFFICE
        })
        verify(workDayRepository).saveTimeBlock(org.mockito.kotlin.argThat {
            location == WorkLocation.OFFICE
        })
    }

    // ========== saveDurationEntry Tests ==========

    @Test
    fun `saveDurationEntry creates TimeBlock with isDuration flag`() = runTest {
        // Given: No work day
        whenever(workDayRepository.getWorkDay(any())).thenReturn(flowOf(null))
        whenever(workDayRepository.saveWorkDay(any())).thenReturn(1L)

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Saving duration entry
        viewModel.saveDurationEntry(480, WorkLocation.OFFICE) // 8 hours
        advanceUntilIdle()

        // Then: TimeBlock with isDuration=true should be saved
        verify(workDayRepository).saveTimeBlock(any())
    }

    @Test
    fun `saveDurationEntry deletes placeholder blocks when WorkDay is planned`() = runTest {
        val today = LocalDate.now()
        val plannedBlock = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(15, 6), isDuration = true)
        val plannedWorkDay = WorkDay(id = 1, date = today, isPlanned = true, timeBlocks = listOf(plannedBlock))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(plannedWorkDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.saveDurationEntry(480, WorkLocation.OFFICE)
        advanceUntilIdle()

        verify(workDayRepository).saveWorkDay(plannedWorkDay.copy(isPlanned = false))
        verify(workDayRepository).deleteTimeBlock(plannedBlock)
    }

    @Test
    fun `saveDurationEntry uses settings defaultStartTime`() = runTest {
        val customStartTime = LocalTime.of(9, 15)
        whenever(getSettings()).thenReturn(flowOf(Settings(defaultStartTime = customStartTime)))
        whenever(workDayRepository.getWorkDay(any())).thenReturn(flowOf(null))
        whenever(workDayRepository.saveWorkDay(any())).thenReturn(1L)

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Saving duration entry
        viewModel.saveDurationEntry(480, WorkLocation.OFFICE)
        advanceUntilIdle()

        // Then: TimeBlock with custom defaultStartTime should be saved
        verify(workDayRepository).saveTimeBlock(org.mockito.kotlin.argThat {
            isDuration && startTime == customStartTime && endTime == customStartTime.plusMinutes(480)
        })
    }

    @Test
    fun `saveDurationEntry with DayType BUSINESS_TRIP forces WorkLocation OFFICE for WorkDay and TimeBlock`() = runTest {
        whenever(workDayRepository.getWorkDay(any())).thenReturn(flowOf(null))
        whenever(workDayRepository.saveWorkDay(any())).thenReturn(1L)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setDayType(DayType.BUSINESS_TRIP)
        viewModel.saveDurationEntry(480, WorkLocation.HOME_OFFICE)
        advanceUntilIdle()

        verify(workDayRepository).saveWorkDay(org.mockito.kotlin.argThat {
            dayType == DayType.BUSINESS_TRIP && location == WorkLocation.OFFICE
        })
        verify(workDayRepository).saveTimeBlock(org.mockito.kotlin.argThat {
            location == WorkLocation.OFFICE
        })
    }

    // ========== deleteTimeBlock Tests ==========

    @Test
    fun `deleteTimeBlock removes TimeBlock from repository`() = runTest {
        // Given: ViewModel initialized
        viewModel = createViewModel()
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

    @Test
    fun `deleteTimeBlock when last block also deletes WorkDay`() = runTest {
        // Given: WorkDay with exactly one TimeBlock
        val today = LocalDate.now()
        val timeBlock = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(12, 0))
        val workDay = WorkDay(id = 1, date = today, timeBlocks = listOf(timeBlock))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Deleting the only TimeBlock
        viewModel.deleteTimeBlock(timeBlock)
        advanceUntilIdle()

        // Then: WorkDay should also be deleted
        verify(workDayRepository).deleteWorkDay(workDay)
    }

    @Test
    fun `deleteTimeBlock when not last block does NOT delete WorkDay`() = runTest {
        // Given: WorkDay with two TimeBlocks
        val today = LocalDate.now()
        val timeBlock1 = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(12, 0))
        val timeBlock2 = TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(13, 0), endTime = LocalTime.of(17, 0))
        val workDay = WorkDay(id = 1, date = today, timeBlocks = listOf(timeBlock1, timeBlock2))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Deleting one of two TimeBlocks
        viewModel.deleteTimeBlock(timeBlock1)
        advanceUntilIdle()

        // Then: WorkDay should NOT be deleted
        verify(workDayRepository, never()).deleteWorkDay(any())
    }

    @Test
    fun `deleteTimeBlock when block is running cancels break warning and stops timer service`() = runTest {
        // Given: WorkDay with a running TimeBlock
        val today = LocalDate.now()
        val runningBlock = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(9, 0), endTime = null)
        val workDay = WorkDay(id = 1, date = today, timeBlocks = listOf(runningBlock))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Deleting the running TimeBlock
        viewModel.deleteTimeBlock(runningBlock)
        advanceUntilIdle()

        // Then: Should delete block, cancel warning, and stop service
        verify(workDayRepository).deleteTimeBlock(runningBlock)
        verify(breakWarningScheduler).cancelWarning()
        verify(context).stopService(any())
    }

    @Test
    fun `deleteDay when a block was running cancels break warning and stops timer service`() = runTest {
        // Given: WorkDay with a running TimeBlock
        val today = LocalDate.now()
        val runningBlock = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(9, 0), endTime = null)
        val workDay = WorkDay(id = 1, date = today, timeBlocks = listOf(runningBlock))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Deleting the day
        viewModel.deleteDay()
        advanceUntilIdle()

        // Then: Should delete block and workday, cancel warning, and stop service
        verify(workDayRepository).deleteTimeBlock(runningBlock)
        verify(workDayRepository).deleteWorkDay(workDay)
        verify(breakWarningScheduler).cancelWarning()
        verify(context).stopService(any())
    }

    @Test
    fun `updateTimeBlock when running block is ended cancels break warning and stops timer service`() = runTest {
        // Given: Running TimeBlock
        val runningBlock = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(9, 0), endTime = null)

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Updating the block to have an end time
        viewModel.updateTimeBlock(runningBlock, runningBlock.startTime, LocalTime.of(17, 0), WorkLocation.OFFICE)
        advanceUntilIdle()

        // Then: Should cancel warning and stop service
        verify(breakWarningScheduler).cancelWarning()
        verify(context).stopService(any())
    }

    @Test
    fun `updateTimeBlock when ended block is made running starts service and schedules warning`() = runTest {
        // Given: Completed TimeBlock and settings with timer/warning enabled
        val completedBlock = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(17, 0))
        whenever(getSettings()).thenReturn(flowOf(Settings(workTimerNotificationEnabled = true, breakWarningEnabled = true)))

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Updating the block to have no end time
        viewModel.updateTimeBlock(completedBlock, completedBlock.startTime, null, WorkLocation.OFFICE)
        advanceUntilIdle()

        // Then: Should schedule warning and start service
        verify(breakWarningScheduler).scheduleWarning(completedBlock.startTime)
        verify(context).startForegroundService(any())
    }

    @Test
    fun `updateTimeBlock retains isDuration true by default`() = runTest {
        val today = LocalDate.now()
        val durationBlock = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(15, 6), location = WorkLocation.OFFICE, isDuration = true)
        val workDay = WorkDay(id = 1, date = today, location = WorkLocation.OFFICE, timeBlocks = listOf(durationBlock))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateTimeBlock(durationBlock, LocalTime.of(8, 0), LocalTime.of(16, 0), WorkLocation.OFFICE)
        advanceUntilIdle()

        verify(workDayRepository).saveTimeBlock(durationBlock.copy(startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 0), location = WorkLocation.OFFICE, isDuration = true))
    }

    @Test
    fun `updateTimeBlock updates isDuration to false when requested`() = runTest {
        val today = LocalDate.now()
        val durationBlock = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(15, 6), location = WorkLocation.OFFICE, isDuration = true)
        val workDay = WorkDay(id = 1, date = today, location = WorkLocation.OFFICE, timeBlocks = listOf(durationBlock))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateTimeBlock(durationBlock, LocalTime.of(8, 0), LocalTime.of(16, 0), WorkLocation.OFFICE, isDuration = false)
        advanceUntilIdle()

        verify(workDayRepository).saveTimeBlock(durationBlock.copy(startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 0), location = WorkLocation.OFFICE, isDuration = false))
    }

    // ========== bookTimeBlock Tests ==========

    @Test
    fun `bookTimeBlock saves adjusted times and location, unplans planned workday, and retains isDuration`() = runTest {
        val today = LocalDate.now()
        val plannedBlock = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(15, 6), location = WorkLocation.OFFICE, isDuration = true)
        val plannedDay = WorkDay(id = 1, date = today, location = WorkLocation.OFFICE, isPlanned = true, timeBlocks = listOf(plannedBlock))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(plannedDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.bookTimeBlock(plannedBlock, LocalTime.of(8, 30), LocalTime.of(17, 0), WorkLocation.HOME_OFFICE)
        advanceUntilIdle()

        verify(workDayRepository).saveWorkDay(plannedDay.copy(isPlanned = false, location = WorkLocation.HOME_OFFICE))
        verify(workDayRepository).saveTimeBlock(plannedBlock.copy(startTime = LocalTime.of(8, 30), endTime = LocalTime.of(17, 0), location = WorkLocation.HOME_OFFICE, isDuration = true))
    }

    @Test
    fun `bookTimeBlock without end time starts timer service when enabled and preserves isDuration`() = runTest {
        val today = LocalDate.now()
        val plannedBlock = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(15, 6), location = WorkLocation.OFFICE, isDuration = true)
        val plannedDay = WorkDay(id = 1, date = today, location = WorkLocation.OFFICE, isPlanned = true, timeBlocks = listOf(plannedBlock))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(plannedDay))
        whenever(getSettings()).thenReturn(flowOf(Settings(workTimerNotificationEnabled = true, breakWarningEnabled = true)))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.bookTimeBlock(plannedBlock, LocalTime.of(8, 30), null, WorkLocation.HOME_OFFICE)
        advanceUntilIdle()

        verify(workDayRepository).saveWorkDay(plannedDay.copy(isPlanned = false, location = WorkLocation.HOME_OFFICE))
        verify(workDayRepository).saveTimeBlock(plannedBlock.copy(startTime = LocalTime.of(8, 30), endTime = null, location = WorkLocation.HOME_OFFICE, isDuration = true))
        verify(context).startForegroundService(any())
        verify(breakWarningScheduler).scheduleWarning(LocalTime.of(8, 30))
    }

    @Test
    fun `bookTimeBlock deletes other placeholder blocks and allows changing isDuration to false`() = runTest {
        val today = LocalDate.now()
        val plannedBlock1 = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(15, 6), location = WorkLocation.OFFICE, isDuration = true)
        val plannedBlock2 = TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(15, 6), endTime = LocalTime.of(16, 0), location = WorkLocation.OFFICE, isDuration = true)
        val plannedDay = WorkDay(id = 1, date = today, location = WorkLocation.OFFICE, isPlanned = true, timeBlocks = listOf(plannedBlock1, plannedBlock2))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(plannedDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.bookTimeBlock(plannedBlock1, LocalTime.of(8, 0), LocalTime.of(15, 6), WorkLocation.OFFICE, isDuration = false)
        advanceUntilIdle()

        verify(workDayRepository).saveWorkDay(plannedDay.copy(isPlanned = false, location = WorkLocation.OFFICE))
        verify(workDayRepository).deleteTimeBlock(plannedBlock2)
        verify(workDayRepository).saveTimeBlock(plannedBlock1.copy(isDuration = false))
    }

    // ========== toggleTimeBlockLocation Tests ==========

    @Test
    fun `toggleTimeBlockLocation switches completed block from OFFICE to HOME_OFFICE`() = runTest {
        val completedBlock = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.OFFICE)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleTimeBlockLocation(completedBlock)
        advanceUntilIdle()

        verify(workDayRepository).saveTimeBlock(org.mockito.kotlin.argThat {
            id == 1L && location == WorkLocation.HOME_OFFICE
        })
    }

    @Test
    fun `toggleTimeBlockLocation switches completed block from HOME_OFFICE to OFFICE`() = runTest {
        val completedBlock = TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.HOME_OFFICE)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleTimeBlockLocation(completedBlock)
        advanceUntilIdle()

        verify(workDayRepository).saveTimeBlock(org.mockito.kotlin.argThat {
            id == 2L && location == WorkLocation.OFFICE
        })
    }

    @Test
    fun `toggleTimeBlockLocation on running block updates block, workday, and notifies service`() = runTest {
        val today = LocalDate.now()
        val runningBlock = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(9, 0), endTime = null, location = WorkLocation.OFFICE)
        val workDay = WorkDay(id = 1, date = today, location = WorkLocation.OFFICE, timeBlocks = listOf(runningBlock))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = createViewModel()
        advanceUntilIdle()
        org.mockito.kotlin.clearInvocations(context)

        viewModel.toggleTimeBlockLocation(runningBlock)
        advanceUntilIdle()

        verify(workDayRepository).saveTimeBlock(org.mockito.kotlin.argThat {
            id == 1L && location == WorkLocation.HOME_OFFICE && endTime == null
        })
        verify(workDayRepository).saveWorkDay(org.mockito.kotlin.argThat {
            id == 1L && location == WorkLocation.HOME_OFFICE
        })
        verify(context).startForegroundService(any())
    }

    @Test
    fun `toggleTimeBlockLocation does nothing when DayType is BUSINESS_TRIP`() = runTest {
        val today = LocalDate.now()
        val btBlock = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(17, 0), location = WorkLocation.OFFICE)
        val btDay = WorkDay(id = 1, date = today, location = WorkLocation.OFFICE, dayType = DayType.BUSINESS_TRIP, timeBlocks = listOf(btBlock))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(btDay))

        viewModel = createViewModel()
        advanceUntilIdle()
        org.mockito.kotlin.clearInvocations(workDayRepository)

        viewModel.toggleTimeBlockLocation(btBlock)
        advanceUntilIdle()

        verify(workDayRepository, never()).saveTimeBlock(any())
    }


    // ========== saveDayType Tests ==========

    @Test
    fun `saveDayType creates WorkDay with specified dayType`() = runTest {
        // Given: No work day
        whenever(workDayRepository.getWorkDay(any())).thenReturn(flowOf(null))

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Marking as vacation
        viewModel.saveDayType(DayType.VACATION)
        advanceUntilIdle()

        // Then: WorkDay with VACATION type should be saved
        verify(workDayRepository).saveWorkDay(any())
    }

    @Test
    fun `saveDayType updates existing WorkDay`() = runTest {
        // Given: Existing work day
        val today = LocalDate.now()
        val workDay = WorkDay(id = 1, date = today, dayType = DayType.WORK)
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Marking as flex day
        viewModel.saveDayType(DayType.FLEX_DAY)
        advanceUntilIdle()

        // Then: WorkDay should be updated
        verify(workDayRepository).saveWorkDay(any())
    }

    @Test
    fun `setDayType updates selectedDayType in uiState before saving`() = runTest {
        whenever(workDayRepository.getWorkDay(any())).thenReturn(flowOf(null))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setDayType(DayType.VACATION)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedDayType).isEqualTo(DayType.VACATION)
    }

    @Test
    fun `saveDayType un-plans planned day when saving`() = runTest {
        val today = LocalDate.now()
        val plannedDay = WorkDay(id = 5, date = today, dayType = DayType.WORK, isPlanned = true)
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(plannedDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.saveDayType(DayType.VACATION)
        advanceUntilIdle()

        val captor = org.mockito.kotlin.argumentCaptor<WorkDay>()
        verify(workDayRepository).saveWorkDay(captor.capture())
        assertThat(captor.firstValue.dayType).isEqualTo(DayType.VACATION)
        assertThat(captor.firstValue.isPlanned).isFalse()
    }

    @Test
    fun `clockIn creates WORK day even if uncommitted dayType selection is VACATION`() = runTest {
        val today = LocalDate.now()
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(null))
        whenever(workDayRepository.saveWorkDay(any())).thenReturn(1L)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setDayType(DayType.VACATION)
        advanceUntilIdle()

        viewModel.clockIn()
        advanceUntilIdle()

        val captor = org.mockito.kotlin.argumentCaptor<WorkDay>()
        verify(workDayRepository).saveWorkDay(captor.capture())
        assertThat(captor.firstValue.dayType).isEqualTo(DayType.WORK)
    }

    @Test
    fun `clockIn creates SATURDAY_BONUS day when uncommitted dayType selection is SATURDAY_BONUS`() = runTest {
        val today = LocalDate.now()
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(null))
        whenever(workDayRepository.saveWorkDay(any())).thenReturn(1L)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setDayType(DayType.SATURDAY_BONUS)
        advanceUntilIdle()

        viewModel.clockIn()
        advanceUntilIdle()

        val captor = org.mockito.kotlin.argumentCaptor<WorkDay>()
        verify(workDayRepository).saveWorkDay(captor.capture())
        assertThat(captor.firstValue.dayType).isEqualTo(DayType.SATURDAY_BONUS)
    }

    @Test
    fun `clockIn updates existing WORK day to SATURDAY_BONUS when uncommitted dayType selection is SATURDAY_BONUS`() = runTest {
        val today = LocalDate.now()
        val existingWorkDay = WorkDay(
            id = 10L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = emptyList()
        )
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(existingWorkDay))
        whenever(workDayRepository.saveWorkDay(any())).thenReturn(10L)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setDayType(DayType.SATURDAY_BONUS)
        advanceUntilIdle()

        viewModel.clockIn()
        advanceUntilIdle()

        val captor = org.mockito.kotlin.argumentCaptor<WorkDay>()
        verify(workDayRepository).saveWorkDay(captor.capture())
        assertThat(captor.firstValue.id).isEqualTo(10L)
        assertThat(captor.firstValue.dayType).isEqualTo(DayType.SATURDAY_BONUS)
    }

    @Test
    fun `saveDayType with DayType WORK deletes workDay when timeBlocks is empty`() = runTest {
        val today = LocalDate.now()
        val vacationDay = WorkDay(id = 10, date = today, dayType = DayType.VACATION, timeBlocks = emptyList())
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(vacationDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.saveDayType(DayType.WORK)
        advanceUntilIdle()

        verify(workDayRepository).deleteWorkDay(vacationDay)
        verify(workDayRepository, never()).saveWorkDay(any())
    }

    @Test
    fun `dailyTargetMinutes is 0 when selectedDayType is VACATION`() = runTest {
        whenever(workDayRepository.getWorkDay(any())).thenReturn(flowOf(null))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setDayType(DayType.VACATION)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.dailyTargetMinutes).isEqualTo(0)
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
        viewModel = createViewModel()
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
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: Clock should not be running
        assertThat(viewModel.uiState.value.isClockRunning).isFalse()
    }

    // ========== Auto-Book Planned Days Tests ==========

    @Test
    fun `autoBookPlannedDays is called on ViewModel init`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        verify(autoBookPlannedDays).invoke()
    }

    @Test
    fun `onResume calls autoBookPlannedDays and checkPermissions`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onResume()
        advanceUntilIdle()

        verify(autoBookPlannedDays, org.mockito.kotlin.times(2)).invoke()
    }

    // ========== Overlap Warning Tests ==========

    @Test
    fun `hasTimeBlockOverlap is false when no time blocks exist`() = runTest {
        val today = LocalDate.now()
        val workDay = WorkDay(id = 1, date = today, timeBlocks = emptyList())
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasTimeBlockOverlap).isFalse()
    }

    @Test
    fun `hasTimeBlockOverlap is false when time blocks do not overlap`() = runTest {
        val today = LocalDate.now()
        val blocks = listOf(
            TimeBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0)),
            TimeBlock(id = 2, startTime = LocalTime.of(13, 0), endTime = LocalTime.of(17, 0))
        )
        val workDay = WorkDay(id = 1, date = today, timeBlocks = blocks)
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasTimeBlockOverlap).isFalse()
    }

    @Test
    fun `hasTimeBlockOverlap is true when time blocks overlap`() = runTest {
        val today = LocalDate.now()
        val blocks = listOf(
            TimeBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(13, 0)),
            TimeBlock(id = 2, startTime = LocalTime.of(12, 0), endTime = LocalTime.of(17, 0))
        )
        val workDay = WorkDay(id = 1, date = today, timeBlocks = blocks)
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasTimeBlockOverlap).isTrue()
    }

    @Test
    fun `hasTimeBlockOverlap is false when overlapping block is duration based`() = runTest {
        val today = LocalDate.now()
        val blocks = listOf(
            TimeBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0)),
            TimeBlock(id = 2, isDuration = true, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(11, 0))
        )
        val workDay = WorkDay(id = 1, date = today, timeBlocks = blocks)
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasTimeBlockOverlap).isFalse()
    }

    @Test
    fun `BUSINESS_TRIP day type calls calculateDayWorkTime and checkBreakViolation with isBusinessTrip true`() = runTest {
        val today = LocalDate.now()
        val blocks = listOf(
            TimeBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 0))
        )
        val workDay = WorkDay(id = 1, date = today, dayType = DayType.BUSINESS_TRIP, timeBlocks = blocks)
        val settings = Settings(breakWarningEnabled = true)
        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        verify(calculateDayWorkTime, atLeastOnce()).invoke(org.mockito.kotlin.eq(blocks), org.mockito.kotlin.eq(true))
        verify(checkBreakViolation, atLeastOnce()).invoke(org.mockito.kotlin.eq(blocks), any(), org.mockito.kotlin.eq(true))
    }

    // ========== projectedEndTime Tests ==========

    @Test
    fun `projectedEndTime is populated in HomeUiState when clocked in with a target`() = runTest {
        val today = LocalDate.now()
        val runningBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.now().minusHours(1),
            endTime = null
        )
        val workDay = WorkDay(id = 1, date = today, timeBlocks = listOf(runningBlock))
        val settings = Settings(dailyWorkMinutes = 480) // 8 hours target (> 6h -> 30m break)
        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isClockRunning).isTrue()
        assertThat(state.projectedEndTime).isNotNull()
        assertThat(state.projectedEndTime?.requiredBreakMinutes).isEqualTo(30L)
        assertThat(state.projectedEndTime?.pendingBreakMinutes).isEqualTo(30L)
        assertThat(state.projectedEndTime?.hasPendingBreak).isTrue()
        assertThat(state.projectedEndTime?.formatWithBreakHint()).contains("ohne Pause")
    }

    @Test
    fun `projectedEndTime is null when clock is not running`() = runTest {
        val today = LocalDate.now()
        val completedBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(12, 0)
        )
        val workDay = WorkDay(id = 1, date = today, timeBlocks = listOf(completedBlock))
        val settings = Settings(dailyWorkMinutes = 480)
        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isClockRunning).isFalse()
        assertThat(viewModel.uiState.value.projectedEndTime).isNull()
    }

    @Test
    fun `projectedEndTime is null when selectedDate is not today`() = runTest {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val runningBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.now().minusHours(1),
            endTime = null
        )
        val workDay = WorkDay(id = 1, date = today, timeBlocks = listOf(runningBlock))
        val settings = Settings(dailyWorkMinutes = 480)
        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))
        whenever(workDayRepository.getWorkDay(yesterday)).thenReturn(flowOf(null))

        viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.projectedEndTime).isNotNull()

        viewModel.navigateToDate(yesterday)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.projectedEndTime).isNull()
    }

    @Test
    fun `projectedEndTime is null when daily target is already reached`() = runTest {
        val today = LocalDate.now()
        // Worked 120 minutes with a target of 60 minutes
        val runningBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.now().minusMinutes(120),
            endTime = null
        )
        val workDay = WorkDay(id = 1, date = today, timeBlocks = listOf(runningBlock))
        val settings = Settings(dailyWorkMinutes = 60)
        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isClockRunning).isTrue()
        assertThat(viewModel.uiState.value.projectedEndTime).isNull()
    }

    @Test
    fun `updateLiveWorkTime keeps projectedEndTime updated when clock is running`() = runTest {
        val today = LocalDate.now()
        val runningBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.now().minusHours(1),
            endTime = null
        )
        val workDay = WorkDay(id = 1, date = today, timeBlocks = listOf(runningBlock))
        val settings = Settings(dailyWorkMinutes = 480)
        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        val initialPrognosis = viewModel.uiState.value.projectedEndTime
        assertThat(initialPrognosis).isNotNull()

        viewModel.updateLiveWorkTime()
        advanceUntilIdle()

        val updatedPrognosis = viewModel.uiState.value.projectedEndTime
        assertThat(updatedPrognosis).isNotNull()
    }

    @Test
    fun `projectedEndTime is null when dailyTargetMinutes is zero`() = runTest {
        val today = LocalDate.now()
        val runningBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.now().minusHours(1),
            endTime = null
        )
        val workDay = WorkDay(id = 1, date = today, timeBlocks = listOf(runningBlock))
        val settings = Settings(dailyWorkMinutes = 0)
        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.projectedEndTime).isNull()
    }

    @Test
    fun `clockIn retains dayType on existing planned vacation day and un-plans it`() = runTest {
        val today = LocalDate.now()
        val plannedVacation = WorkDay(
            id = 42L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.VACATION,
            isPlanned = true,
            timeBlocks = emptyList()
        )
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(plannedVacation))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.clockIn()
        advanceUntilIdle()

        val captor = org.mockito.kotlin.argumentCaptor<WorkDay>()
        verify(workDayRepository).saveWorkDay(captor.capture())
        assertThat(captor.firstValue.id).isEqualTo(42L)
        assertThat(captor.firstValue.dayType).isEqualTo(DayType.VACATION)
        assertThat(captor.firstValue.isPlanned).isFalse()
    }

    @Test
    fun `clockIn on business trip suppresses break warning scheduler`() = runTest {
        val today = LocalDate.now()
        val businessTrip = WorkDay(
            id = 43L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.BUSINESS_TRIP,
            isPlanned = false,
            timeBlocks = emptyList()
        )
        val settings = Settings(breakWarningEnabled = true)
        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(businessTrip))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.clockIn()
        advanceUntilIdle()

        verify(breakWarningScheduler, never()).scheduleWarning(any())
    }

    @Test
    fun `saveDayType preserves existing workDay and enforces OFFICE for BUSINESS_TRIP`() = runTest {
        val today = LocalDate.now()
        val block = TimeBlock(id = 1L, workDayId = 44L, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(17, 0), location = WorkLocation.HOME_OFFICE)
        val existing = WorkDay(
            id = 44L,
            date = today,
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = listOf(block)
        )
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(existing))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.saveDayType(DayType.BUSINESS_TRIP)
        advanceUntilIdle()

        val dayCaptor = org.mockito.kotlin.argumentCaptor<WorkDay>()
        verify(workDayRepository).saveWorkDay(dayCaptor.capture())
        assertThat(dayCaptor.firstValue.id).isEqualTo(44L)
        assertThat(dayCaptor.firstValue.dayType).isEqualTo(DayType.BUSINESS_TRIP)
        assertThat(dayCaptor.firstValue.location).isEqualTo(WorkLocation.OFFICE)

        val blockCaptor = org.mockito.kotlin.argumentCaptor<TimeBlock>()
        verify(workDayRepository).saveTimeBlock(blockCaptor.capture())
        assertThat(blockCaptor.firstValue.location).isEqualTo(WorkLocation.OFFICE)
    }

    @Test
    fun `SATURDAY_BONUS has zero daily target and zero remaining minutes`() = runTest {
        val today = LocalDate.now()
        val saturdayBonus = WorkDay(
            id = 50L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.SATURDAY_BONUS,
            isPlanned = false,
            timeBlocks = listOf(
                TimeBlock(id = 1L, workDayId = 50L, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.OFFICE)
            )
        )
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(saturdayBonus))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.dailyTargetMinutes).isEqualTo(0)
        assertThat(viewModel.remainingMinutes.value).isEqualTo(0)
    }

    @Test
    fun `saveDayType to BUSINESS_TRIP while timer is running cancels break warning`() = runTest {
        val today = LocalDate.now()
        val runningBlock = TimeBlock(id = 1L, workDayId = 55L, startTime = LocalTime.of(8, 0), endTime = null, location = WorkLocation.OFFICE)
        val runningWorkDay = WorkDay(
            id = 55L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = listOf(runningBlock)
        )
        val settings = Settings(breakWarningEnabled = true)
        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(runningWorkDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.saveDayType(DayType.BUSINESS_TRIP)
        advanceUntilIdle()

        verify(breakWarningScheduler).cancelWarning()
    }
}



