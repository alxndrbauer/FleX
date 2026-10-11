package com.flex.ui.planning

import com.google.common.truth.Truth.assertThat
import com.flex.BaseUnitTest
import com.flex.MainDispatcherExtension
import com.flex.domain.model.DayType
import com.flex.domain.model.FlextimeBalance
import com.flex.domain.model.PublicHolidays
import com.flex.domain.model.QuotaStatus
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.BuildPrognosisDaysUseCase
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.DayWorkTimeResult
import com.flex.domain.model.SyncPlannedDaysResult
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.usecase.GetMonthWorkDaysUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.domain.usecase.SyncPlannedDaysWithWorkTimeRuleUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth

@ExtendWith(MainDispatcherExtension::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PlanningViewModelTest : BaseUnitTest() {

    @Mock private lateinit var getMonthWorkDays: GetMonthWorkDaysUseCase
    @Mock private lateinit var getSettings: GetSettingsUseCase
    @Mock private lateinit var workDayRepository: WorkDayRepository
    @Mock private lateinit var settingsRepository: SettingsRepository
    @Mock private lateinit var calculateDayWorkTime: CalculateDayWorkTimeUseCase
    @Mock private lateinit var calculateQuota: CalculateQuotaUseCase
    @Mock private lateinit var calculateFlextime: CalculateFlextimeUseCase
    @Mock private lateinit var buildPrognosisDays: BuildPrognosisDaysUseCase
    @Mock private lateinit var syncPlannedDaysWithWorkTimeRuleUseCase: SyncPlannedDaysWithWorkTimeRuleUseCase

    private lateinit var viewModel: PlanningViewModel

    @BeforeEach
    override fun setUp() {
        super.setUp()
        whenever(getMonthWorkDays(any())).thenReturn(flowOf(emptyList()))
        whenever(getSettings()).thenReturn(flowOf(Settings()))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(emptyList()))
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(emptyList()))
        whenever(workDayRepository.getWorkDaysForYear(any())).thenReturn(flowOf(emptyList()))
        whenever(calculateQuota(any(), any(), any(), any(), any(), any())).thenReturn(QuotaStatus())
        whenever(calculateFlextime(any(), any(), anyOrNull(), any())).thenReturn(FlextimeBalance())
        whenever(calculateDayWorkTime(any())).thenReturn(DayWorkTimeResult(0, 0, 0, false))
        whenever(calculateDayWorkTime(any(), any())).thenReturn(DayWorkTimeResult(0, 0, 0, false))
        whenever(buildPrognosisDays(any(), any(), any(), any())).thenAnswer { inv -> inv.getArgument(1) }
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(null)
        whenever(settingsRepository.getWorkTimeRuleForDate(any(), any())).thenReturn(null)
    }

    private fun createViewModel() = PlanningViewModel(
        getMonthWorkDays, getSettings, workDayRepository, settingsRepository,
        calculateDayWorkTime, calculateQuota, calculateFlextime, buildPrognosisDays,
        syncPlannedDaysWithWorkTimeRuleUseCase
    )

    @Test
    fun `planDay creates time block with settings defaultStartTime`() = runTest {
        val customStartTime = LocalTime.of(9, 30)
        val settings = Settings(defaultStartTime = customStartTime, dailyWorkMinutes = 480)
        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(workDayRepository.saveWorkDay(any())).thenReturn(10L)

        viewModel = createViewModel()
        advanceUntilIdle()

        val nextMonth = YearMonth.now().plusMonths(1)
        val testDate = (1..28)
            .map { nextMonth.atDay(it) }
            .first { it.dayOfWeek !in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) && !PublicHolidays.isHoliday(it) }

        viewModel.setSelectedPlanType(PlanType.OFFICE)
        viewModel.planDay(testDate)
        advanceUntilIdle()

        val captor = argumentCaptor<TimeBlock>()
        verify(workDayRepository).saveTimeBlock(captor.capture())

        assertThat(captor.firstValue.startTime).isEqualTo(customStartTime)
        assertThat(captor.firstValue.endTime).isEqualTo(customStartTime.plusMinutes(480))
    }

    @Test
    fun `savePlannedHours creates time block with settings defaultStartTime`() = runTest {
        val customStartTime = LocalTime.of(7, 45)
        val settings = Settings(defaultStartTime = customStartTime)
        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(workDayRepository.saveWorkDay(any())).thenReturn(20L)

        viewModel = createViewModel()
        advanceUntilIdle()

        val testDate = YearMonth.now().plusMonths(1).atDay(10)
        viewModel.savePlannedHours(testDate, 300)
        advanceUntilIdle()

        val captor = argumentCaptor<TimeBlock>()
        verify(workDayRepository).saveTimeBlock(captor.capture())

        assertThat(captor.firstValue.startTime).isEqualTo(customStartTime)
        assertThat(captor.firstValue.endTime).isEqualTo(customStartTime.plusMinutes(300))
    }

    @Test
    fun `applyStandardMonth creates time blocks with settings defaultStartTime`() = runTest {
        val customStartTime = LocalTime.of(8, 45)
        val settings = Settings(defaultStartTime = customStartTime, dailyWorkMinutes = 420)
        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(workDayRepository.saveWorkDay(any())).thenReturn(30L)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.applyStandardMonth(PlanType.OFFICE)
        advanceUntilIdle()

        val captor = argumentCaptor<TimeBlock>()
        verify(workDayRepository, atLeastOnce()).saveTimeBlock(captor.capture())

        assertThat(captor.allValues).isNotEmpty()
        assertThat(captor.allValues.all { it.startTime == customStartTime }).isTrue()
        assertThat(captor.allValues.all { it.endTime == customStartTime.plusMinutes(420) }).isTrue()
    }

    @Test
    fun `planDay with PlanType BUSINESS_TRIP saves WorkLocation OFFICE and DayType BUSINESS_TRIP`() = runTest {
        val testDate = YearMonth.now().plusMonths(1).atDay(5)
        whenever(workDayRepository.saveWorkDay(any())).thenReturn(15L)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSelectedPlanType(PlanType.BUSINESS_TRIP)
        viewModel.planDay(testDate)
        advanceUntilIdle()

        val captor = argumentCaptor<com.flex.domain.model.WorkDay>()
        verify(workDayRepository).saveWorkDay(captor.capture())

        assertThat(captor.firstValue.dayType).isEqualTo(DayType.BUSINESS_TRIP)
        assertThat(captor.firstValue.location).isEqualTo(com.flex.domain.model.WorkLocation.OFFICE)
        assertThat(captor.firstValue.isPlanned).isTrue()
    }

    @Test
    fun `getAffectedPlannedDaysCount delegates to use case with selected month`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val selectedMonth = YearMonth.now().plusMonths(1)
        whenever(syncPlannedDaysWithWorkTimeRuleUseCase.countAffectedPlannedDays(selectedMonth, selectedMonth)).thenReturn(8)

        val count = viewModel.getAffectedPlannedDaysCount()

        assertThat(count).isEqualTo(8)
        verify(syncPlannedDaysWithWorkTimeRuleUseCase).countAffectedPlannedDays(selectedMonth, selectedMonth)
    }

    @Test
    fun `syncPlannedDays calls use case syncMonth and emits undoEvent`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val selectedMonth = YearMonth.now().plusMonths(1)
        whenever(syncPlannedDaysWithWorkTimeRuleUseCase.syncMonth(selectedMonth, false, null)).thenReturn(
            SyncPlannedDaysResult(updatedDaysCount = 5, removedDaysCount = 0, untouchedCustomDaysCount = 0)
        )

        var emittedMessage: String? = null
        val job = launch(UnconfinedTestDispatcher()) {
            viewModel.undoEvent.collect { emittedMessage = it.message }
        }

        viewModel.syncPlannedDays(removeNonWorkDays = false)
        advanceUntilIdle()

        assertThat(emittedMessage).isEqualTo("5 Tage an Regel angepasst")
        verify(syncPlannedDaysWithWorkTimeRuleUseCase).syncMonth(selectedMonth, false, null)
        job.cancel()
    }

    @Test
    fun `syncPlannedDays calculates previousDailyMinutes as most frequent duration ungleich targetDailyMinutes`() = runTest {
        val selectedMonth = YearMonth.now().plusMonths(1)
        val workTimeRule = WorkTimeRule(
            id = 1L,
            validFrom = selectedMonth,
            dailyWorkMinutes = 420,
            monthlyWorkMinutes = 8400
        )
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(listOf(workTimeRule)))

        // Planned days: 3 days with 480 min (TimeBlocks), 1 day with 360 min
        val tb480 = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 0))
        val tb360 = TimeBlock(id = 2, workDayId = 4, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(14, 0))

        val day1 = WorkDay(id = 1, date = selectedMonth.atDay(1), isPlanned = true, dayType = DayType.WORK, location = WorkLocation.OFFICE, timeBlocks = listOf(tb480))
        val day2 = WorkDay(id = 2, date = selectedMonth.atDay(2), isPlanned = true, dayType = DayType.WORK, location = WorkLocation.OFFICE, timeBlocks = listOf(tb480))
        val day3 = WorkDay(id = 3, date = selectedMonth.atDay(3), isPlanned = true, dayType = DayType.WORK, location = WorkLocation.OFFICE, timeBlocks = listOf(tb480))
        val day4 = WorkDay(id = 4, date = selectedMonth.atDay(4), isPlanned = true, dayType = DayType.WORK, location = WorkLocation.OFFICE, timeBlocks = listOf(tb360))

        whenever(getMonthWorkDays(selectedMonth)).thenReturn(flowOf(listOf(day1, day2, day3, day4)))
        whenever(calculateDayWorkTime(listOf(tb480))).thenReturn(DayWorkTimeResult(480, 480, 0, false))
        whenever(calculateDayWorkTime(listOf(tb360))).thenReturn(DayWorkTimeResult(360, 360, 0, false))

        whenever(syncPlannedDaysWithWorkTimeRuleUseCase.syncMonth(selectedMonth, true, 480)).thenReturn(
            SyncPlannedDaysResult(updatedDaysCount = 3, removedDaysCount = 1, untouchedCustomDaysCount = 1)
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        var emittedMessage: String? = null
        val job = launch(UnconfinedTestDispatcher()) {
            viewModel.undoEvent.collect { emittedMessage = it.message }
        }

        viewModel.syncPlannedDays(removeNonWorkDays = true)
        advanceUntilIdle()

        // Most frequent duration ungleich target (420) is 480 (count 3 vs count 1)
        verify(syncPlannedDaysWithWorkTimeRuleUseCase).syncMonth(selectedMonth, true, 480)
        assertThat(emittedMessage).isEqualTo("3 Tage an Regel angepasst, 1 gelöscht (1 individuelle beibehalten)")
        job.cancel()
    }

    @Test
    fun `canSyncPlannedDays is false when all planned days already match rule`() = runTest {
        val selectedMonth = YearMonth.now().plusMonths(1)
        val workTimeRule = WorkTimeRule(
            id = 1L,
            validFrom = selectedMonth,
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 9600
        )
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(listOf(workTimeRule)))

        val tb480 = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 0))
        val day1 = WorkDay(id = 1, date = selectedMonth.atDay(2), isPlanned = true, dayType = DayType.WORK, location = WorkLocation.OFFICE, timeBlocks = listOf(tb480))

        whenever(getMonthWorkDays(selectedMonth)).thenReturn(flowOf(listOf(day1)))
        whenever(calculateDayWorkTime(listOf(tb480))).thenReturn(DayWorkTimeResult(480, 480, 0, false))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.canSyncPlannedDays).isFalse()
    }

    @Test
    fun `canSyncPlannedDays is false when all standard days match rule and only a single custom day exists`() = runTest {
        val selectedMonth = YearMonth.now().plusMonths(1)
        val workTimeRule = WorkTimeRule(
            id = 1L,
            validFrom = selectedMonth,
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 9600
        )
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(listOf(workTimeRule)))

        val tb480 = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 0))
        val tb240 = TimeBlock(id = 2, workDayId = 2, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0))
        val day1 = WorkDay(id = 1, date = selectedMonth.atDay(2), isPlanned = true, dayType = DayType.WORK, location = WorkLocation.OFFICE, timeBlocks = listOf(tb480))
        val day2 = WorkDay(id = 2, date = selectedMonth.atDay(3), isPlanned = true, dayType = DayType.WORK, location = WorkLocation.OFFICE, timeBlocks = listOf(tb240))

        whenever(getMonthWorkDays(selectedMonth)).thenReturn(flowOf(listOf(day1, day2)))
        whenever(calculateDayWorkTime(listOf(tb480))).thenReturn(DayWorkTimeResult(480, 480, 0, false))
        whenever(calculateDayWorkTime(listOf(tb240))).thenReturn(DayWorkTimeResult(240, 240, 0, false))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.canSyncPlannedDays).isFalse()
    }

    @Test
    fun `canSyncPlannedDays is true when planned days have outdated standard duration`() = runTest {
        val selectedMonth = YearMonth.now().plusMonths(1)
        val workTimeRule = WorkTimeRule(
            id = 1L,
            validFrom = selectedMonth,
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 9600
        )
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(listOf(workTimeRule)))

        // Day with 426 minutes (which is default Settings().dailyWorkMinutes)
        val tb426 = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(15, 6))
        val day1 = WorkDay(id = 1, date = selectedMonth.atDay(2), isPlanned = true, dayType = DayType.WORK, location = WorkLocation.OFFICE, timeBlocks = listOf(tb426))

        whenever(getMonthWorkDays(selectedMonth)).thenReturn(flowOf(listOf(day1)))
        whenever(calculateDayWorkTime(listOf(tb426))).thenReturn(DayWorkTimeResult(426, 426, 0, false))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.canSyncPlannedDays).isTrue()
    }
}

