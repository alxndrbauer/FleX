package com.flex.ui.planning

import com.google.common.truth.Truth.assertThat
import com.flex.BaseUnitTest
import com.flex.MainDispatcherExtension
import com.flex.domain.model.DayType
import com.flex.domain.model.FlextimeBalance
import com.flex.domain.model.QuotaRule
import com.flex.domain.model.QuotaStatus
import com.flex.domain.model.Settings
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.model.WorkTimeRule
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.YearMonth

/**
 * Tests that verify PlanningViewModel resolves work time targets and quota values
 * correctly from WorkTimeRules and QuotaRules, and that the UI updates reactively
 * when settings or rules change.
 *
 * Regression tests for: office hours requirements (geforderte Bürozeit)
 * in PlanView not updating after settings changes.
 */
@ExtendWith(MainDispatcherExtension::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PlanningViewModelWorkTimeRuleTest : BaseUnitTest() {

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

    // ========== QuotaRule Priority Tests ==========

    @Test
    fun `officeHours requiredOfficeMinutes uses QuotaRule quota percent when rule exists`() = runTest {
        // Given: A QuotaRule that overrides global settings
        val settingsPercent = 40
        val rulePercent = 65
        val monthlyTarget = 10000L

        val settings = Settings(
            officeQuotaPercent = settingsPercent,
            officeQuotaMinDays = 8,
            monthlyWorkMinutes = monthlyTarget.toInt()
        )
        val quotaRule = QuotaRule(
            id = 1,
            validFrom = YearMonth.of(2020, 1),
            officeQuotaPercent = rulePercent,
            officeQuotaMinDays = 10
        )

        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(listOf(quotaRule)))
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(quotaRule)

        // Office day planned for next month (default selected month in Planning is next month)
        val nextMonth = YearMonth.now().plusMonths(1)
        val officeDay = WorkDay(
            id = 1,
            date = nextMonth.atDay(1),
            dayType = DayType.WORK,
            location = WorkLocation.OFFICE
        )
        whenever(getMonthWorkDays(any())).thenReturn(flowOf(listOf(officeDay)))
        whenever(buildPrognosisDays(any(), any(), any(), any())).thenAnswer { inv -> inv.getArgument(1) }
        whenever(calculateDayWorkTime(any())).thenReturn(DayWorkTimeResult(
            grossMinutes = 480, netMinutes = 480, breakMinutes = 0, exceedsMaxHours = false
        ))

        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: requiredOfficeMinutes = monthlyTarget * rulePercent / 100 = 10000 * 0.65 = 6500
        // Bug would give: monthlyTarget * settingsPercent / 100 = 10000 * 0.40 = 4000
        val state = viewModel.uiState.value
        val expectedRequired = (monthlyTarget * rulePercent / 100.0).toLong()
        val buggyRequired = (monthlyTarget * settingsPercent / 100.0).toLong()

        assertThat(expectedRequired).isNotEqualTo(buggyRequired)
        assertThat(state.officeHours.requiredOfficeMinutes).isEqualTo(expectedRequired)
    }

    @Test
    fun `officeHours targetMonthlyMinutes uses WorkTimeRule when rule exists`() = runTest {
        // Given: WorkTimeRule with monthlyWorkMinutes different from settings
        val settingsMonthly = 9266
        val ruleMonthly = 8000

        val settings = Settings(monthlyWorkMinutes = settingsMonthly, officeQuotaPercent = 40)
        val workTimeRule = WorkTimeRule(
            id = 1,
            validFrom = YearMonth.of(2020, 1),
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = ruleMonthly
        )

        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(listOf(workTimeRule)))
        whenever(settingsRepository.getWorkTimeRuleForDate(any(), any())).thenReturn(workTimeRule)

        viewModel = createViewModel()
        advanceUntilIdle()

        // targetMonthlyMinutes in OfficeHoursDetail should use the rule's monthly target
        // The base monthly target comes from workTimeRules.getRuleForMonth(ym) or settings.monthlyWorkMinutes
        assertThat(viewModel.uiState.value.officeHours.targetMonthlyMinutes).isEqualTo(ruleMonthly.toLong())
    }

    // ========== Settings Change Reactivity Tests ==========

    @Test
    fun `officeHours updates when settings officeQuotaPercent changes`() = runTest {
        // Given: Reactive settings flow
        val settingsFlow = MutableStateFlow(
            Settings(officeQuotaPercent = 40, monthlyWorkMinutes = 10000)
        )
        whenever(getSettings()).thenReturn(settingsFlow)
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(null)

        viewModel = createViewModel()
        advanceUntilIdle()

        // Initial: requiredOfficeMinutes = 10000 * 0.40 = 4000
        assertThat(viewModel.uiState.value.officeHours.requiredOfficeMinutes).isEqualTo(4000L)

        // When: User changes quota percent in settings
        settingsFlow.value = Settings(officeQuotaPercent = 60, monthlyWorkMinutes = 10000)
        advanceUntilIdle()

        // Then: requiredOfficeMinutes should update to 10000 * 0.60 = 6000
        assertThat(viewModel.uiState.value.officeHours.requiredOfficeMinutes).isEqualTo(6000L)
    }

    @Test
    fun `officeHours updates when settings monthlyWorkMinutes changes`() = runTest {
        // Given: Reactive settings flow
        val settingsFlow = MutableStateFlow(
            Settings(officeQuotaPercent = 40, monthlyWorkMinutes = 10000)
        )
        whenever(getSettings()).thenReturn(settingsFlow)
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(null)

        viewModel = createViewModel()
        advanceUntilIdle()

        // Initial: requiredOfficeMinutes = 10000 * 0.40 = 4000
        assertThat(viewModel.uiState.value.officeHours.requiredOfficeMinutes).isEqualTo(4000L)

        // When: User changes monthly work minutes in settings
        settingsFlow.value = Settings(officeQuotaPercent = 40, monthlyWorkMinutes = 8000)
        advanceUntilIdle()

        // Then: requiredOfficeMinutes should update to 8000 * 0.40 = 3200
        assertThat(viewModel.uiState.value.officeHours.requiredOfficeMinutes).isEqualTo(3200L)
    }

    @Test
    fun `settings change triggers recalculation of quota and officeHours`() = runTest {
        // Given: Reactive settings and quota rules flows
        val settingsFlow = MutableStateFlow(Settings(officeQuotaPercent = 40, officeQuotaMinDays = 8))
        val quotaRulesFlow = MutableStateFlow(emptyList<QuotaRule>())
        whenever(getSettings()).thenReturn(settingsFlow)
        whenever(settingsRepository.getQuotaRules()).thenReturn(quotaRulesFlow)
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(null)

        var calculateQuotaCallCount = 0
        whenever(calculateQuota(any(), any(), any(), any(), any(), any())).thenAnswer {
            calculateQuotaCallCount++
            QuotaStatus()
        }

        viewModel = createViewModel()
        advanceUntilIdle()
        val initialCallCount = calculateQuotaCallCount

        // When: Settings change (user edits quota in settings screen)
        settingsFlow.value = Settings(officeQuotaPercent = 60, officeQuotaMinDays = 12)
        advanceUntilIdle()

        // Then: calculateQuota should have been called again
        assertThat(calculateQuotaCallCount).isGreaterThan(initialCallCount)
    }

    @Test
    fun `workTimeRules change triggers recalculation`() = runTest {
        // Given: Initially no work time rules
        val workTimeRulesFlow = MutableStateFlow(emptyList<WorkTimeRule>())
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(workTimeRulesFlow)

        var calculateQuotaCallCount = 0
        whenever(calculateQuota(any(), any(), any(), any(), any(), any())).thenAnswer {
            calculateQuotaCallCount++
            QuotaStatus()
        }

        viewModel = createViewModel()
        advanceUntilIdle()
        val initialCallCount = calculateQuotaCallCount

        // When: A new WorkTimeRule is added
        val newRule = WorkTimeRule(
            id = 1,
            validFrom = YearMonth.of(2020, 1),
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 10400
        )
        whenever(settingsRepository.getWorkTimeRuleForDate(any(), any())).thenReturn(newRule)
        workTimeRulesFlow.value = listOf(newRule)
        advanceUntilIdle()

        // Then: calculateQuota should have been called again with new rules
        assertThat(calculateQuotaCallCount).isGreaterThan(initialCallCount)
    }

    // ========== isMet correctly updates on settings change ==========

    @Test
    fun `officeHours isMet reflects correct required minutes after settings change`() = runTest {
        // Given: Planning month with one full office day (480 min)
        val nextMonth = YearMonth.now().plusMonths(1)
        val officeDay = WorkDay(
            id = 1,
            date = nextMonth.atDay(1),
            dayType = DayType.WORK,
            location = WorkLocation.OFFICE
        )

        val settingsFlow = MutableStateFlow(Settings(officeQuotaPercent = 40, monthlyWorkMinutes = 1000))
        whenever(getSettings()).thenReturn(settingsFlow)
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(null)
        whenever(getMonthWorkDays(any())).thenReturn(flowOf(listOf(officeDay)))
        whenever(buildPrognosisDays(any(), any(), any(), any())).thenAnswer { inv -> inv.getArgument(1) }
        whenever(calculateDayWorkTime(any())).thenReturn(
            DayWorkTimeResult(grossMinutes = 480, netMinutes = 480, breakMinutes = 0, exceedsMaxHours = false)
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        // Initial: required = 1000 * 0.40 = 400 min; planned = 480 min → isMet = true
        assertThat(viewModel.uiState.value.officeHours.isMet).isTrue()

        // When: Quota percent increased so requirement now exceeds what's planned
        settingsFlow.value = Settings(officeQuotaPercent = 60, monthlyWorkMinutes = 1000)
        advanceUntilIdle()

        // Then: required = 1000 * 0.60 = 600 min; planned = 480 min → isMet = false
        assertThat(viewModel.uiState.value.officeHours.isMet).isFalse()
    }
}
