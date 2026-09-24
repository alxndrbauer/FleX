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
import com.flex.domain.usecase.GetMonthWorkDaysUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
        whenever(buildPrognosisDays(any(), any(), any(), any())).thenAnswer { inv -> inv.getArgument(1) }
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(null)
        whenever(settingsRepository.getWorkTimeRuleForDate(any(), any())).thenReturn(null)
    }

    private fun createViewModel() = PlanningViewModel(
        getMonthWorkDays, getSettings, workDayRepository, settingsRepository,
        calculateDayWorkTime, calculateQuota, calculateFlextime, buildPrognosisDays
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
}
