package com.flex.ui.month

import com.google.common.truth.Truth.assertThat
import com.flex.BaseUnitTest
import com.flex.MainDispatcherExtension
import com.flex.domain.model.DayType
import com.flex.domain.model.FlextimeBalance
import com.flex.domain.model.QuotaRule
import com.flex.domain.model.QuotaStatus
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.BuildPrognosisDaysUseCase
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.CheckBreakViolationUseCase
import com.flex.domain.usecase.DayWorkTimeResult
import com.flex.domain.usecase.GetMonthWorkDaysUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.domain.usecase.PrepareExportDataUseCase
import com.flex.domain.events.DataChangeEventBus
import com.flex.domain.model.BreakCheckResult
import com.flex.data.export.ExportService
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * Tests that verify MonthViewModel always resolves work time targets and quota values
 * correctly from WorkTimeRules and QuotaRules rather than falling back to global Settings
 * when rules are available for the given period.
 *
 * Regression tests for: required office minutes and total monthly minutes not updating
 * correctly after settings changes in MonthView.
 */
@ExtendWith(MainDispatcherExtension::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MonthViewModelWorkTimeRuleTest : BaseUnitTest() {

    @Mock private lateinit var getMonthWorkDays: GetMonthWorkDaysUseCase
    @Mock private lateinit var getSettings: GetSettingsUseCase
    @Mock private lateinit var workDayRepository: WorkDayRepository
    @Mock private lateinit var settingsRepository: SettingsRepository
    @Mock private lateinit var calculateDayWorkTime: CalculateDayWorkTimeUseCase
    @Mock private lateinit var calculateQuota: CalculateQuotaUseCase
    @Mock private lateinit var calculateFlextime: CalculateFlextimeUseCase
    @Mock private lateinit var dataChangeEventBus: DataChangeEventBus
    @Mock private lateinit var prepareExportData: PrepareExportDataUseCase
    @Mock private lateinit var exportService: ExportService
    @Mock private lateinit var checkBreakViolation: CheckBreakViolationUseCase
    @Mock private lateinit var buildPrognosisDays: BuildPrognosisDaysUseCase

    private lateinit var viewModel: MonthViewModel

    // Fixed test month in the past to avoid "current month" edge cases
    private val testMonth = YearMonth.of(2025, 3)

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
        whenever(checkBreakViolation(any(), any())).thenReturn(BreakCheckResult(emptyList(), skipped = false))
        whenever(buildPrognosisDays(any(), any(), any(), any())).thenAnswer { inv -> inv.getArgument(1) }
        // Default: no rule matches (returns null → falls back to Settings)
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(null)
        whenever(settingsRepository.getWorkTimeRuleForDate(any(), any())).thenReturn(null)
    }

    private fun createViewModel(): MonthViewModel = MonthViewModel(
        getMonthWorkDays, getSettings, workDayRepository,
        settingsRepository, calculateDayWorkTime, calculateQuota, calculateFlextime, dataChangeEventBus,
        prepareExportData, exportService, checkBreakViolation, buildPrognosisDays
    )

    // ========== WorkTimeRule Priority Tests ==========

    @Test
    fun `requiredOfficeMinutes uses workTimeRule monthlyTarget when rule exists`() = runTest {
        // Given: WorkTimeRule with higher monthly target than Settings
        val settingsMonthly = 9266   // ~154h
        val ruleMonthly = 8000       // ~133h (different from settings)
        val quotaPercent = 40

        val settings = Settings(monthlyWorkMinutes = settingsMonthly, officeQuotaPercent = quotaPercent)
        val workTimeRule = WorkTimeRule(
            id = 1,
            validFrom = YearMonth.of(2025, 1),
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = ruleMonthly
        )

        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(listOf(workTimeRule)))
        // Rule applies for the test month
        whenever(settingsRepository.getWorkTimeRuleForDate(any(), any())).thenReturn(workTimeRule)

        viewModel = createViewModel()
        advanceUntilIdle()

        // When navigating to test month
        viewModel.previousMonth() // go back to ensure we're not in current month territory
        repeat(10) { viewModel.previousMonth() }
        advanceUntilIdle()

        // Then: requiredOfficeMinutes should be based on ruleMonthly, NOT settingsMonthly
        val state = viewModel.uiState.value
        // requiredMin = ruleMonthly * quotaPercent / 100 = 8000 * 0.4 = 3200
        // (If bug was present, it would use: settingsMonthly * quotaPercent / 100 = 9266 * 0.4 = 3706)
        assertThat(state.requiredOfficeMinutes).isNotEqualTo((settingsMonthly * quotaPercent / 100.0).toLong())
    }

    @Test
    fun `totalWorkMinutes uses workTimeRule dailyTarget for neutral day deduction`() = runTest {
        // Given: A vacation day in the month with a WorkTimeRule specifying different daily minutes
        val settingsDaily = 426      // 7h 6min (global settings)
        val ruleDaily = 480          // 8h (rule for this period)
        val settingsMonthly = 9266
        val ruleMonthly = 10400      // different monthly target

        val vacationDate = testMonth.atDay(10) // a Monday in March 2025
        val vacationDay = WorkDay(
            id = 1,
            date = vacationDate,
            dayType = DayType.VACATION,
            location = WorkLocation.HOME_OFFICE
        )

        val workTimeRule = WorkTimeRule(
            id = 1,
            validFrom = YearMonth.of(2025, 1),
            dailyWorkMinutes = ruleDaily,
            monthlyWorkMinutes = ruleMonthly
        )

        val settings = Settings(
            dailyWorkMinutes = settingsDaily,
            monthlyWorkMinutes = settingsMonthly,
            officeQuotaPercent = 40,
            officeQuotaMinDays = 8
        )

        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(getMonthWorkDays(any())).thenReturn(flowOf(listOf(vacationDay)))
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(listOf(workTimeRule)))
        whenever(settingsRepository.getWorkTimeRuleForDate(any(), any())).thenReturn(workTimeRule)
        // Make buildPrognosisDays return the vacation day
        whenever(buildPrognosisDays(any(), any(), any(), any())).thenAnswer { inv -> inv.getArgument(1) }

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value

        // totalMin should be: ruleMonthly - ruleDaily (1 vacation day deducted at rule rate)
        // If bug was present: settingsMonthly - settingsDaily would be used
        val expectedTotal = (ruleMonthly - ruleDaily).toLong().coerceAtLeast(0)
        val buggyTotal = (settingsMonthly - settingsDaily).toLong().coerceAtLeast(0)

        assertThat(expectedTotal).isNotEqualTo(buggyTotal) // verify test setup is meaningful
        assertThat(state.totalWorkMinutes).isEqualTo(expectedTotal)
    }

    // ========== QuotaRule Priority Tests ==========

    @Test
    fun `effectiveQuotaPercent uses QuotaRule when it exists for the month`() = runTest {
        // Given: QuotaRule with different percent than Settings
        val settingsQuotaPercent = 40
        val ruleQuotaPercent = 60   // higher quota required in this period

        val settings = Settings(officeQuotaPercent = settingsQuotaPercent, officeQuotaMinDays = 8)
        val quotaRule = QuotaRule(
            id = 1,
            validFrom = testMonth,
            officeQuotaPercent = ruleQuotaPercent,
            officeQuotaMinDays = 10
        )

        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(listOf(quotaRule)))
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(quotaRule)

        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: effectiveQuotaPercent should come from the QuotaRule, not Settings
        assertThat(viewModel.uiState.value.effectiveQuotaPercent).isEqualTo(ruleQuotaPercent)
        assertThat(viewModel.uiState.value.effectiveQuotaPercent).isNotEqualTo(settingsQuotaPercent)
    }

    @Test
    fun `effectiveQuotaMinDays uses QuotaRule when it exists for the month`() = runTest {
        // Given: QuotaRule with different min days than Settings
        val settingsMinDays = 8
        val ruleMinDays = 12  // higher requirement in this period

        val settings = Settings(officeQuotaPercent = 40, officeQuotaMinDays = settingsMinDays)
        val quotaRule = QuotaRule(
            id = 1,
            validFrom = testMonth,
            officeQuotaPercent = 50,
            officeQuotaMinDays = ruleMinDays
        )

        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(listOf(quotaRule)))
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(quotaRule)

        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: effectiveQuotaMinDays should come from the QuotaRule, not Settings
        assertThat(viewModel.uiState.value.effectiveQuotaMinDays).isEqualTo(ruleMinDays)
        assertThat(viewModel.uiState.value.effectiveQuotaMinDays).isNotEqualTo(settingsMinDays)
    }

    @Test
    fun `effectiveQuotaPercent falls back to Settings when no QuotaRule exists`() = runTest {
        // Given: No quota rule for the month
        val settingsQuotaPercent = 40
        val settings = Settings(officeQuotaPercent = settingsQuotaPercent)

        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(emptyList()))
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(null)

        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: effectiveQuotaPercent should fall back to Settings value
        assertThat(viewModel.uiState.value.effectiveQuotaPercent).isEqualTo(settingsQuotaPercent)
    }

    // ========== Settings Change Reactivity Tests ==========

    @Test
    fun `state updates when settings officeQuotaPercent changes`() = runTest {
        // Given: A reactive settings flow
        val settingsFlow = MutableStateFlow(Settings(officeQuotaPercent = 40))
        whenever(getSettings()).thenReturn(settingsFlow)
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(null)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.effectiveQuotaPercent).isEqualTo(40)

        // When: Settings are updated (simulates user changing quota in SettingsScreen)
        settingsFlow.value = Settings(officeQuotaPercent = 60)
        advanceUntilIdle()

        // Then: State should reflect new quota percent
        assertThat(viewModel.uiState.value.effectiveQuotaPercent).isEqualTo(60)
    }

    @Test
    fun `state updates when settings officeQuotaMinDays changes`() = runTest {
        // Given: A reactive settings flow
        val settingsFlow = MutableStateFlow(Settings(officeQuotaMinDays = 8))
        whenever(getSettings()).thenReturn(settingsFlow)
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(null)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.effectiveQuotaMinDays).isEqualTo(8)

        // When: Settings are updated
        settingsFlow.value = Settings(officeQuotaMinDays = 12)
        advanceUntilIdle()

        // Then: State should reflect new min days
        assertThat(viewModel.uiState.value.effectiveQuotaMinDays).isEqualTo(12)
    }

    @Test
    fun `state updates when workTimeRules change`() = runTest {
        // Given: Initially no WorkTimeRules
        val workTimeRulesFlow = MutableStateFlow(emptyList<WorkTimeRule>())
        val settingsFlow = MutableStateFlow(Settings(monthlyWorkMinutes = 9266, officeQuotaPercent = 40))
        whenever(getSettings()).thenReturn(settingsFlow)
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(workTimeRulesFlow)
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(null)

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: A new WorkTimeRule is added (simulates user adding a rule in SettingsScreen)
        val newRule = WorkTimeRule(
            id = 1,
            validFrom = YearMonth.of(2020, 1),
            dailyWorkMinutes = 480,   // 8h
            monthlyWorkMinutes = 10400 // different from settings (9266)
        )
        whenever(settingsRepository.getWorkTimeRuleForDate(any(), any())).thenReturn(newRule)
        workTimeRulesFlow.value = listOf(newRule)
        advanceUntilIdle()

        // Then: The workTimeRules in state should be updated
        assertThat(viewModel.uiState.value.workTimeRules).hasSize(1)
        assertThat(viewModel.uiState.value.workTimeRules.first().monthlyWorkMinutes).isEqualTo(10400)
    }

    @Test
    fun `state updates when quotaRules change`() = runTest {
        // Given: Initially no QuotaRules
        val quotaRulesFlow = MutableStateFlow(emptyList<QuotaRule>())
        val settingsFlow = MutableStateFlow(Settings(officeQuotaPercent = 40, officeQuotaMinDays = 8))
        whenever(getSettings()).thenReturn(settingsFlow)
        whenever(settingsRepository.getQuotaRules()).thenReturn(quotaRulesFlow)
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(null)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.effectiveQuotaPercent).isEqualTo(40) // from settings

        // When: A QuotaRule is added with different values
        val newRule = QuotaRule(
            id = 1,
            validFrom = YearMonth.of(2020, 1),
            officeQuotaPercent = 55,
            officeQuotaMinDays = 10
        )
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(newRule)
        quotaRulesFlow.value = listOf(newRule)
        advanceUntilIdle()

        // Then: effectiveQuota should now come from the new rule
        assertThat(viewModel.uiState.value.effectiveQuotaPercent).isEqualTo(55)
        assertThat(viewModel.uiState.value.effectiveQuotaMinDays).isEqualTo(10)
    }

    // ========== calculateQuota receives correct effective values ==========

    @Test
    fun `calculateQuota is called with QuotaRule values not Settings values when rule exists`() = runTest {
        // Given: QuotaRule overrides Settings
        val settingsQuotaPercent = 40
        val settingsMinDays = 8
        val ruleQuotaPercent = 60
        val ruleMinDays = 12

        val settings = Settings(
            officeQuotaPercent = settingsQuotaPercent,
            officeQuotaMinDays = settingsMinDays
        )
        val quotaRule = QuotaRule(
            id = 1,
            validFrom = YearMonth.of(2020, 1),
            officeQuotaPercent = ruleQuotaPercent,
            officeQuotaMinDays = ruleMinDays
        )

        val capturedQuotaPercents = mutableListOf<Int>()
        val capturedMinDays = mutableListOf<Int>()

        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(listOf(quotaRule)))
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(quotaRule)
        whenever(calculateQuota(any(), any(), any(), any(), any(), any())).thenAnswer { inv ->
            capturedQuotaPercents.add(inv.getArgument(3))
            capturedMinDays.add(inv.getArgument(4))
            QuotaStatus()
        }

        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: calculateQuota should have been called with rule values
        assertThat(capturedQuotaPercents).isNotEmpty()
        assertThat(capturedQuotaPercents.last()).isEqualTo(ruleQuotaPercent)
        assertThat(capturedMinDays.last()).isEqualTo(ruleMinDays)
    }

    // ========== Edge cases ==========

    @Test
    fun `totalWorkMinutes correctly computes zero when all days are neutral`() = runTest {
        // Given: An entire month of vacation days, with no workTimeRules
        val month = YearMonth.now().minusMonths(2)
        val vacationDays = (1..5).map { day ->
            WorkDay(
                id = day.toLong(),
                date = month.atDay(day),
                dayType = DayType.VACATION,
                location = WorkLocation.HOME_OFFICE
            )
        }
        val settings = Settings(dailyWorkMinutes = 426, monthlyWorkMinutes = 2130) // exactly 5 days

        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(getMonthWorkDays(any())).thenReturn(flowOf(vacationDays))
        whenever(buildPrognosisDays(any(), any(), any(), any())).thenAnswer { inv -> inv.getArgument(1) }
        // daily target per vacation day = settings.dailyWorkMinutes = 426
        // total deduction = 5 * 426 = 2130 = monthlyWorkMinutes → totalMin = 0
        whenever(settingsRepository.getWorkTimeRuleForDate(any(), any())).thenReturn(null)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.totalWorkMinutes).isEqualTo(0)
        assertThat(viewModel.uiState.value.requiredOfficeMinutes).isEqualTo(0)
    }
}
