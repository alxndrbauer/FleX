package com.flex.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flex.domain.model.AbsenceSummary
import com.flex.domain.model.CurrentWeekStatus
import com.flex.domain.model.DashboardForecast
import com.flex.domain.model.DayType
import com.flex.domain.model.FlextimeBalance
import com.flex.domain.model.FlextimeChartPeriod
import com.flex.domain.model.QuotaRule
import com.flex.domain.model.QuotaStatus
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeRange
import com.flex.domain.model.TimeSeriesPoint
import com.flex.domain.model.UpcomingEvent
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkHabits
import com.flex.domain.model.DEFAULT_WORK_DAYS
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.model.getRuleForMonth
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.repository.observeYearClosingsOrEmpty
import com.flex.domain.usecase.CalculateAnalyticsUseCase
import com.flex.domain.usecase.CalculateDashboardMetricsUseCase
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.GetMonthWorkDaysUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.domain.usecase.YearContextResolver
import com.flex.domain.model.YearClosing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class VacationInfo(
    val annualDays: Int = 30,
    val usedVacationDays: Int = 0,
    val carryOverDays: Int = 0,
    val specialDays: Int = 5,
    val usedSpecialDays: Int = 0,
    val plannedVacationDays: Int = 0,
    val plannedSpecialDays: Int = 0,
    val isSpecialPaidOut: Boolean = false,
    val canModifySpecialVacationPayout: Boolean = true
) {
    val remainingVacationDays: Int get() = annualDays + carryOverDays - usedVacationDays
    val remainingSpecialDays: Int get() = if (isSpecialPaidOut) 0 else (specialDays - usedSpecialDays).coerceAtLeast(0)
    val canPayoutSpecialDays: Boolean get() = canModifySpecialVacationPayout && !isSpecialPaidOut && usedSpecialDays == 0 && specialDays > 0
}

data class DashboardUiState(
    val selectedYear: Int = LocalDate.now().year,
    val selectedYearMonth: YearMonth = YearMonth.now(),
    val quotaStatus: QuotaStatus = QuotaStatus(),
    val projectedQuotaStatus: QuotaStatus = QuotaStatus(),
    val flextimeBalance: FlextimeBalance = FlextimeBalance(),
    val vacationInfo: VacationInfo = VacationInfo(),
    val settings: Settings = Settings(),
    val effectiveQuotaPercent: Int = 40,
    val effectiveQuotaMinDays: Int = 8,
    val totalWorkMinutes: Long = 0,
    val requiredOfficeMinutes: Long = 0,
    val sickDays: Int = 0,
    val forecast: DashboardForecast? = null,
    val upcomingEvents: List<UpcomingEvent> = emptyList(),
    val workHabits: WorkHabits = WorkHabits(),
    val currentWeekStatus: CurrentWeekStatus? = null,
    val absenceSummary: AbsenceSummary = AbsenceSummary(),
    val monthFlextimeSeries: List<TimeSeriesPoint> = emptyList(),
    val yearFlextimeSeries: List<TimeSeriesPoint> = emptyList(),
    val selectedFlextimePeriod: FlextimeChartPeriod = FlextimeChartPeriod.MONTH
) {
    val yearMonth: YearMonth get() = selectedYearMonth
}

