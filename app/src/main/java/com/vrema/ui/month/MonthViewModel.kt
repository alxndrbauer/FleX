package com.vrema.ui.month

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vrema.domain.events.DataChangeEvent
import com.vrema.domain.events.DataChangeEventBus
import com.vrema.domain.model.DayType
import com.vrema.domain.model.FlextimeBalance
import com.vrema.domain.model.PublicHolidays
import com.vrema.domain.model.QuotaStatus
import com.vrema.domain.model.Settings
import com.vrema.domain.model.TimeBlock
import com.vrema.domain.model.WorkDay
import com.vrema.domain.model.WorkLocation
import com.vrema.domain.repository.SettingsRepository
import com.vrema.domain.repository.WorkDayRepository
import com.vrema.domain.usecase.CalculateDayWorkTimeUseCase
import com.vrema.domain.usecase.CalculateFlextimeUseCase
import com.vrema.domain.usecase.CalculateQuotaUseCase
import com.vrema.domain.usecase.GetMonthWorkDaysUseCase
import com.vrema.domain.usecase.GetSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject

data class MonthUiState(
    val yearMonth: YearMonth = YearMonth.now(),
    val workDays: List<WorkDay> = emptyList(),
    val settings: Settings = Settings(),
    val editingDay: WorkDay? = null,
    val editingTimeBlocks: List<TimeBlock> = emptyList(),
    val prognosisQuota: QuotaStatus = QuotaStatus(),
    val prognosisFlextime: FlextimeBalance = FlextimeBalance(),
    val effectiveQuotaPercent: Int = 40,
    val effectiveQuotaMinDays: Int = 8,
    val officeMinutes: Long = 0,
    val requiredOfficeMinutes: Long = 0,
    val totalWorkMinutes: Long = 0,
    val netMinutesByDate: Map<LocalDate, Long> = emptyMap(),
    val hasPlannedDays: Boolean = false,
    val workedMinutesMonth: Long = 0,
    val targetMinutesMonth: Long = 0,
    val differenceMinutesMonth: Long = 0
)

