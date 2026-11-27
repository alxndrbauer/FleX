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
import com.flex.domain.model.DEFAULT_WORK_DAYS
import com.flex.domain.model.getRuleForDate
import com.flex.domain.model.getRuleForMonth
import com.flex.domain.model.hasDailyWorkTarget
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.repository.observeYearClosingsOrEmpty
import com.flex.domain.events.UndoEvent
import com.flex.domain.usecase.BuildPrognosisDaysUseCase
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.GetMonthWorkDaysUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.domain.usecase.SyncPlannedDaysWithWorkTimeRuleUseCase
import com.flex.domain.usecase.YearContextResolver
import com.flex.domain.model.YearClosing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject

data class OfficeHoursDetail(
    val requiredOfficeMinutes: Long = 0,
    val plannedOfficeMinutes: Long = 0,
    val plannedTotalMinutes: Long = 0,
    val targetMonthlyMinutes: Long = 0
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
    val targetMonthlyHours: String get() {
        val h = targetMonthlyMinutes / 60
        val m = targetMonthlyMinutes % 60
        return if (m > 0) "${h}h ${m}min" else "${h}h"
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
    val workTimeRules: List<com.flex.domain.model.WorkTimeRule> = emptyList(),
    val quotaStatus: QuotaStatus = QuotaStatus(),
    val flextimeBalance: FlextimeBalance = FlextimeBalance(),
    val actualFlextimeMinutes: Long = 0L,
    val plannedContributionMinutes: Long = 0L,
    val officeHours: OfficeHoursDetail = OfficeHoursDetail(),
    val selectedPlanType: PlanType = PlanType.OFFICE,
    val editingDate: LocalDate? = null,
    val monthSummaries: List<MonthSummary> = emptyList(),
    val canSyncPlannedDays: Boolean = false,
    val hasOvertimeAvailable: Boolean = false,
    val overtimeMinutesAtMonthStart: Long = 0L,
    val overtimeMinutesAfterDate: Map<LocalDate, Long> = emptyMap(),
    val isSpecialVacationPaidOut: Boolean = false
) {
    /** Overtime balance available for an overtime day at [date]: balance after the previous day. */
    fun overtimeMinutesBefore(date: LocalDate): Long =
        overtimeMinutesAfterDate.entries
            .filter { it.key.isBefore(date) }
            .maxByOrNull { it.key }
            ?.value
            ?: overtimeMinutesAtMonthStart

    /** An overtime day can only be planned on days that have a work target at all. */
    fun hasDailyTarget(date: LocalDate): Boolean =
        hasDailyWorkTarget(date, settings.dailyWorkMinutes, workTimeRules.getRuleForDate(date))
}

enum class PlanType(val label: String) {
    OFFICE("Büro"),
    HOME_OFFICE("Home-Office"),
    VACATION("Urlaub"),
    SPECIAL_VACATION("Sonderurlaub"),
    FLEX_DAY("Gleittag"),
    SATURDAY_BONUS("Samstag+"),
    BUSINESS_TRIP("Dienstgang / Dienstreise"),
    OVERTIME_DAY("Überstundentag")
}

@HiltViewModel
class PlanningViewModel @Inject constructor(
    private val getMonthWorkDays: GetMonthWorkDaysUseCase,
    private val getSettings: GetSettingsUseCase,
    private val workDayRepository: WorkDayRepository,
    private val settingsRepository: SettingsRepository,
    private val calculateDayWorkTime: CalculateDayWorkTimeUseCase,
    private val calculateQuota: CalculateQuotaUseCase,
    private val calculateFlextime: CalculateFlextimeUseCase,
    private val buildPrognosisDays: BuildPrognosisDaysUseCase,
    private val syncPlannedDaysWithWorkTimeRuleUseCase: SyncPlannedDaysWithWorkTimeRuleUseCase,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val yearContextResolver: YearContextResolver = YearContextResolver(calculateFlextime)
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanningUiState())
    val uiState: StateFlow<PlanningUiState> = _uiState.asStateFlow()

    private val _undoEvent = MutableSharedFlow<UndoEvent>()
    val undoEvent: SharedFlow<UndoEvent> = _undoEvent.asSharedFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _selectedMonth = MutableStateFlow(YearMonth.now(clock).plusMonths(1))

    init {
        loadData()
        loadMonthSummaries()
    }

private data class PlanningConfig(
    val month: YearMonth,
    val settings: Settings,
    val quotaRules: List<QuotaRule>,
    val workTimeRules: List<com.flex.domain.model.WorkTimeRule>,
    val closings: List<YearClosing>
)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun loadData() {
        viewModelScope.launch {
            combine(
                _selectedMonth,
                getSettings(),
                settingsRepository.getQuotaRules(),
                settingsRepository.getWorkTimeRules(),
                settingsRepository.observeYearClosingsOrEmpty()
            ) { month, settings, rules, workTimeRules, closings ->
                PlanningConfig(month, settings, rules, workTimeRules, closings)
            }.flatMapLatest { (month, settings, rules, workTimeRules, closings) ->
                val closingsByYear = closings.associateBy { it.year }
                val extraYearsFlow = when {
                    month.year > settings.settingsYear -> {
                        if (month.year == settings.settingsYear + 1) {
                            runCatching { workDayRepository.getWorkDaysForYear(settings.settingsYear) }.getOrNull()
                                ?: flowOf(emptyList())
                        } else {
                            runCatching {
                                workDayRepository.getWorkDaysInRange(
                                    LocalDate.of(settings.settingsYear, 1, 1),
                                    LocalDate.of(month.year - 1, 12, 31)
                                )
                            }.getOrNull() ?: flowOf(emptyList())
                        }
                    }
                    month.year < settings.settingsYear && closingsByYear[month.year] == null -> {
                        val anchorYear = closingsByYear.keys
                            .filter { it > month.year && it < settings.settingsYear }
                            .minOrNull() ?: settings.settingsYear
                        if (anchorYear > month.year + 1) {
                            runCatching {
                                workDayRepository.getWorkDaysInRange(
                                    LocalDate.of(month.year + 1, 1, 1),
                                    LocalDate.of(anchorYear - 1, 12, 31)
                                )
                            }.getOrNull() ?: flowOf(emptyList())
                        } else {
                            flowOf(emptyList())
                        }
                    }
                    else -> flowOf(emptyList())
                }
                combine(
                    getMonthWorkDays(month),
                    workDayRepository.getWorkDaysForYear(month.year),
                    extraYearsFlow
                ) { days, targetYearDays, extraDays ->
                    arrayOf(month, settings, rules, workTimeRules, closingsByYear, days, targetYearDays, extraDays)
                }
            }.collect { arr ->
                @Suppress("UNCHECKED_CAST")
                val month = arr[0] as YearMonth
                val settings = arr[1] as Settings
                @Suppress("UNCHECKED_CAST")
                val rules = arr[2] as List<QuotaRule>
                @Suppress("UNCHECKED_CAST")
                val workTimeRules = arr[3] as List<com.flex.domain.model.WorkTimeRule>
                @Suppress("UNCHECKED_CAST")
                val closingsByYear = arr[4] as Map<Int, YearClosing>
                @Suppress("UNCHECKED_CAST")
                val days = arr[5] as List<WorkDay>
                @Suppress("UNCHECKED_CAST")
                val targetYearDays = arr[6] as List<WorkDay>
                @Suppress("UNCHECKED_CAST")
                val extraDays = arr[7] as List<WorkDay>
                val extraByYear = extraDays.groupBy { it.date.year }
                val (effectiveSettings, yearDays) = yearContextResolver.buildFlextimeInputForYear(
                    targetYear = month.year,
                    settings = settings,
                    closingsByYear = closingsByYear,
                    targetYearWorkDays = targetYearDays,
                    otherYearsWorkDaysLookup = { y -> extraByYear[y].orEmpty() },
                    workTimeRules = workTimeRules
                )

                    val rule = settingsRepository.getQuotaRuleForMonth(month, rules)
                    val qPercent = rule?.officeQuotaPercent ?: settings.officeQuotaPercent
                    val qDays = rule?.officeQuotaMinDays ?: settings.officeQuotaMinDays
                    val resolvedDays = resolveRunningBlocks(days)
                    val prognosisDays = buildPrognosisDays(month, resolvedDays, settings, workTimeRules)
                    val quota = calculateQuota(prognosisDays, settings, month, qPercent, qDays, workTimeRules)

                    // Cumulative flextime: previous months' actual data + this month's prognosis
                    val previousMonthsDays = yearDays.filter {
                        YearMonth.from(it.date).isBefore(month) && !it.isPlanned
                    }
                    val flextimeDays = previousMonthsDays + prognosisDays
                    val flextime = calculateFlextime(flextimeDays, effectiveSettings, month, workTimeRules)

                    // Overtime balance before each prognosis day, used to gate overtime days
                    val monthStartOvertime = calculateFlextime(previousMonthsDays, effectiveSettings, null, workTimeRules).overtimeMinutes
                    val overtimeAfterByDate = mutableMapOf<LocalDate, Long>()
                    var runningOvertime = monthStartOvertime
                    for (day in prognosisDays.sortedBy { it.date }) {
                        runningOvertime = calculateFlextime(
                            listOf(day),
                            effectiveSettings.copy(initialFlextimeMinutes = 0, initialOvertimeMinutes = runningOvertime.toInt()),
                            null,
                            workTimeRules
                        ).overtimeMinutes
                        overtimeAfterByDate[day.date] = runningOvertime
                    }

                    val actualRecordedDays = previousMonthsDays + days.filter { !it.isPlanned }
                    val actualFlextime = calculateFlextime(actualRecordedDays, effectiveSettings, null, workTimeRules)
                    val actualMinutes = actualFlextime.totalMinutes
                    val plannedContribution = flextime.totalMinutes - actualMinutes

                    val officeHours = quota.toOfficeHoursDetail()
                    val isSpecialVacationBlocked = effectiveSettings.specialVacationPaidOut
                    val hasOvertimeAvailable = monthStartOvertime > 0 || overtimeAfterByDate.values.any { it > 0 }
                    val currentSelectedType = _uiState.value.selectedPlanType
                    val selectedType = when {
                        isSpecialVacationBlocked && currentSelectedType == PlanType.SPECIAL_VACATION -> PlanType.OFFICE
                        !hasOvertimeAvailable && currentSelectedType == PlanType.OVERTIME_DAY -> PlanType.OFFICE
                        else -> currentSelectedType
                    }
                    val canSync = checkCanSyncPlannedDays(days, month, workTimeRules, settings)
                    _uiState.value = _uiState.value.copy(
                        yearMonth = month,
                        workDays = days,
                        settings = settings.copy(officeQuotaPercent = qPercent, officeQuotaMinDays = qDays),
                        workTimeRules = workTimeRules,
                        quotaStatus = quota,
                        flextimeBalance = flextime,
                        actualFlextimeMinutes = actualMinutes,
                        plannedContributionMinutes = plannedContribution,
                        officeHours = officeHours,
                        selectedPlanType = selectedType,
                        canSyncPlannedDays = canSync,
                        hasOvertimeAvailable = hasOvertimeAvailable,
                        overtimeMinutesAtMonthStart = monthStartOvertime,
                        overtimeMinutesAfterDate = overtimeAfterByDate,
                        isSpecialVacationPaidOut = effectiveSettings.specialVacationPaidOut
                    )
                    refreshMonthSummaries(settings, rules, workTimeRules)
                }
        }
    }

    private fun loadMonthSummaries() {
        viewModelScope.launch {
            combine(
                getSettings(),
                settingsRepository.getQuotaRules(),
                settingsRepository.getWorkTimeRules()
            ) { settings, rules, workTimeRules -> Triple(settings, rules, workTimeRules) }
                .collect { (settings, rules, workTimeRules) ->
                    refreshMonthSummaries(settings, rules, workTimeRules)
                }
        }
    }

    private suspend fun refreshMonthSummaries(
        settings: Settings,
        rules: List<QuotaRule>,
        workTimeRules: List<com.flex.domain.model.WorkTimeRule>
    ) {
        val currentMonth = YearMonth.now(clock)
        val summaries = mutableListOf<MonthSummary>()

        for (offset in 0L..11L) {
            val month = currentMonth.plusMonths(offset)
            val rule = settingsRepository.getQuotaRuleForMonth(month, rules)
            val qPercent = rule?.officeQuotaPercent ?: settings.officeQuotaPercent
            val qDays = rule?.officeQuotaMinDays ?: settings.officeQuotaMinDays
            val days = getMonthWorkDays(month).firstOrNull() ?: emptyList()
            val resolvedDays = resolveRunningBlocks(days)
            val prognosisDays = buildPrognosisDays(month, resolvedDays, settings, workTimeRules)
            val quota = calculateQuota(prognosisDays, settings, month, qPercent, qDays, workTimeRules)
            val officeHours = quota.toOfficeHoursDetail()

            val plannedDays = days.count { it.isPlanned }
            val officeDays = days.count { (it.dayType == DayType.WORK && it.location == WorkLocation.OFFICE) || it.dayType == DayType.BUSINESS_TRIP }
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

    private fun resolveRunningBlocks(days: List<WorkDay>): List<WorkDay> {
        val today = LocalDate.now(clock)
        val now = LocalTime.now(clock).withSecond(0).withNano(0)
        return days.map { day ->
            if (day.date == today && day.timeBlocks.any { it.endTime == null }) {
                day.copy(timeBlocks = day.timeBlocks.map { block ->
                    if (block.endTime == null) block.copy(endTime = now) else block
                })
            } else {
                day
            }
        }
    }

    private fun QuotaStatus.toOfficeHoursDetail(): OfficeHoursDetail = OfficeHoursDetail(
        requiredOfficeMinutes = requiredOfficeMinutes,
        plannedOfficeMinutes = officeMinutes,
        plannedTotalMinutes = totalWorkedMinutes,
        targetMonthlyMinutes = baseMonthlyTargetMinutes
    )

    fun previousMonth() {
        val min = YearMonth.now(clock)
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
        val state = _uiState.value
        if (type == PlanType.SPECIAL_VACATION && state.isSpecialVacationPaidOut) {
            return
        }
        if (type == PlanType.OVERTIME_DAY && !state.hasOvertimeAvailable) {
            return
        }
        _uiState.value = _uiState.value.copy(selectedPlanType = type)
    }

    fun planDay(date: LocalDate) {
        if (PublicHolidays.isHoliday(date)) return
        viewModelScope.launch {
            val state = _uiState.value
            val planType = state.selectedPlanType
            if (planType == PlanType.SPECIAL_VACATION && state.isSpecialVacationPaidOut) {
                return@launch
            }

            val existing = state.workDays.find { it.date == date }

            // An overtime day needs a daily target and a positive overtime balance before that date.
            // An already planned overtime day stays editable.
            if (planType == PlanType.OVERTIME_DAY && existing?.dayType != DayType.OVERTIME_DAY) {
                if (!state.hasDailyTarget(date)) {
                    _messages.emit("Überstundentag nicht möglich: an diesem Tag kein Tagessoll")
                    return@launch
                }
                if (state.overtimeMinutesBefore(date) <= 0) {
                    _messages.emit("Überstundentag nicht möglich: kein Überstundenguthaben vorhanden")
                    return@launch
                }
            }

            val (location, dayType) = when (planType) {
                PlanType.OFFICE -> WorkLocation.OFFICE to DayType.WORK
                PlanType.HOME_OFFICE -> WorkLocation.HOME_OFFICE to DayType.WORK
                PlanType.VACATION -> WorkLocation.HOME_OFFICE to DayType.VACATION
                PlanType.SPECIAL_VACATION -> WorkLocation.HOME_OFFICE to DayType.SPECIAL_VACATION
                PlanType.FLEX_DAY -> WorkLocation.HOME_OFFICE to DayType.FLEX_DAY
                PlanType.SATURDAY_BONUS -> WorkLocation.OFFICE to DayType.SATURDAY_BONUS
                PlanType.BUSINESS_TRIP -> WorkLocation.OFFICE to DayType.BUSINESS_TRIP
                PlanType.OVERTIME_DAY -> WorkLocation.HOME_OFFICE to DayType.OVERTIME_DAY
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

            if (dayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS, DayType.BUSINESS_TRIP)) {
                existing?.timeBlocks?.forEach { workDayRepository.deleteTimeBlock(it) }
                val dailyTarget = settingsRepository.getWorkTimeRuleForDate(date, state.workTimeRules)?.dailyWorkMinutes
                    ?: state.settings.dailyWorkMinutes
                val start = state.settings.defaultStartTime
                val end = start.plusMinutes(dailyTarget.toLong())
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
                _undoEvent.emit(UndoEvent("Plan entfernt") {
                    val newId = workDayRepository.saveWorkDay(existing.copy(id = 0L))
                    existing.timeBlocks.forEach { workDayRepository.saveTimeBlock(it.copy(id = 0L, workDayId = newId)) }
                })
            }
        }
    }

    fun deleteWorkDay(date: LocalDate) {
        viewModelScope.launch {
            val existing = _uiState.value.workDays.find { it.date == date }
            if (existing != null) {
                workDayRepository.deleteWorkDay(existing)
                closeDayEditor()
                _undoEvent.emit(UndoEvent("Eintrag gelöscht") {
                    val newId = workDayRepository.saveWorkDay(existing.copy(id = 0L))
                    existing.timeBlocks.forEach { workDayRepository.saveTimeBlock(it.copy(id = 0L, workDayId = newId)) }
                })
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
            val existing = _uiState.value.workDays.find { it.date == date }
            val isReal = existing != null && !existing.isPlanned
            val workDayId = workDayRepository.saveWorkDay(
                WorkDay(
                    id = existing?.id ?: 0,
                    date = date,
                    location = existing?.location ?: WorkLocation.HOME_OFFICE,
                    dayType = existing?.dayType ?: DayType.WORK,
                    isPlanned = !isReal,
                    note = existing?.note ?: ""
                )
            )

            existing?.timeBlocks?.forEach { workDayRepository.deleteTimeBlock(it) }

            val start = _uiState.value.settings.defaultStartTime
            val end = start.plusMinutes(totalMinutes.toLong())
            workDayRepository.saveTimeBlock(
                TimeBlock(workDayId = workDayId, startTime = start, endTime = end, isDuration = true, location = existing?.location ?: WorkLocation.HOME_OFFICE)
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

    fun planRemainingAs(planType: PlanType) = applyStandardMonth(planType)

    fun applyStandardMonth(planType: PlanType) {
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
                val rule = state.workTimeRules.getRuleForDate(date)
                val activeWorkDays = rule?.workDays ?: DEFAULT_WORK_DAYS
                if (date.dayOfWeek !in activeWorkDays) continue
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

                val dailyTarget = settingsRepository.getWorkTimeRuleForDate(date, state.workTimeRules)?.dailyWorkMinutes
                    ?: settings.dailyWorkMinutes
                val start = settings.defaultStartTime
                val end = start.plusMinutes(dailyTarget.toLong())
                workDayRepository.saveTimeBlock(
                    TimeBlock(workDayId = workDayId, startTime = start, endTime = end, isDuration = true, location = location)
                )
            }
        }
    }

    suspend fun getAffectedPlannedDaysCount(): Int {
        return syncPlannedDaysWithWorkTimeRuleUseCase.countAffectedPlannedDays(_selectedMonth.value, _selectedMonth.value)
    }

    private fun checkCanSyncPlannedDays(
        days: List<WorkDay>,
        month: YearMonth,
        workTimeRules: List<com.flex.domain.model.WorkTimeRule>,
        settings: Settings
    ): Boolean {
        val plannedWorkDays = days.filter {
            it.isPlanned && it.dayType in SyncPlannedDaysWithWorkTimeRuleUseCase.PLANNED_WORK_DAY_TYPES
        }
        if (plannedWorkDays.isEmpty()) return false

        val rule = workTimeRules.getRuleForMonth(month)
        val activeWorkDays = rule?.workDays ?: DEFAULT_WORK_DAYS
        val targetDailyMinutes = rule?.dailyWorkMinutes ?: settings.dailyWorkMinutes

        if (plannedWorkDays.any { it.date.dayOfWeek !in activeWorkDays }) return true

        val knownStandardDurations = (workTimeRules.map { it.dailyWorkMinutes } + settings.dailyWorkMinutes).toSet()
        val nonTargetDurations = plannedWorkDays
            .map { calculateDayWorkTime(it.timeBlocks).netMinutes.toInt() }
            .filter { it != targetDailyMinutes }

        if (nonTargetDurations.isEmpty()) return false

        val candidateGroups = nonTargetDurations.groupingBy { it }.eachCount()
        return candidateGroups.any { (duration, count) ->
            duration in knownStandardDurations || count > 1
        }
    }

    fun syncPlannedDays(removeNonWorkDays: Boolean = false) {
        viewModelScope.launch {
            val month = _selectedMonth.value
            val state = _uiState.value
            val rule = state.workTimeRules.getRuleForMonth(month)
            val targetDailyMinutes = rule?.dailyWorkMinutes ?: state.settings.dailyWorkMinutes

            val plannedWorkDays = state.workDays.filter {
                it.isPlanned && it.dayType in SyncPlannedDaysWithWorkTimeRuleUseCase.PLANNED_WORK_DAY_TYPES
            }
            val knownStandardDurations = (state.workTimeRules.map { it.dailyWorkMinutes } + state.settings.dailyWorkMinutes).toSet()

            val nonTargetDurations = plannedWorkDays
                .map { calculateDayWorkTime(it.timeBlocks).netMinutes.toInt() }
                .filter { it != targetDailyMinutes }

            val candidateGroups = nonTargetDurations
                .groupingBy { it }
                .eachCount()

            val previousDailyMinutes = candidateGroups
                .filter { (duration, count) -> duration in knownStandardDurations || count > 1 }
                .maxByOrNull { it.value }
                ?.key

            val result = syncPlannedDaysWithWorkTimeRuleUseCase.syncMonth(
                month = month,
                removeNonWorkDays = removeNonWorkDays,
                previousDailyMinutes = previousDailyMinutes
            )

            val message = result.formatFeedbackMessage()
            _undoEvent.emit(UndoEvent(message))
        }
    }
}