private data class DateSelection(
    val year: Int = LocalDate.now().year,
    val yearMonth: YearMonth = YearMonth.now()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getMonthWorkDays: GetMonthWorkDaysUseCase,
    private val getSettings: GetSettingsUseCase,
    private val workDayRepository: WorkDayRepository,
    private val settingsRepository: SettingsRepository,
    private val calculateQuota: CalculateQuotaUseCase,
    private val calculateFlextime: CalculateFlextimeUseCase,
    private val calculateDashboardMetrics: CalculateDashboardMetricsUseCase = CalculateDashboardMetricsUseCase(CalculateDayWorkTimeUseCase()),
    private val calculateAnalytics: CalculateAnalyticsUseCase = CalculateAnalyticsUseCase(CalculateDayWorkTimeUseCase(), calculateFlextime),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val yearContextResolver: YearContextResolver = YearContextResolver(calculateFlextime)
) : ViewModel() {

    private val _dateSelection = MutableStateFlow(
        DateSelection(year = LocalDate.now(clock).year, yearMonth = YearMonth.now(clock))
    )

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadData() {
        viewModelScope.launch {
            combine(
                _dateSelection,
                getSettings(),
                settingsRepository.observeYearClosingsOrEmpty()
            ) { dateSelection, settings, closings ->
                Triple(dateSelection, settings, closings)
            }.flatMapLatest { (dateSelection, settings, closings) ->
                val (year, yearMonth) = dateSelection
                val closingsByYear = closings.associateBy { it.year }
                val extraYearsFlow = when {
                    year > settings.settingsYear -> {
                        if (year == settings.settingsYear + 1) {
                            runCatching { workDayRepository.getWorkDaysForYear(settings.settingsYear) }.getOrNull()
                                ?: kotlinx.coroutines.flow.flowOf(emptyList())
                        } else {
                            runCatching {
                                workDayRepository.getWorkDaysInRange(
                                    LocalDate.of(settings.settingsYear, 1, 1),
                                    LocalDate.of(year - 1, 12, 31)
                                )
                            }.getOrNull() ?: kotlinx.coroutines.flow.flowOf(emptyList())
                        }
                    }
                    year < settings.settingsYear && closingsByYear[year] == null -> {
                        val anchorYear = closingsByYear.keys
                            .filter { it > year && it < settings.settingsYear }
                            .minOrNull() ?: settings.settingsYear
                        if (anchorYear > year + 1) {
                            runCatching {
                                workDayRepository.getWorkDaysInRange(
                                    LocalDate.of(year + 1, 1, 1),
                                    LocalDate.of(anchorYear - 1, 12, 31)
                                )
                            }.getOrNull() ?: kotlinx.coroutines.flow.flowOf(emptyList())
                        } else {
                            kotlinx.coroutines.flow.flowOf(emptyList())
                        }
                    }
                    else -> kotlinx.coroutines.flow.flowOf(emptyList())
                }
                combine(
                    getMonthWorkDays(yearMonth),
                    workDayRepository.getWorkDaysForYear(year),
                    settingsRepository.getQuotaRules(),
                    settingsRepository.getWorkTimeRules(),
                    extraYearsFlow
                ) { arr ->
                    @Suppress("UNCHECKED_CAST")
                    val monthDays = arr[0] as List<WorkDay>
                    @Suppress("UNCHECKED_CAST")
                    val yearDays = arr[1] as List<WorkDay>
                    @Suppress("UNCHECKED_CAST")
                    val rules = arr[2] as List<QuotaRule>
                    @Suppress("UNCHECKED_CAST")
                    val workTimeRules = arr[3] as List<WorkTimeRule>
                    @Suppress("UNCHECKED_CAST")
                    val extraDays = arr[4] as List<WorkDay>
                    val extraByYear = extraDays.groupBy { it.date.year }
                    val effectiveSettings = yearContextResolver.resolveSettingsForYear(
                        targetYear = year,
                        settings = settings,
                        closingsByYear = closingsByYear,
                        workDaysLookup = { y -> if (y == year) yearDays else extraByYear[y].orEmpty() },
                        workTimeRules = workTimeRules,
                        includeForwardBalances = true
                    )

                    val rule = settingsRepository.getQuotaRuleForMonth(yearMonth, rules)
                    val qPercent = rule?.officeQuotaPercent ?: settings.officeQuotaPercent
                    val qDays = rule?.officeQuotaMinDays ?: settings.officeQuotaMinDays

                    val actualMonthDays = monthDays.filter { !it.isPlanned }
                    val actualQuota = calculateQuota(actualMonthDays, settings, yearMonth, qPercent, qDays, workTimeRules)

                    val preparedMonthDays = monthDays.map { day ->
                        if (day.isPlanned && day.timeBlocks.isEmpty() && day.dayType in setOf(DayType.WORK, DayType.BUSINESS_TRIP, DayType.SATURDAY_BONUS)) {
                            val wtr = settingsRepository.getWorkTimeRuleForDate(day.date, workTimeRules)
                            val dailyTarget = wtr?.dailyWorkMinutes ?: settings.dailyWorkMinutes
                            val start = settings.defaultStartTime
                            val end = start.plusMinutes(dailyTarget.toLong())
                            day.copy(
                                timeBlocks = listOf(
                                    com.flex.domain.model.TimeBlock(
                                        workDayId = day.id,
                                        startTime = start,
                                        endTime = end,
                                        isDuration = true,
                                        location = day.location
                                    )
                                )
                            )
                        } else {
                            day
                        }
                    }
                    val projectedQuota = calculateQuota(preparedMonthDays, settings, yearMonth, qPercent, qDays, workTimeRules)

                    // Cumulative flextime from all year's actual data
                    val actualYearDays = yearDays.filter { !it.isPlanned }
                    val flextime = calculateFlextime(actualYearDays, effectiveSettings, yearMonth, workTimeRules)

                    // Fixed monthly target, reduced by neutral days
                    val neutralTypes = setOf(DayType.VACATION, DayType.SPECIAL_VACATION, DayType.FLEX_DAY, DayType.SICK_DAY)
                    val neutralDays = monthDays.filter { it.dayType in neutralTypes }
                    val neutralDaysDeduction = neutralDays.sumOf { day ->
                        val rule = settingsRepository.getWorkTimeRuleForDate(day.date, workTimeRules)
                        val isWorkDay = if (rule != null) day.date.dayOfWeek in rule.workDays else true
                        if (isWorkDay) {
                            (rule?.dailyWorkMinutes ?: settings.dailyWorkMinutes).toLong()
                        } else 0L
                    }
                    val baseMonthlyTarget = (workTimeRules.getRuleForMonth(yearMonth)?.monthlyWorkMinutes
                        ?: settings.monthlyWorkMinutes).toLong()
                    val totalMin = (baseMonthlyTarget - neutralDaysDeduction).coerceAtLeast(0)
                    val requiredMin = (totalMin * qPercent / 100.0).toLong()

                    val usedVacation = yearDays.count {
                        it.dayType == DayType.VACATION && !it.isPlanned
                    }
                    val usedSpecial = yearDays.count {
                        it.dayType == DayType.SPECIAL_VACATION && !it.isPlanned
                    }
                    val plannedVacation = yearDays.count {
                        it.dayType == DayType.VACATION && it.isPlanned
                    }
                    val plannedSpecial = yearDays.count {
                        it.dayType == DayType.SPECIAL_VACATION && it.isPlanned
                    }
                    val sickDays = yearDays.count {
                        it.dayType == DayType.SICK_DAY && !it.isPlanned
                    }

                    val today = LocalDate.now(clock)
                    val forecast = calculateDashboardMetrics.calculateForecast(
                        actualQuota = actualQuota,
                        projectedQuota = projectedQuota,
                        effectiveQuotaMinDays = qDays,
                        effectiveQuotaPercent = qPercent,
                        monthWorkDays = preparedMonthDays,
                        targetMonthlyMinutes = totalMin
                    )
                    val upcomingEvents = calculateDashboardMetrics.calculateUpcomingEvents(
                        today = today,
                        yearDays = yearDays,
                        year = year
                    )
                    val habits = calculateDashboardMetrics.calculateWorkHabits(monthDays)
                    val absenceSummary = calculateDashboardMetrics.calculateAbsenceSummary(yearDays)
                    val currentWeekStatus = if (year == today.year) {
                        calculateDashboardMetrics.calculateCurrentWeekStatus(today, yearDays, settings, workTimeRules)
                    } else null

                    val monthFlextime = calculateAnalytics(
                        workDays = monthDays,
                        settings = effectiveSettings,
                        timeRange = TimeRange.Month(yearMonth),
                        workTimeRules = workTimeRules,
                        priorWorkDays = yearDays.filter { it.date.isBefore(yearMonth.atDay(1)) && !it.isPlanned }
                    ).flextimeSeries

                    val yearFlextime = calculateAnalytics(
                        workDays = yearDays,
                        settings = effectiveSettings,
                        timeRange = TimeRange.Year(year),
                        workTimeRules = workTimeRules
                    ).flextimeSeries

                    DashboardUiState(
                        selectedYear = year,
                        selectedYearMonth = yearMonth,
                        quotaStatus = actualQuota,
                        projectedQuotaStatus = projectedQuota,
                        flextimeBalance = flextime,
                        vacationInfo = VacationInfo(
                            annualDays = effectiveSettings.annualVacationDays,
                            usedVacationDays = usedVacation,
                            carryOverDays = effectiveSettings.carryOverVacationDays,
                            specialDays = effectiveSettings.specialVacationDays,
                            usedSpecialDays = usedSpecial,
                            plannedVacationDays = plannedVacation,
                            plannedSpecialDays = plannedSpecial,
                            isSpecialPaidOut = effectiveSettings.specialVacationPaidOut,
                            canModifySpecialVacationPayout = (year == settings.settingsYear)
                        ),
                        settings = settings,
                        effectiveQuotaPercent = qPercent,
                        effectiveQuotaMinDays = qDays,
                        totalWorkMinutes = totalMin,
                        requiredOfficeMinutes = requiredMin,
                        sickDays = sickDays,
                        forecast = forecast,
                        upcomingEvents = upcomingEvents,
                        workHabits = habits,
                        currentWeekStatus = currentWeekStatus,
                        absenceSummary = absenceSummary,
                        monthFlextimeSeries = monthFlextime,
                        yearFlextimeSeries = yearFlextime,
                        selectedFlextimePeriod = _uiState.value.selectedFlextimePeriod
                    )
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun selectFlextimePeriod(period: FlextimeChartPeriod) {
        _uiState.value = _uiState.value.copy(selectedFlextimePeriod = period)
    }

    fun previousYear() {
        val newYear = _dateSelection.value.year - 1
        _dateSelection.value = DateSelection(
            year = newYear,
            yearMonth = YearMonth.of(newYear, _dateSelection.value.yearMonth.monthValue)
        )
    }

    fun nextYear() {
        val newYear = _dateSelection.value.year + 1
        _dateSelection.value = DateSelection(
            year = newYear,
            yearMonth = YearMonth.of(newYear, _dateSelection.value.yearMonth.monthValue)
        )
    }

    fun selectYear(year: Int) {
        _dateSelection.value = DateSelection(
            year = year,
            yearMonth = YearMonth.of(year, _dateSelection.value.yearMonth.monthValue)
        )
    }

    fun previousMonth() {
        val newMonth = _dateSelection.value.yearMonth.minusMonths(1)
        _dateSelection.value = DateSelection(
            year = newMonth.year,
            yearMonth = newMonth
        )
    }

    fun nextMonth() {
        val newMonth = _dateSelection.value.yearMonth.plusMonths(1)
        _dateSelection.value = DateSelection(
            year = newMonth.year,
            yearMonth = newMonth
        )
    }

    fun selectMonth(yearMonth: YearMonth) {
        _dateSelection.value = DateSelection(
            year = yearMonth.year,
            yearMonth = yearMonth
        )
    }

    fun payoutSpecialVacation() {
        viewModelScope.launch {
            val currentSettings = getSettings().first()
            val year = _dateSelection.value.year
            if (year != currentSettings.settingsYear) return@launch
            val yearDays = workDayRepository.getWorkDaysForYear(year).first()
            val usedSpecial = yearDays.count { it.dayType == DayType.SPECIAL_VACATION && !it.isPlanned }
            if (usedSpecial > 0) return@launch

            val plannedSpecialDays = yearDays.filter { it.dayType == DayType.SPECIAL_VACATION && it.isPlanned }
            plannedSpecialDays.forEach { workDayRepository.deleteWorkDay(it) }

            settingsRepository.saveSettings(currentSettings.copy(specialVacationPaidOut = true))
        }
    }

    fun revertSpecialVacationPayout() {
        viewModelScope.launch {
            val currentSettings = getSettings().first()
            if (_dateSelection.value.year != currentSettings.settingsYear) return@launch
            settingsRepository.saveSettings(currentSettings.copy(specialVacationPaidOut = false))
        }
    }
}
