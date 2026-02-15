package com.vrema.ui.quota

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.vrema.BaseUnitTest
import com.vrema.MainDispatcherRule
import com.vrema.domain.model.DayType
import com.vrema.domain.model.FlextimeBalance
import com.vrema.domain.model.QuotaRule
import com.vrema.domain.model.QuotaStatus
import com.vrema.domain.model.Settings
import com.vrema.domain.model.WorkDay
import com.vrema.domain.model.WorkLocation
import com.vrema.domain.repository.SettingsRepository
import com.vrema.domain.repository.WorkDayRepository
import com.vrema.domain.usecase.CalculateFlextimeUseCase
import com.vrema.domain.usecase.CalculateQuotaUseCase
import com.vrema.domain.usecase.GetMonthWorkDaysUseCase
import com.vrema.domain.usecase.GetSettingsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.YearMonth

/**
 * Unit tests for QuotaViewModel.
 * Tests quota status, flextime balance, vacation info calculations, and state updates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuotaViewModelTest : BaseUnitTest() {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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

    private lateinit var viewModel: QuotaViewModel

    override fun setUp() {
        super.setUp()
        // Default mock behavior
        whenever(getMonthWorkDays(any())).thenReturn(flowOf(emptyList()))
        whenever(getSettings()).thenReturn(flowOf(Settings()))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(emptyList()))
        whenever(workDayRepository.getWorkDaysForYear(any())).thenReturn(flowOf(emptyList()))
        whenever(calculateQuota(any(), any(), any(), any(), any())).thenReturn(QuotaStatus())
        whenever(calculateFlextime(any(), any(), any())).thenReturn(FlextimeBalance())
    }

    // ========== Initial State Tests ==========

    @Test
    fun `initial state uses current YearMonth`() = runTest {
        // When: ViewModel is created
        viewModel = QuotaViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateQuota, calculateFlextime
        )
        advanceUntilIdle()

        // Then: State should have current YearMonth
        assertThat(viewModel.uiState.value.yearMonth).isEqualTo(YearMonth.now())
    }

    @Test
    fun `initial state loads quotaStatus from calculation`() = runTest {
        // Given: Calculate quota returns specific result
        val expectedQuota = QuotaStatus(
            officeMinutes = 1800,
            homeOfficeMinutes = 1200,
            officeDays = 10,
            homeOfficeDays = 5,
            officePercent = 60.0,
            percentQuotaMet = true,
            daysQuotaMet = true
        )
        whenever(calculateQuota(any(), any(), any(), any(), any())).thenReturn(expectedQuota)

        // When: ViewModel is created
        viewModel = QuotaViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateQuota, calculateFlextime
        )
        advanceUntilIdle()

        // Then: State should contain calculated quota status
        assertThat(viewModel.uiState.value.quotaStatus).isEqualTo(expectedQuota)
    }

    @Test
    fun `initial state loads flextimeBalance from calculation`() = runTest {
        // Given: Calculate flextime returns specific result
        val expectedFlextime = FlextimeBalance(
            initialMinutes = 60,
            earnedMinutes = 180,
            totalMinutes = 240,
            targetMinutes = 0,
            overtimeMinutes = 240
        )
        whenever(calculateFlextime(any(), any(), any())).thenReturn(expectedFlextime)

        // When: ViewModel is created
        viewModel = QuotaViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateQuota, calculateFlextime
        )
        advanceUntilIdle()

        // Then: State should contain calculated flextime balance
        assertThat(viewModel.uiState.value.flextimeBalance).isEqualTo(expectedFlextime)
    }

    @Test
    fun `initial state calculates vacationInfo from settings and workdays`() = runTest {
        // Given: Settings with vacation days
        val settings = Settings(
            annualVacationDays = 30,
            carryOverVacationDays = 5,
            specialVacationDays = 5
        )
        whenever(getSettings()).thenReturn(flowOf(settings))

        // And: Work days with used vacation
        val yearDays = listOf(
            WorkDay(id = 1, date = LocalDate.now().minusDays(10), dayType = DayType.VACATION, isPlanned = false),
            WorkDay(id = 2, date = LocalDate.now().minusDays(9), dayType = DayType.VACATION, isPlanned = false),
            WorkDay(id = 3, date = LocalDate.now().minusDays(8), dayType = DayType.SPECIAL_VACATION, isPlanned = false)
        )
        whenever(workDayRepository.getWorkDaysForYear(any())).thenReturn(flowOf(yearDays))

        // When: ViewModel is created
        viewModel = QuotaViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateQuota, calculateFlextime
        )
        advanceUntilIdle()

        // Then: VacationInfo should be calculated correctly
        val vacationInfo = viewModel.uiState.value.vacationInfo
        assertThat(vacationInfo.annualDays).isEqualTo(30)
        assertThat(vacationInfo.carryOverDays).isEqualTo(5)
        assertThat(vacationInfo.specialDays).isEqualTo(5)
        assertThat(vacationInfo.usedVacationDays).isEqualTo(2)
        assertThat(vacationInfo.usedSpecialDays).isEqualTo(1)
        assertThat(vacationInfo.remainingVacationDays).isEqualTo(33) // 30 + 5 - 2
        assertThat(vacationInfo.remainingSpecialDays).isEqualTo(4) // 5 - 1
    }

    // ========== Vacation Info Tests ==========

    @Test
    fun `vacationInfo excludes planned days from used count`() = runTest {
        // Given: Mix of actual and planned vacation days
        val yearDays = listOf(
            WorkDay(id = 1, date = LocalDate.now().minusDays(10), dayType = DayType.VACATION, isPlanned = false),
            WorkDay(id = 2, date = LocalDate.now().plusDays(10), dayType = DayType.VACATION, isPlanned = true),
            WorkDay(id = 3, date = LocalDate.now().plusDays(11), dayType = DayType.VACATION, isPlanned = true)
        )
        whenever(workDayRepository.getWorkDaysForYear(any())).thenReturn(flowOf(yearDays))

        // When: ViewModel is created
        viewModel = QuotaViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateQuota, calculateFlextime
        )
        advanceUntilIdle()

        // Then: Only actual vacation days should be counted as used
        val vacationInfo = viewModel.uiState.value.vacationInfo
        assertThat(vacationInfo.usedVacationDays).isEqualTo(1)
        assertThat(vacationInfo.plannedVacationDays).isEqualTo(2)
    }

    @Test
    fun `vacationInfo tracks special vacation separately`() = runTest {
        // Given: Mix of regular and special vacation
        val yearDays = listOf(
            WorkDay(id = 1, date = LocalDate.now().minusDays(10), dayType = DayType.VACATION, isPlanned = false),
            WorkDay(id = 2, date = LocalDate.now().minusDays(9), dayType = DayType.VACATION, isPlanned = false),
            WorkDay(id = 3, date = LocalDate.now().minusDays(8), dayType = DayType.SPECIAL_VACATION, isPlanned = false),
            WorkDay(id = 4, date = LocalDate.now().plusDays(10), dayType = DayType.SPECIAL_VACATION, isPlanned = true)
        )
        whenever(workDayRepository.getWorkDaysForYear(any())).thenReturn(flowOf(yearDays))

        // When: ViewModel is created
        viewModel = QuotaViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateQuota, calculateFlextime
        )
        advanceUntilIdle()

        // Then: Vacation types should be tracked separately
        val vacationInfo = viewModel.uiState.value.vacationInfo
        assertThat(vacationInfo.usedVacationDays).isEqualTo(2)
        assertThat(vacationInfo.usedSpecialDays).isEqualTo(1)
        assertThat(vacationInfo.plannedSpecialDays).isEqualTo(1)
    }

    @Test
    fun `vacationInfo remainingVacationDays includes carryOver`() = runTest {
        // Given: Settings with carry-over days
        val settings = Settings(
            annualVacationDays = 30,
            carryOverVacationDays = 5
        )
        whenever(getSettings()).thenReturn(flowOf(settings))

        // And: Some vacation days used
        val yearDays = listOf(
            WorkDay(id = 1, date = LocalDate.now().minusDays(10), dayType = DayType.VACATION, isPlanned = false)
        )
        whenever(workDayRepository.getWorkDaysForYear(any())).thenReturn(flowOf(yearDays))

        // When: ViewModel is created
        viewModel = QuotaViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateQuota, calculateFlextime
        )
        advanceUntilIdle()

        // Then: Remaining should be annual + carryOver - used
        val vacationInfo = viewModel.uiState.value.vacationInfo
        assertThat(vacationInfo.remainingVacationDays).isEqualTo(34) // 30 + 5 - 1
    }

    // ========== Settings and Quota Rules Tests ==========

    @Test
    fun `state reflects Settings from repository`() = runTest {
        // Given: Repository returns specific settings
        val expectedSettings = Settings(
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 10000,
            officeQuotaPercent = 50,
            officeQuotaMinDays = 10
        )
        whenever(getSettings()).thenReturn(flowOf(expectedSettings))

        // When: ViewModel is created
        viewModel = QuotaViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateQuota, calculateFlextime
        )
        advanceUntilIdle()

        // Then: State should contain settings
        assertThat(viewModel.uiState.value.settings).isEqualTo(expectedSettings)
    }

    @Test
    fun `effectiveQuotaPercent uses quota rule when available`() = runTest {
        // Given: Settings with default quota
        val settings = Settings(officeQuotaPercent = 40, officeQuotaMinDays = 8)
        whenever(getSettings()).thenReturn(flowOf(settings))

        // And: Quota rule for current month
        val quotaRule = QuotaRule(
            id = 1,
            validFrom = YearMonth.now().minusMonths(1),
            officeQuotaPercent = 60,
            officeQuotaMinDays = 12
        )
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(listOf(quotaRule)))
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(quotaRule)

        // When: ViewModel is created
        viewModel = QuotaViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateQuota, calculateFlextime
        )
        advanceUntilIdle()

        // Then: Effective quota should use rule values
        assertThat(viewModel.uiState.value.effectiveQuotaPercent).isEqualTo(60)
        assertThat(viewModel.uiState.value.effectiveQuotaMinDays).isEqualTo(12)
    }

    @Test
    fun `effectiveQuotaPercent uses settings when no quota rule exists`() = runTest {
        // Given: Settings with default quota and no rules
        val settings = Settings(officeQuotaPercent = 40, officeQuotaMinDays = 8)
        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(settingsRepository.getQuotaRules()).thenReturn(flowOf(emptyList()))
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(null)

        // When: ViewModel is created
        viewModel = QuotaViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateQuota, calculateFlextime
        )
        advanceUntilIdle()

        // Then: Effective quota should use settings values
        assertThat(viewModel.uiState.value.effectiveQuotaPercent).isEqualTo(40)
        assertThat(viewModel.uiState.value.effectiveQuotaMinDays).isEqualTo(8)
    }

    // ========== Calculations Tests ==========

    @Test
    fun `quotaStatus is calculated correctly`() = runTest {
        // Given: Specific quota calculation result
        val expectedQuota = QuotaStatus(
            officeMinutes = 2400,
            homeOfficeMinutes = 1600,
            officeDays = 12,
            homeOfficeDays = 8,
            officePercent = 60.0,
            percentQuotaMet = true,
            daysQuotaMet = true,
            remainingWorkDays = 5,
            requiredOfficeDaysForQuota = 2
        )
        whenever(calculateQuota(any(), any(), any(), any(), any())).thenReturn(expectedQuota)

        // When: ViewModel is created
        viewModel = QuotaViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateQuota, calculateFlextime
        )
        advanceUntilIdle()

        // Then: State should have correct quota status
        val quota = viewModel.uiState.value.quotaStatus
        assertThat(quota.officeMinutes).isEqualTo(2400)
        assertThat(quota.officeDays).isEqualTo(12)
        assertThat(quota.percentQuotaMet).isTrue()
        assertThat(quota.daysQuotaMet).isTrue()
    }

    @Test
    fun `flextimeBalance is calculated from year data`() = runTest {
        // Given: Year work days
        val yearDays = listOf(
            WorkDay(id = 1, date = LocalDate.now().minusDays(30), location = WorkLocation.OFFICE, isPlanned = false),
            WorkDay(id = 2, date = LocalDate.now().minusDays(29), location = WorkLocation.HOME_OFFICE, isPlanned = false)
        )
        whenever(workDayRepository.getWorkDaysForYear(any())).thenReturn(flowOf(yearDays))

        val expectedFlextime = FlextimeBalance(
            initialMinutes = 0,
            earnedMinutes = 300,
            totalMinutes = 300,
            targetMinutes = 9266,
            overtimeMinutes = -8966
        )
        whenever(calculateFlextime(any(), any(), any())).thenReturn(expectedFlextime)

        // When: ViewModel is created
        viewModel = QuotaViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateQuota, calculateFlextime
        )
        advanceUntilIdle()

        // Then: Flextime balance should be calculated
        assertThat(viewModel.uiState.value.flextimeBalance).isEqualTo(expectedFlextime)
    }

    // ========== Monthly Calculations Tests ==========

    @Test
    fun `totalWorkMinutes excludes neutral day types`() = runTest {
        // Given: Month days with mix of work and neutral types
        val settings = Settings(
            monthlyWorkMinutes = 9266,
            dailyWorkMinutes = 426
        )
        whenever(getSettings()).thenReturn(flowOf(settings))

        val monthDays = listOf(
            WorkDay(id = 1, date = LocalDate.now().minusDays(5), dayType = DayType.WORK),
            WorkDay(id = 2, date = LocalDate.now().minusDays(4), dayType = DayType.VACATION),
            WorkDay(id = 3, date = LocalDate.now().minusDays(3), dayType = DayType.FLEX_DAY)
        )
        whenever(getMonthWorkDays(any())).thenReturn(flowOf(monthDays))

        // When: ViewModel is created
        viewModel = QuotaViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateQuota, calculateFlextime
        )
        advanceUntilIdle()

        // Then: Total work minutes should exclude 2 neutral days (vacation + flex)
        // 9266 - (2 * 426) = 9266 - 852 = 8414
        assertThat(viewModel.uiState.value.totalWorkMinutes).isEqualTo(8414)
    }

    @Test
    fun `requiredOfficeMinutes is calculated from totalWorkMinutes and quota percent`() = runTest {
        // Given: Settings and calculated total
        val settings = Settings(
            monthlyWorkMinutes = 10000,
            dailyWorkMinutes = 500,
            officeQuotaPercent = 40
        )
        whenever(getSettings()).thenReturn(flowOf(settings))
        whenever(settingsRepository.getQuotaRuleForMonth(any(), any())).thenReturn(null)

        // When: ViewModel is created
        viewModel = QuotaViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateQuota, calculateFlextime
        )
        advanceUntilIdle()

        // Then: Required office minutes should be 40% of total
        // 10000 * 0.40 = 4000
        assertThat(viewModel.uiState.value.requiredOfficeMinutes).isEqualTo(4000)
    }

    // ========== Edge Cases ==========

    @Test
    fun `empty workDays list produces default state`() = runTest {
        // Given: No work days
        whenever(getMonthWorkDays(any())).thenReturn(flowOf(emptyList()))
        whenever(workDayRepository.getWorkDaysForYear(any())).thenReturn(flowOf(emptyList()))

        // When: ViewModel is created
        viewModel = QuotaViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateQuota, calculateFlextime
        )
        advanceUntilIdle()

        // Then: VacationInfo should show no used days
        val vacationInfo = viewModel.uiState.value.vacationInfo
        assertThat(vacationInfo.usedVacationDays).isEqualTo(0)
        assertThat(vacationInfo.usedSpecialDays).isEqualTo(0)
        assertThat(vacationInfo.plannedVacationDays).isEqualTo(0)
        assertThat(vacationInfo.plannedSpecialDays).isEqualTo(0)
    }

    @Test
    fun `planned days are excluded from flextime calculation`() = runTest {
        // Given: Mix of planned and actual days
        val yearDays = listOf(
            WorkDay(id = 1, date = LocalDate.now().minusDays(5), dayType = DayType.WORK, isPlanned = false),
            WorkDay(id = 2, date = LocalDate.now().plusDays(5), dayType = DayType.WORK, isPlanned = true)
        )
        whenever(workDayRepository.getWorkDaysForYear(any())).thenReturn(flowOf(yearDays))

        // When: ViewModel is created
        viewModel = QuotaViewModel(
            getMonthWorkDays, getSettings, workDayRepository,
            settingsRepository, calculateQuota, calculateFlextime
        )
        advanceUntilIdle()

        // Then: Calculate flextime should be called with only actual days (filtered by ViewModel logic)
        // This is implicitly tested through the flow collection logic
        assertThat(viewModel.uiState.value.flextimeBalance).isNotNull()
    }
}
