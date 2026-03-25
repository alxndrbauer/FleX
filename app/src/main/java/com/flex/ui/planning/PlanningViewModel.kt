package com.flex.ui.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flex.domain.model.DayType
import com.flex.domain.model.FlextimeBalance
import com.flex.domain.model.PublicHolidays
import com.flex.domain.model.QuotaStatus
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.model.QuotaRule
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.GetMonthWorkDaysUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject

data class OfficeHoursDetail(
    val requiredOfficeMinutes: Long = 0,
    val plannedOfficeMinutes: Long = 0,
    val plannedTotalMinutes: Long = 0
) {
    val requiredOfficeHours: String get() {
        val h = requiredOfficeMinutes / 60
        val m = requiredOfficeMinutes % 60
        return "${h}h ${m}min"
    }
    val plannedOfficeHours: String get() {
        val h = plannedOfficeMinutes / 60
        val m = plannedOfficeMinutes % 60
        return "${h}h ${m}min"
    }
    val plannedTotalHours: String get() {
        val h = plannedTotalMinutes / 60
        val m = plannedTotalMinutes % 60
        return "${h}h ${m}min"
    }
    val isMet: Boolean get() = plannedOfficeMinutes >= requiredOfficeMinutes
}

data class MonthSummary(
    val yearMonth: YearMonth,
    val plannedDays: Int = 0,
    val officeDays: Int = 0,
    val homeOfficeDays: Int = 0,
    val vacationDays: Int = 0,
    val quotaMet: Boolean = false,
    val officePercent: Double = 0.0,
    val officeHours: OfficeHoursDetail = OfficeHoursDetail()
)

data class PlanningUiState(
    val yearMonth: YearMonth = YearMonth.now().plusMonths(1),
    val workDays: List<WorkDay> = emptyList(),
    val settings: Settings = Settings(),
    val quotaStatus: QuotaStatus = QuotaStatus(),
    val flextimeBalance: FlextimeBalance = FlextimeBalance(),
    val officeHours: OfficeHoursDetail = OfficeHoursDetail(),
    val selectedPlanType: PlanType = PlanType.OFFICE,
    val editingDate: LocalDate? = null,
    val monthSummaries: List<MonthSummary> = emptyList()
)

enum class PlanType(val label: String) {
    OFFICE("Büro"),
    HOME_OFFICE("Home-Office"),
    VACATION("Urlaub"),
    SPECIAL_VACATION("Sonderurlaub"),
    FLEX_DAY("Gleittag"),
    SATURDAY_BONUS("Samstag+")
}