@HiltViewModel
class MonthViewModel @Inject constructor(
    private val getMonthWorkDays: GetMonthWorkDaysUseCase,
    private val getSettings: GetSettingsUseCase,
    private val workDayRepository: WorkDayRepository,
    private val settingsRepository: SettingsRepository,
    private val calculateDayWorkTime: CalculateDayWorkTimeUseCase,
    private val calculateQuota: CalculateQuotaUseCase,
    private val calculateFlextime: CalculateFlextimeUseCase,
    private val dataChangeEventBus: DataChangeEventBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonthUiState())
    val uiState: StateFlow<MonthUiState> = _uiState.asStateFlow()

    private val _selectedMonth = MutableStateFlow(YearMonth.now())

    init {
        loadMonth()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun loadMonth() {
        viewModelScope.launch {
            combine(
                _selectedMonth,
                getSettings(),
                settingsRepository.getQuotaRules()
            ) { month, settings, rules ->
                Triple(month, settings, rules)
            }.flatMapLatest { (month, settings, rules) ->
                combine(
                    getMonthWorkDays(month),
                    workDayRepository.getWorkDaysForYear(month.year)
                ) { days, yearDays ->
                    arrayOf(month, settings, rules, days, yearDays)
                }
            }.collect { arr ->
                @Suppress("UNCHECKED_CAST")
                val month = arr[0] as YearMonth
                val settings = arr[1] as Settings
                val rules = arr[2] as List<com.vrema.domain.model.QuotaRule>
                val days = arr[3] as List<WorkDay>
                @Suppress("UNCHECKED_CAST")
                val yearDays = arr[4] as List<WorkDay>

                val rule = settingsRepository.getQuotaRuleForMonth(month, rules)
                val qPercent = rule?.officeQuotaPercent ?: settings.officeQuotaPercent
                val qDays = rule?.officeQuotaMinDays ?: settings.officeQuotaMinDays

                // For current/past months, exclude planned days from calculations
                val isCurrentOrPast = !month.isAfter(YearMonth.now())
                val daysForCalc = if (isCurrentOrPast) days.filter { !it.isPlanned } else days
                val hasPlanned = days.any { it.isPlanned }

                val prognosisDays = buildPrognosisDays(month, daysForCalc, settings)
                val quota = calculateQuota(prognosisDays, settings, month, qPercent, qDays)

                // Cumulative flextime: actual year days (before this month) + this month's prognosis
                val previousMonthsDays = yearDays.filter {
                    YearMonth.from(it.date).isBefore(month) && !it.isPlanned
                }
                val flextimeDays = previousMonthsDays + prognosisDays
                val flextime = calculateFlextime(flextimeDays, settings, month)

                // Fixed monthly target, reduced by neutral days (vacation, flex, etc.)
                val neutralTypes = setOf(DayType.VACATION, DayType.SPECIAL_VACATION, DayType.FLEX_DAY)
                val neutralDayCount = prognosisDays.count { it.dayType in neutralTypes }
                val totalMin = (settings.monthlyWorkMinutes - (neutralDayCount.toLong() * settings.dailyWorkMinutes)).coerceAtLeast(0)
                val requiredMin = (totalMin * qPercent / 100.0).toLong()

                val workingDays = prognosisDays.filter { it.dayType !in neutralTypes }
                var officeMin = 0L
                for (day in workingDays) {
                    val adjustedBlocks = CalculateDayWorkTimeUseCase.adjustTimeBlocks(day.timeBlocks)
                    val dayResult = calculateDayWorkTime(day.timeBlocks)
                    val totalGross = dayResult.grossMinutes
                    if (totalGross == 0L) continue
                    var dayOfficeGross = 0L
                    for (block in adjustedBlocks) {
                        val blockEnd = block.endTime ?: continue
                        val blockMin = java.time.Duration.between(block.startTime, blockEnd).toMinutes()
                        if (blockMin > 0 && block.location == WorkLocation.OFFICE) dayOfficeGross += blockMin
                    }
                    officeMin += dayOfficeGross * dayResult.netMinutes / totalGross
                }

                val netByDate = days.filter { !it.isPlanned || !isCurrentOrPast }.associate { day ->
                    day.date to calculateDayWorkTime(day.timeBlocks).netMinutes
                }

                // Calculate monthly worked hours (sum of WORK and SATURDAY_BONUS days only, excluding planned days)
                val workingDaysMonth = daysForCalc.filter {
                    it.dayType in listOf(
                        DayType.WORK,
                        DayType.SATURDAY_BONUS
                    )
                }
                val totalWorkMinutesMonth = workingDaysMonth.sumOf { day ->
                    calculateDayWorkTime(day.timeBlocks).netMinutes
                }

                // Calculate target work days for the month (Mon-Fri excluding holidays)
                var targetWorkDaysMonth = 0
                for (day in 1..month.lengthOfMonth()) {
                    val date = month.atDay(day)
                    if (date.dayOfWeek !in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                        && !PublicHolidays.isHoliday(date)
                    ) {
                        targetWorkDaysMonth++
                    }
                }
                val targetMinutesMonth = targetWorkDaysMonth.toLong() * settings.dailyWorkMinutes
                val differenceMinutesMonth = totalWorkMinutesMonth - targetMinutesMonth

                _uiState.value = _uiState.value.copy(
                    yearMonth = month,
                    workDays = days,
                    settings = settings.copy(officeQuotaPercent = qPercent, officeQuotaMinDays = qDays),
                    prognosisQuota = quota,
                    prognosisFlextime = flextime,
                    effectiveQuotaPercent = qPercent,
                    effectiveQuotaMinDays = qDays,
                    officeMinutes = officeMin,
                    requiredOfficeMinutes = requiredMin,
                    totalWorkMinutes = totalMin,
                    netMinutesByDate = netByDate,
                    hasPlannedDays = hasPlanned,
                    workedMinutesMonth = totalWorkMinutesMonth,
                    targetMinutesMonth = targetMinutesMonth,
                    differenceMinutesMonth = differenceMinutesMonth
                )
            }
        }
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
                // Use actual/planned data
                if (existing.timeBlocks.isEmpty() &&
                    existing.dayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS)
                ) {
                    // Planned work day without time blocks: assume full day
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
                // Unplanned weekday (not a holiday): default to Home-Office
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

    fun confirmPlannedDays() {
        viewModelScope.launch {
            workDayRepository.confirmPlannedDays(_selectedMonth.value)
        }
    }

    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        _selectedMonth.value = _selectedMonth.value.plusMonths(1)
    }

    fun selectDay(date: LocalDate) {
        if (PublicHolidays.isHoliday(date)) return
        viewModelScope.launch {
            val workDay = workDayRepository.getWorkDay(date).firstOrNull()
            _uiState.value = _uiState.value.copy(
                editingDay = workDay ?: WorkDay(date = date),
                editingTimeBlocks = workDay?.timeBlocks ?: emptyList()
            )
        }
    }

    fun clearEditing() {
        _uiState.value = _uiState.value.copy(editingDay = null, editingTimeBlocks = emptyList())
    }

    fun deleteDay(workDay: WorkDay) {
        viewModelScope.launch {
            workDay.timeBlocks.forEach { workDayRepository.deleteTimeBlock(it) }
            workDayRepository.deleteWorkDay(workDay)
            clearEditing()
        }
    }

    fun saveDay(
        date: LocalDate,
        location: WorkLocation,
        dayType: DayType,
        note: String?,
        timeBlocks: List<Pair<LocalTime, LocalTime>>,
        isDuration: Boolean = false
    ) {
        viewModelScope.launch {
            val existingDay = _uiState.value.editingDay
            val workDayId = workDayRepository.saveWorkDay(
                WorkDay(
                    id = existingDay?.id ?: 0,
                    date = date,
                    location = location,
                    dayType = dayType,
                    isPlanned = false,
                    note = note
                )
            )

            existingDay?.timeBlocks?.forEach { workDayRepository.deleteTimeBlock(it) }

            timeBlocks.forEach { (start, end) ->
                workDayRepository.saveTimeBlock(
                    TimeBlock(workDayId = workDayId, startTime = start, endTime = end, isDuration = isDuration, location = location)
                )
            }

            clearEditing()

            dataChangeEventBus.emit(DataChangeEvent.WorkDayChanged)
        }
    }
}
