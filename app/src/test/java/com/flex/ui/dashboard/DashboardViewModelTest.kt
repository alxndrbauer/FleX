package com.flex.ui.dashboard

import com.flex.BaseUnitTest
import com.flex.MainDispatcherExtension
import com.flex.domain.model.DayType
import com.flex.domain.model.FlextimeBalance
import com.flex.domain.model.QuotaRule
import com.flex.domain.model.QuotaStatus
import com.flex.domain.model.Settings
import com.flex.domain.model.WorkDay
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.GetMonthWorkDaysUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import com.google.common.truth.Truth.assertThat
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
import java.time.YearMonth

@ExtendWith(MainDispatcherExtension::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest : BaseUnitTest() {

    @Mock
    private lateinit var getMonthWorkDays: GetMonthWorkDaysUseCase

    @Mock
    private lateinit var getSettings: GetSettingsUseCase

    @Mock
    private lateinit var workDayRepository: WorkDayRepository

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    @Mock
    private lateinit var calculateQuota: CalculateQuotaUseCase

    @Mock
    private lateinit var calculateFlextime: CalculateFlextimeUseCase

    private lateinit var viewModel: DashboardViewModel

    @BeforeEach
    override fun setUp() {
        super.setUp()
        whenever(getMonthWorkDays(any())).thenReturn(flowOf(emptyList()))
        whenever(getSettings()).thenReturn(flowOf(Settings()))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(emptyList()))
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(emptyList()))
        whenever(workDayRepository.getWorkDaysForYear(any())).thenReturn(flowOf(emptyList()))
        whenever(calculateQuota(any(), any(), any(), any(), any(), any())).thenReturn(QuotaStatus())
        whenever(calculateFlextime(any(), any(), any(), any())).thenReturn(FlextimeBalance())
    }

    private fun createViewModel(): DashboardViewModel {
        return DashboardViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateQuota, calculateFlextime
        )
    }

    @Test
    fun `initial state uses current year and current yearMonth`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val currentYear = LocalDate.now().year
        val currentYearMonth = YearMonth.now()

        assertThat(viewModel.uiState.value.selectedYear).isEqualTo(currentYear)
        assertThat(viewModel.uiState.value.selectedYearMonth).isEqualTo(currentYearMonth)
    }

    @Test
    fun `previousYear decrements selectedYear and updates selectedYearMonth`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val initialYear = viewModel.uiState.value.selectedYear
        val initialMonth = viewModel.uiState.value.selectedYearMonth.monthValue

        viewModel.previousYear()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedYear).isEqualTo(initialYear - 1)
        assertThat(viewModel.uiState.value.selectedYearMonth).isEqualTo(YearMonth.of(initialYear - 1, initialMonth))
    }

    @Test
    fun `nextYear increments selectedYear and updates selectedYearMonth`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val initialYear = viewModel.uiState.value.selectedYear
        val initialMonth = viewModel.uiState.value.selectedYearMonth.monthValue

        viewModel.nextYear()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedYear).isEqualTo(initialYear + 1)
        assertThat(viewModel.uiState.value.selectedYearMonth).isEqualTo(YearMonth.of(initialYear + 1, initialMonth))
    }

    @Test
    fun `selectYear sets selectedYear directly`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val initialMonth = viewModel.uiState.value.selectedYearMonth.monthValue

        viewModel.selectYear(2025)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedYear).isEqualTo(2025)
        assertThat(viewModel.uiState.value.selectedYearMonth).isEqualTo(YearMonth.of(2025, initialMonth))
    }

    @Test
    fun `previousMonth decrements month and synchronizes year across year boundary`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectMonth(YearMonth.of(2026, 1))
        advanceUntilIdle()

        viewModel.previousMonth()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedYearMonth).isEqualTo(YearMonth.of(2025, 12))
        assertThat(viewModel.uiState.value.selectedYear).isEqualTo(2025)
    }

    @Test
    fun `nextMonth increments month and synchronizes year across year boundary`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectMonth(YearMonth.of(2025, 12))
        advanceUntilIdle()

        viewModel.nextMonth()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedYearMonth).isEqualTo(YearMonth.of(2026, 1))
        assertThat(viewModel.uiState.value.selectedYear).isEqualTo(2026)
    }

    @Test
    fun `selectMonth updates selectedYear when month belongs to different year`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectMonth(YearMonth.of(2024, 6))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedYearMonth).isEqualTo(YearMonth.of(2024, 6))
        assertThat(viewModel.uiState.value.selectedYear).isEqualTo(2024)
    }

    @Test
    fun `switching year reloads work days for that year`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectYear(2025)
        advanceUntilIdle()

        verify(workDayRepository).getWorkDaysForYear(2025)
    }

    @Test
    fun `selectFlextimePeriod switches between MONTH and YEAR`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedFlextimePeriod).isEqualTo(com.flex.domain.model.FlextimeChartPeriod.MONTH)

        viewModel.selectFlextimePeriod(com.flex.domain.model.FlextimeChartPeriod.YEAR)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedFlextimePeriod).isEqualTo(com.flex.domain.model.FlextimeChartPeriod.YEAR)

        viewModel.selectFlextimePeriod(com.flex.domain.model.FlextimeChartPeriod.MONTH)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedFlextimePeriod).isEqualTo(com.flex.domain.model.FlextimeChartPeriod.MONTH)
    }

    @Test
    fun `flextime series are calculated for month and year when work days are present`() = runTest {
        val testDate = LocalDate.of(2026, 10, 5)
        val workDay = WorkDay(
            date = testDate,
            dayType = DayType.WORK,
            timeBlocks = listOf(
                com.flex.domain.model.TimeBlock(
                    startTime = java.time.LocalTime.of(8, 0),
                    endTime = java.time.LocalTime.of(17, 0),
                    location = com.flex.domain.model.WorkLocation.OFFICE
                )
            ),
            isPlanned = false
        )
        whenever(getMonthWorkDays(YearMonth.of(2026, 10))).thenReturn(flowOf(listOf(workDay)))
        whenever(workDayRepository.getWorkDaysForYear(2026)).thenReturn(flowOf(listOf(workDay)))

        viewModel = createViewModel()
        viewModel.selectYear(2026)
        viewModel.selectMonth(YearMonth.of(2026, 10))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.monthFlextimeSeries).isNotEmpty()
        assertThat(viewModel.uiState.value.yearFlextimeSeries).isNotEmpty()
    }

    @Test
    fun `quotaStatus reflects actual days and projectedQuotaStatus includes planned days`() = runTest {
        val actualDay = WorkDay(
            date = LocalDate.of(2026, 10, 5),
            dayType = DayType.WORK,
            isPlanned = false
        )
        val plannedDay = WorkDay(
            date = LocalDate.of(2026, 10, 15),
            dayType = DayType.WORK,
            location = com.flex.domain.model.WorkLocation.OFFICE,
            isPlanned = true
        )
        val actualQuota = QuotaStatus(officeDays = 1)
        val projectedQuota = QuotaStatus(officeDays = 2)

        whenever(getMonthWorkDays(YearMonth.of(2026, 10))).thenReturn(flowOf(listOf(actualDay, plannedDay)))
        whenever(calculateQuota(org.mockito.kotlin.argThat { size == 1 && none { it.isPlanned } }, any(), any(), any(), any(), any())).thenReturn(actualQuota)
        whenever(calculateQuota(org.mockito.kotlin.argThat { size == 2 }, any(), any(), any(), any(), any())).thenReturn(projectedQuota)

        viewModel = createViewModel()
        viewModel.selectYear(2026)
        viewModel.selectMonth(YearMonth.of(2026, 10))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.quotaStatus.officeDays).isEqualTo(1)
        assertThat(viewModel.uiState.value.projectedQuotaStatus.officeDays).isEqualTo(2)
        assertThat(viewModel.uiState.value.forecast?.plannedOfficeDays).isEqualTo(1)
    }

    @Test
    fun `calculateFlextime and currentWeekStatus receive workTimeRules when available`() = runTest {
        val rule = com.flex.domain.model.WorkTimeRule(
            id = 1,
            validFrom = YearMonth.of(2026, 10),
            dailyWorkMinutes = 324, // 5h 24m
            monthlyWorkMinutes = 7047
        )
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(listOf(rule)))

        viewModel = createViewModel()
        advanceUntilIdle()

        verify(calculateFlextime).invoke(any(), any(), any(), org.mockito.kotlin.eq(listOf(rule)))
        val currentWeek = viewModel.uiState.value.currentWeekStatus
        assertThat(currentWeek).isNotNull()
        assertThat(currentWeek?.targetMinutes).isEqualTo(5 * 324L)
    }
}
