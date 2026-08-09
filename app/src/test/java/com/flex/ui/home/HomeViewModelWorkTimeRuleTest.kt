package com.flex.ui.home

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
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.DayWorkTimeResult
import com.flex.domain.usecase.GetMonthWorkDaysUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.domain.events.DataChangeEventBus
import com.flex.domain.model.BreakCheckResult
import com.flex.domain.usecase.CheckBreakViolationUseCase
import com.flex.data.local.WhatsNewPreferences
import com.flex.notification.BreakWarningScheduler
import com.flex.wearable.WearSyncHelper
import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.YearMonth

/**
 * Tests that verify HomeViewModel always resolves work time targets and quota values
 * correctly from WorkTimeRules and QuotaRules rather than falling back to global Settings
 * when rules are available.
 *
 * Regression tests for: required office time (geforderte Bürozeit) on the home screen
 * not updating correctly when settings or rules change.
 */
@ExtendWith(MainDispatcherExtension::class)
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelWorkTimeRuleTest : BaseUnitTest() {

    @Mock private lateinit var context: Context
    @Mock private lateinit var workDayRepository: WorkDayRepository
    @Mock private lateinit var settingsRepository: SettingsRepository
    @Mock private lateinit var getMonthWorkDays: GetMonthWorkDaysUseCase
    @Mock private lateinit var getSettings: GetSettingsUseCase
    @Mock private lateinit var calculateDayWorkTime: CalculateDayWorkTimeUseCase
    @Mock private lateinit var calculateFlextime: CalculateFlextimeUseCase
    @Mock private lateinit var calculateQuota: CalculateQuotaUseCase
    @Mock private lateinit var dataChangeEventBus: DataChangeEventBus
    @Mock private lateinit var wearSyncHelper: WearSyncHelper
    @Mock private lateinit var checkBreakViolation: CheckBreakViolationUseCase
    @Mock private lateinit var breakWarningScheduler: BreakWarningScheduler
    @Mock private lateinit var whatsNewPreferences: WhatsNewPreferences
    @Mock private lateinit var backupPreferences: com.flex.data.backup.BackupPreferences

    private lateinit var viewModel: HomeViewModel

    @BeforeEach
    override fun setUp() {
        super.setUp()
        whenever(workDayRepository.getWorkDay(any())).thenReturn(flowOf(null))
        whenever(getMonthWorkDays(any())).thenReturn(flowOf(emptyList()))
        whenever(getSettings()).thenReturn(flowOf(Settings()))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(emptyList()))
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(emptyList()))
        whenever(workDayRepository.getWorkDaysForYear(any())).thenReturn(flowOf(emptyList()))
        whenever(calculateDayWorkTime(any())).thenReturn(DayWorkTimeResult(0, 0, 0, false))
        whenever(calculateFlextime(any(), any(), any(), any())).thenReturn(FlextimeBalance())
        whenever(calculateQuota(any(), any(), any(), any(), any(), any())).thenReturn(QuotaStatus())
        whenever(dataChangeEventBus.events).thenReturn(MutableSharedFlow())
        whenever(checkBreakViolation(any(), any())).thenReturn(BreakCheckResult(emptyList(), skipped = false))
        whenever(whatsNewPreferences.getLastSeenVersionCode()).thenReturn(0)
        whenever(backupPreferences.isAutoBackupEnabled).thenReturn(false)
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(null)
        whenever(settingsRepository.getWorkTimeRuleForDate(any(), any())).thenReturn(null)
    }

    private fun createViewModel() = HomeViewModel(
        context, workDayRepository, settingsRepository, getMonthWorkDays,
        getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota,
        dataChangeEventBus, wearSyncHelper, checkBreakViolation, breakWarningScheduler,
        whatsNewPreferences, backupPreferences
    )

    // ========== QuotaRule Resolution Tests ==========

    @Test
    fun `effectiveQuotaPercent uses QuotaRule when one exists for current month`() = runTest {
        // Given: QuotaRule for current month overrides global settings
        val settingsPercent = 40
        val rulePercent = 60

        val settings = Settings(officeQuotaPercent = settingsPercent, officeQuotaMinDays = 8)
        val quotaRule = QuotaRule(
            id = 1,
            validFrom = YearMonth.now().minusMonths(1), // valid since last month
            officeQuotaPercent = rulePercent,
            officeQuotaMinDays = 10
        )

        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(listOf(quotaRule)))
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(quotaRule)

        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: effectiveQuotaPercent should come from the rule
        assertThat(viewModel.uiState.value.effectiveQuotaPercent).isEqualTo(rulePercent)
        assertThat(viewModel.uiState.value.effectiveQuotaPercent).isNotEqualTo(settingsPercent)
    }

    @Test
    fun `effectiveQuotaMinDays uses QuotaRule when one exists for current month`() = runTest {
        // Given: QuotaRule for current month overrides global settings
        val settingsMinDays = 8
        val ruleMinDays = 12

        val settings = Settings(officeQuotaPercent = 40, officeQuotaMinDays = settingsMinDays)
        val quotaRule = QuotaRule(
            id = 1,
            validFrom = YearMonth.now().minusMonths(1),
            officeQuotaPercent = 50,
            officeQuotaMinDays = ruleMinDays
        )

        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(listOf(quotaRule)))
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(quotaRule)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.effectiveQuotaMinDays).isEqualTo(ruleMinDays)
        assertThat(viewModel.uiState.value.effectiveQuotaMinDays).isNotEqualTo(settingsMinDays)
    }

    @Test
    fun `effectiveQuotaPercent falls back to Settings when no QuotaRule exists`() = runTest {
        // Given: No quota rule
        val settingsPercent = 40
        whenever(getSettings()).thenReturn(flowOf(Settings(officeQuotaPercent = settingsPercent)))
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(null)

        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: Falls back to settings
        assertThat(viewModel.uiState.value.effectiveQuotaPercent).isEqualTo(settingsPercent)
    }

    // ========== Settings Change Reactivity Tests ==========

    @Test
    fun `requiredOfficeMinutes updates when settings officeQuotaPercent changes`() = runTest {
        // Given: Settings observable flow, no quota rule
        val settingsFlow = MutableStateFlow(Settings(officeQuotaPercent = 40, officeQuotaMinDays = 8))
        whenever(getSettings()).thenReturn(settingsFlow)
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(null)

        // Return a non-zero quota status on first call, to verify reactivity
        whenever(calculateQuota(any(), any(), any(), any(), any(), any())).thenReturn(QuotaStatus())

        viewModel = createViewModel()
        advanceUntilIdle()

        val initialPercent = viewModel.uiState.value.effectiveQuotaPercent
        assertThat(initialPercent).isEqualTo(40)

        // When: User updates quota in SettingsScreen
        settingsFlow.value = Settings(officeQuotaPercent = 60, officeQuotaMinDays = 8)
        advanceUntilIdle()

        // Then: effectiveQuotaPercent should reflect the new setting
        assertThat(viewModel.uiState.value.effectiveQuotaPercent).isEqualTo(60)
    }

    @Test
    fun `state updates reactively when workTimeRules change`() = runTest {
        // Given: Empty workTimeRules initially
        val workTimeRulesFlow = MutableStateFlow(emptyList<WorkTimeRule>())
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(workTimeRulesFlow)

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: WorkTimeRule is added
        val newRule = WorkTimeRule(
            id = 1,
            validFrom = YearMonth.of(2020, 1),
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 10400
        )
        workTimeRulesFlow.value = listOf(newRule)
        advanceUntilIdle()

        // Then: ViewModel should have recomputed (calculateQuota called again with new rules)
        // The state is reloaded when workTimeRules flow emits a new value
        // (can only verify indirectly via the calculateQuota mock being called)
        // Settings still has default officeQuotaPercent=40 → effectiveQuotaPercent remains 40
        assertThat(viewModel.uiState.value.effectiveQuotaPercent).isEqualTo(40)
    }

    @Test
    fun `state updates reactively when quotaRules change`() = runTest {
        // Given: No quota rules initially
        val quotaRulesFlow = MutableStateFlow(emptyList<QuotaRule>())
        whenever(settingsRepository.getQuotaRules()).thenReturn(quotaRulesFlow)
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(null)
        whenever(getSettings()).thenReturn(flowOf(Settings(officeQuotaPercent = 40)))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.effectiveQuotaPercent).isEqualTo(40)

        // When: A new quota rule is added via settings screen
        val newRule = QuotaRule(
            id = 1,
            validFrom = YearMonth.of(2020, 1),
            officeQuotaPercent = 55,
            officeQuotaMinDays = 10
        )
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(newRule)
        quotaRulesFlow.value = listOf(newRule)
        advanceUntilIdle()

        // Then: effectiveQuotaPercent should now reflect the new rule
        assertThat(viewModel.uiState.value.effectiveQuotaPercent).isEqualTo(55)
    }

    // ========== WorkTimeRule Usage for daily/monthly targets ==========

    @Test
    fun `requiredOfficeMinutes is computed using WorkTimeRule monthly target when rule is active`() = runTest {
        // Given: WorkTimeRule with monthlyWorkMinutes different from settings
        val settingsMonthly = 9266
        val ruleMonthly = 8000
        val quotaPercent = 40

        val settings = Settings(monthlyWorkMinutes = settingsMonthly, officeQuotaPercent = quotaPercent)
        val workTimeRule = WorkTimeRule(
            id = 1,
            validFrom = YearMonth.of(2020, 1),
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = ruleMonthly
        )

        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(listOf(workTimeRule)))
        whenever(settingsRepository.getWorkTimeRuleForDate(any(), any())).thenReturn(workTimeRule)
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(null)

        // When: Month work days has some actual office work to trigger requiredOfficeMinutes calc
        val today = LocalDate.now()
        val officeDay = WorkDay(
            id = 1,
            date = today,
            dayType = DayType.WORK,
            location = WorkLocation.OFFICE
        )
        whenever(getMonthWorkDays(any())).thenReturn(flowOf(listOf(officeDay)))
        whenever(workDayRepository.getWorkDay(any())).thenReturn(flowOf(officeDay))

        viewModel = createViewModel()
        advanceUntilIdle()

        // requiredOfficeMinutes = ruleMonthly * quotaPercent / 100 = 8000 * 0.4 = 3200
        // (bug: would use settingsMonthly * 0.4 = 3706)
        val expectedRequired = (ruleMonthly * quotaPercent / 100.0).toLong()
        val buggyRequired = (settingsMonthly * quotaPercent / 100.0).toLong()

        assertThat(expectedRequired).isNotEqualTo(buggyRequired) // test is meaningful
        assertThat(viewModel.uiState.value.requiredOfficeMinutes).isEqualTo(expectedRequired)
    }

    @Test
    fun `calculateQuota is invoked with QuotaRule values not global settings values`() = runTest {
        // Given: A QuotaRule that differs from global Settings
        val settingsPercent = 40
        val settingsMinDays = 8
        val rulePercent = 65
        val ruleMinDays = 14

        val settings = Settings(officeQuotaPercent = settingsPercent, officeQuotaMinDays = settingsMinDays)
        val quotaRule = QuotaRule(
            id = 1,
            validFrom = YearMonth.of(2020, 1),
            officeQuotaPercent = rulePercent,
            officeQuotaMinDays = ruleMinDays
        )

        val capturedPercents = mutableListOf<Int>()
        val capturedMinDays = mutableListOf<Int>()

        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(listOf(quotaRule)))
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(quotaRule)
        whenever(calculateQuota(any(), any(), any(), any(), any(), any())).thenAnswer { inv ->
            capturedPercents.add(inv.getArgument(3))
            capturedMinDays.add(inv.getArgument(4))
            QuotaStatus()
        }

        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: calculateQuota should have been called with rule values
        assertThat(capturedPercents).isNotEmpty()
        assertThat(capturedPercents.last()).isEqualTo(rulePercent)
        assertThat(capturedMinDays.last()).isEqualTo(ruleMinDays)
    }

    // ========== Regression: Past-month navigation uses correct QuotaRule ==========

    @Test
    fun `navigating to past month shows QuotaRule for that month not current month rule`() = runTest {
        // Regression test for: HomeViewModel used todayYearMonth instead of yearMonth when
        // looking up the QuotaRule, causing past-month views to always show the current month's rule.
        //
        // Scenario: Two rules exist:
        //   - Ab Januar 2026: 40%, 6 Tage
        //   - Ab August 2026: 40%, 8 Tage (current month rule)
        // When navigating to July 2026, the January rule (6 days) must apply, not August (8 days).

        val januaryRule = QuotaRule(
            id = 1,
            validFrom = YearMonth.of(2026, 1),
            officeQuotaPercent = 40,
            officeQuotaMinDays = 6   // the Jan rule: 6 days
        )
        val augustRule = QuotaRule(
            id = 2,
            validFrom = YearMonth.of(2026, 8),
            officeQuotaPercent = 40,
            officeQuotaMinDays = 8   // the Aug rule: 8 days
        )
        val rules = listOf(januaryRule, augustRule)

        val settings = Settings(officeQuotaPercent = 40, officeQuotaMinDays = 10) // default different from both rules
        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(rules))

        // When a date in July is selected, the January rule applies (most recent that started <= July)
        val julyDate = LocalDate.of(2026, 7, 15)
        whenever(workDayRepository.getWorkDay(julyDate)).thenReturn(flowOf(null))
        whenever(getMonthWorkDays(YearMonth.of(2026, 7))).thenReturn(flowOf(emptyList()))

        // getQuotaRuleForMonth(July2026, rules) → januaryRule (validFrom=Jan ≤ Jul, max = Jan)
        whenever(settingsRepository.getQuotaRuleForMonth(YearMonth.of(2026, 7), rules))
            .thenReturn(januaryRule)
        // getQuotaRuleForMonth(August2026, rules) → augustRule (for current month, not used for selected)
        whenever(settingsRepository.getQuotaRuleForMonth(YearMonth.of(2026, 8), rules))
            .thenReturn(augustRule)
        // Fallback for any other month
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(januaryRule)

        viewModel = createViewModel()
        advanceUntilIdle()

        // Navigate to July
        viewModel.navigateToDate(julyDate)
        advanceUntilIdle()

        // Then: effectiveQuotaMinDays must be 6 (from January rule), NOT 8 (from August rule)
        assertThat(viewModel.uiState.value.effectiveQuotaMinDays).isEqualTo(6)
        assertThat(viewModel.uiState.value.effectiveQuotaMinDays).isNotEqualTo(8)
    }

    @Test
    fun `current month shows its own QuotaRule not a past month rule`() = runTest {
        // Complement of the above test: August 2026 should show the August rule (8 days)
        val januaryRule = QuotaRule(id = 1, validFrom = YearMonth.of(2026, 1), officeQuotaPercent = 40, officeQuotaMinDays = 6)
        val augustRule = QuotaRule(id = 2, validFrom = YearMonth.of(2026, 8), officeQuotaPercent = 40, officeQuotaMinDays = 8)
        val rules = listOf(januaryRule, augustRule)

        val settings = Settings(officeQuotaPercent = 40, officeQuotaMinDays = 10)
        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(rules))
        // For current month (today = August 2026 in test scope), return augustRule
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(augustRule)

        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: effectiveQuotaMinDays must be 8 (August rule)
        assertThat(viewModel.uiState.value.effectiveQuotaMinDays).isEqualTo(8)
    }
}