@HiltViewModel
class PlanningViewModel @Inject constructor(
    private val getMonthWorkDays: GetMonthWorkDaysUseCase,
    private val getSettings: GetSettingsUseCase,
    private val workDayRepository: WorkDayRepository,
    private val settingsRepository: SettingsRepository,
    private val calculateDayWorkTime: CalculateDayWorkTimeUseCase,
    private val calculateQuota: CalculateQuotaUseCase,
    private val calculateFlextime: CalculateFlextimeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanningUiState())
    val uiState: StateFlow<PlanningUiState> = _uiState.asStateFlow()

    private val _selectedMonth = MutableStateFlow(YearMonth.now().plusMonths(1))

    init {
        loadData()
        loadMonthSummaries()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun loadData() {
        viewModelScope.launch {
            combine(
                _selectedMonth,
                getSettings(),
                settingsRepository.getQuotaRules()
            ) { month, settings, rules -> Triple(month, settings, rules) }
                .flatMapLatest { (month, settings, rules) ->
                    combine(
                        getMonthWorkDays(month),
                        workDayRepository.getWorkDaysForYear(month.year)
                    ) { days, yearDays ->
                        arrayOf(month, settings, rules, days, yearDays)
                    }
                }
                .collect { arr ->
                    @Suppress("UNCHECKED_CAST")
                    val month = arr[0] as YearMonth
                    val settings = arr[1] as Settings
                    @Suppress("UNCHECKED_CAST")
                    val rules = arr[2] as List<QuotaRule>
                    @Suppress("UNCHECKED_CAST")
                    val days = arr[3] as List<WorkDay>
                    @Suppress("UNCHECKED_CAST")
                    val yearDays = arr[4] as List<WorkDay>

                    val rule = settingsRepository.getQuotaRuleForMonth(month, rules)
                    val qPercent = rule?.officeQuotaPercent ?: settings.officeQuotaPercent
                    val qDays = rule?.officeQuotaMinDays ?: settings.officeQuotaMinDays
                    val prognosisDays = buildPrognosisDays(month, days, settings)
                    val quota = calculateQuota(prognosisDays, settings, month, qPercent, qDays)

                    // Cumulative flextime: previous months' actual data + this month's prognosis
                    val previousMonthsDays = yearDays.filter {
                        YearMonth.from(it.date).isBefore(month) && !it.isPlanned
                    }
                    val flextime = calculateFlextime(previousMonthsDays + prognosisDays, settings, month)
                    val officeHours = calculateOfficeHours(prognosisDays, qPercent, settings)
                    _uiState.value = _uiState.value.copy(
                        yearMonth = month,
                        workDays = days,
                        settings = settings.copy(officeQuotaPercent = qPercent, officeQuotaMinDays = qDays),
                        quotaStatus = quota,
                        flextimeBalance = flextime,
                        officeHours = officeHours
                    )
                    refreshMonthSummaries(settings, rules)
                }
        }
    }

    private fun loadMonthSummaries() {
        viewModelScope.launch {
            combine(
                getSettings(),
                settingsRepository.getQuotaRules()
            ) { settings, rules -> settings to rules }
                .collect { (settings, rules) ->
                    refreshMonthSummaries(settings, rules)
                }
        }
    }

    private suspend fun refreshMonthSummaries(settings: Settings, rules: List<QuotaRule>) {
        val currentMonth = YearMonth.now()
        val summaries = mutableListOf<MonthSummary>()

        for (offset in 0L..11L) {
            val month = currentMonth.plusMonths(offset)
            val rule = settingsRepository.getQuotaRuleForMonth(month, rules)
            val qPercent = rule?.officeQuotaPercent ?: settings.officeQuotaPercent
            val qDays = rule?.officeQuotaMinDays ?: settings.officeQuotaMinDays
            val days = getMonthWorkDays(month).firstOrNull() ?: emptyList()
            val prognosisDays = buildPrognosisDays(month, days, settings)
            val quota = calculateQuota(prognosisDays, settings, month, qPercent, qDays)
            val officeHours = calculateOfficeHours(prognosisDays, qPercent, settings)

            val plannedDays = days.count { it.isPlanned }
            val officeDays = days.count { it.dayType == DayType.WORK && it.location == WorkLocation.OFFICE }
            val homeOfficeDays = days.count { it.dayType == DayType.WORK && it.location == WorkLocation.HOME_OFFICE }
            val vacationDays = days.count { it.dayType in listOf(DayType.VACATION, DayType.SPECIAL_VACATION) }

            summaries.add(MonthSummary(
                yearMonth = month,
                plannedDays = plannedDays,
                officeDays = officeDays,
                homeOfficeDays = homeOfficeDays,
                vacationDays = vacationDays,
                quotaMet = quota.quotaMet,
                officePercent = quota.officePercent,
                officeHours = officeHours
            ))
        }

        _uiState.value = _uiState.value.copy(monthSummaries = summaries)
    }

    private fun calculateOfficeHours(
        prognosisDays: List<WorkDay>,
        quotaPercent: Int,
        settings: Settings
    ): OfficeHoursDetail {
        // Fixed monthly target, reduced by neutral days
        val neutralTypes = setOf(DayType.VACATION, DayType.SPECIAL_VACATION, DayType.FLEX_DAY, DayType.SICK_DAY)
        val neutralDayCount = prognosisDays.count { it.dayType in neutralTypes }
        val totalMinutes = (settings.monthlyWorkMinutes - neutralDayCount.toLong() * settings.dailyWorkMinutes).coerceAtLeast(0)

        val workingDays = prognosisDays.filter { it.dayType !in neutralTypes }

        var officeMinutes = 0L
        for (day in workingDays) {
            val result = calculateDayWorkTime(day.timeBlocks)
            if (day.location == WorkLocation.OFFICE) {
                officeMinutes += result.netMinutes
            }
        }

        val requiredOfficeMinutes = (totalMinutes * quotaPercent / 100.0).toLong()

        return OfficeHoursDetail(
            requiredOfficeMinutes = requiredOfficeMinutes,
            plannedOfficeMinutes = officeMinutes,
            plannedTotalMinutes = totalMinutes
        )
    }

    private fun buildPrognosisDays(
        month: YearMonth,
        existingDays: List<WorkDay>,
        settings: Settings
    ): List<WorkDay> {
        val existingByDate = existingDays.associateBy { it.date }
        val allDays = mutableListOf<WorkDay>()

        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val existing = existingByDate[date]

            if (existing != null) {
                if (existing.timeBlocks.isEmpty() &&
                    existing.dayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS)
                ) {
                    val start = LocalTime.of(8, 0)
                    val end = start.plusMinutes(settings.dailyWorkMinutes.toLong())
                    allDays.add(existing.copy(
                        timeBlocks = listOf(TimeBlock(workDayId = existing.id, startTime = start, endTime = end, isDuration = true, location = existing.location))
                    ))
                } else {
                    allDays.add(existing)
                }
            } else if (date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
                && !PublicHolidays.isHoliday(date)) {
                val start = LocalTime.of(8, 0)
                val end = start.plusMinutes(settings.dailyWorkMinutes.toLong())
                allDays.add(WorkDay(
                    date = date,
                    location = WorkLocation.HOME_OFFICE,
                    dayType = DayType.WORK,
                    isPlanned = true,
                    timeBlocks = listOf(TimeBlock(workDayId = 0, startTime = start, endTime = end, isDuration = true, location = WorkLocation.HOME_OFFICE))
                ))
            }
        }
        return allDays
    }

    fun previousMonth() {
        val min = YearMonth.now()
        if (_selectedMonth.value > min) {
            _selectedMonth.value = _selectedMonth.value.minusMonths(1)
        }
    }

    fun nextMonth() {
        _selectedMonth.value = _selectedMonth.value.plusMonths(1)
    }

    fun navigateToMonth(yearMonth: YearMonth) {
        _selectedMonth.value = yearMonth
    }

    fun setSelectedPlanType(type: PlanType) {
        _uiState.value = _uiState.value.copy(selectedPlanType = type)
    }

    fun planDay(date: LocalDate) {
        if (PublicHolidays.isHoliday(date)) return
        viewModelScope.launch {
            val state = _uiState.value
            val planType = state.selectedPlanType

            val (location, dayType) = when (planType) {
                PlanType.OFFICE -> WorkLocation.OFFICE to DayType.WORK
                PlanType.HOME_OFFICE -> WorkLocation.HOME_OFFICE to DayType.WORK
                PlanType.VACATION -> WorkLocation.HOME_OFFICE to DayType.VACATION
                PlanType.SPECIAL_VACATION -> WorkLocation.HOME_OFFICE to DayType.SPECIAL_VACATION
                PlanType.FLEX_DAY -> WorkLocation.HOME_OFFICE to DayType.FLEX_DAY
                PlanType.SATURDAY_BONUS -> WorkLocation.OFFICE to DayType.SATURDAY_BONUS
            }

            val existing = state.workDays.find { it.date == date }
            val workDayId = workDayRepository.saveWorkDay(
                WorkDay(
                    id = existing?.id ?: 0,
                    date = date,
                    location = location,
                    dayType = dayType,
                    isPlanned = true
                )
            )

            if (dayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS)) {
                existing?.timeBlocks?.forEach { workDayRepository.deleteTimeBlock(it) }
                val start = LocalTime.of(8, 0)
                val end = start.plusMinutes(state.settings.dailyWorkMinutes.toLong())
                workDayRepository.saveTimeBlock(
                    TimeBlock(workDayId = workDayId, startTime = start, endTime = end, isDuration = true, location = location)
                )
            }
        }
    }

    fun removePlan(date: LocalDate) {
        viewModelScope.launch {
            val existing = _uiState.value.workDays.find { it.date == date }
            if (existing != null && existing.isPlanned) {
                workDayRepository.deleteWorkDay(existing)
            }
        }
    }

    fun deleteWorkDay(date: LocalDate) {
        viewModelScope.launch {
            val existing = _uiState.value.workDays.find { it.date == date }
            if (existing != null) {
                workDayRepository.deleteWorkDay(existing)
                closeDayEditor()
            }
        }
    }

    fun openDayEditor(date: LocalDate) {
        if (PublicHolidays.isHoliday(date)) return
        _uiState.value = _uiState.value.copy(editingDate = date)
    }

    fun closeDayEditor() {
        _uiState.value = _uiState.value.copy(editingDate = null)
    }

    fun savePlannedHours(date: LocalDate, totalMinutes: Int) {
        viewModelScope.launch {
            val state = _uiState.value
            val existing = state.workDays.find { it.date == date }
            val planType = state.selectedPlanType

            val (location, dayType) = when {
                existing != null -> existing.location to existing.dayType
                else -> when (planType) {
                    PlanType.OFFICE -> WorkLocation.OFFICE to DayType.WORK
                    PlanType.HOME_OFFICE -> WorkLocation.HOME_OFFICE to DayType.WORK
                    PlanType.VACATION -> WorkLocation.HOME_OFFICE to DayType.VACATION
                    PlanType.SPECIAL_VACATION -> WorkLocation.HOME_OFFICE to DayType.SPECIAL_VACATION
                    PlanType.FLEX_DAY -> WorkLocation.HOME_OFFICE to DayType.FLEX_DAY
                    PlanType.SATURDAY_BONUS -> WorkLocation.OFFICE to DayType.SATURDAY_BONUS
                }
            }

            val workDayId = workDayRepository.saveWorkDay(
                WorkDay(
                    id = existing?.id ?: 0,
                    date = date,
                    location = location,
                    dayType = dayType,
                    isPlanned = true
                )
            )

            existing?.timeBlocks?.forEach { workDayRepository.deleteTimeBlock(it) }

            val start = LocalTime.of(8, 0)
            val end = start.plusMinutes(totalMinutes.toLong())
            workDayRepository.saveTimeBlock(
                TimeBlock(workDayId = workDayId, startTime = start, endTime = end, isDuration = true)
            )

            closeDayEditor()
        }
    }

    fun clearAllPlanned() {
        viewModelScope.launch {
            val plannedDays = _uiState.value.workDays.filter { it.isPlanned }
            plannedDays.forEach { workDayRepository.deleteWorkDay(it) }
        }
    }

    fun planRemainingAs(planType: PlanType) {
        viewModelScope.launch {
            val state = _uiState.value
            val month = state.yearMonth
            val existingDates = state.workDays.map { it.date }.toSet()

            val (location, dayType) = when (planType) {
                PlanType.OFFICE -> WorkLocation.OFFICE to DayType.WORK
                PlanType.HOME_OFFICE -> WorkLocation.HOME_OFFICE to DayType.WORK
                else -> return@launch
            }

            val settings = state.settings
            for (day in 1..month.lengthOfMonth()) {
                val date = month.atDay(day)
                if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) continue
                if (PublicHolidays.isHoliday(date)) continue
                if (date in existingDates) continue

                val workDayId = workDayRepository.saveWorkDay(
                    WorkDay(
                        date = date,
                        location = location,
                        dayType = dayType,
                        isPlanned = true
                    )
                )

                val start = LocalTime.of(8, 0)
                val end = start.plusMinutes(settings.dailyWorkMinutes.toLong())
                workDayRepository.saveTimeBlock(
                    TimeBlock(workDayId = workDayId, startTime = start, endTime = end, isDuration = true, location = location)
                )
            }
        }
    }
}
