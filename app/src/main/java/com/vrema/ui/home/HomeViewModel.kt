package com.vrema.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vrema.domain.model.DayType
import com.vrema.domain.model.FlextimeBalance
import com.vrema.domain.model.QuotaStatus
import com.vrema.domain.model.Settings
import com.vrema.domain.model.TimeBlock
import com.vrema.domain.model.WorkDay
import com.vrema.domain.model.WorkLocation
import com.vrema.domain.usecase.CalculateDayWorkTimeUseCase
import com.vrema.domain.usecase.CalculateFlextimeUseCase
import com.vrema.domain.usecase.CalculateQuotaUseCase
import com.vrema.domain.usecase.DayWorkTimeResult
import com.vrema.domain.usecase.GetMonthWorkDaysUseCase
import com.vrema.domain.usecase.GetSettingsUseCase
import com.vrema.domain.repository.SettingsRepository
import com.vrema.domain.repository.WorkDayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject

data class HomeUiState(
    val today: LocalDate = LocalDate.now(),
    val workDay: WorkDay? = null,
    val timeBlocks: List<TimeBlock> = emptyList(),
    val isClockRunning: Boolean = false,
    val selectedLocation: WorkLocation = WorkLocation.OFFICE,
    val selectedDayType: DayType = DayType.WORK,
    val dayWorkTime: DayWorkTimeResult = DayWorkTimeResult(0, 0, 0, false),
    val flextimeBalance: FlextimeBalance = FlextimeBalance(),
    val quotaStatus: QuotaStatus = QuotaStatus(),
    val settings: Settings = Settings(),
    val effectiveQuotaPercent: Int = 40,
    val effectiveQuotaMinDays: Int = 8,
    val officeMinutes: Long = 0,
    val requiredOfficeMinutes: Long = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workDayRepository: WorkDayRepository,
    private val settingsRepository: SettingsRepository,
    private val getMonthWorkDays: GetMonthWorkDaysUseCase,
    private val getSettings: GetSettingsUseCase,
    private val calculateDayWorkTime: CalculateDayWorkTimeUseCase,
    private val calculateFlextime: CalculateFlextimeUseCase,
    private val calculateQuota: CalculateQuotaUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadTodayData()
    }

    private fun loadTodayData() {
        val today = LocalDate.now()
        val yearMonth = YearMonth.from(today)

        viewModelScope.launch {
            combine(
                workDayRepository.getWorkDay(today),
                getSettings(),
                getMonthWorkDays(yearMonth),
                settingsRepository.getQuotaRules(),
                workDayRepository.getWorkDaysForYear(today.year)
            ) { arr ->
                val workDay = arr[0] as WorkDay?
                val settings = arr[1] as Settings
                @Suppress("UNCHECKED_CAST")
                val monthDays = arr[2] as List<WorkDay>
                @Suppress("UNCHECKED_CAST")
                val rules = arr[3] as List<com.vrema.domain.model.QuotaRule>
                @Suppress("UNCHECKED_CAST")
                val yearDays = arr[4] as List<WorkDay>

                val rule = settingsRepository.getQuotaRuleForMonth(yearMonth, rules)
                val qPercent = rule?.officeQuotaPercent ?: settings.officeQuotaPercent
                val qDays = rule?.officeQuotaMinDays ?: settings.officeQuotaMinDays

                val timeBlocks = workDay?.timeBlocks ?: emptyList()
                val isRunning = timeBlocks.any { it.endTime == null }
                val dayResult = calculateDayWorkTime(timeBlocks)
                // Exclude planned days from calculations in current month
                val actualMonthDays = monthDays.filter { !it.isPlanned }.map { day ->
                    if (day.date == today && workDay != null) workDay else day
                }

                // Cumulative flextime: all year's actual days (not planned), with today replaced
                val actualYearDays = yearDays.filter { !it.isPlanned }.map { day ->
                    if (day.date == today && workDay != null) workDay else day
                }
                val flextime = calculateFlextime(actualYearDays, settings, yearMonth)
                val quota = calculateQuota(actualMonthDays, settings, yearMonth, qPercent, qDays)

                // Fixed monthly target, reduced by neutral days
                val neutralTypes = setOf(DayType.VACATION, DayType.SPECIAL_VACATION, DayType.FLEX_DAY)
                val neutralDayCount = actualMonthDays.count { it.dayType in neutralTypes }
                val totalMin = (settings.monthlyWorkMinutes - neutralDayCount.toLong() * settings.dailyWorkMinutes).coerceAtLeast(0)
                val requiredMin = (totalMin * qPercent / 100.0).toLong()

                val workingDays = actualMonthDays.filter { it.dayType !in neutralTypes }
                var officeMin = 0L
                for (day in workingDays) {
                    val r = calculateDayWorkTime(day.timeBlocks)
                    if (day.location == WorkLocation.OFFICE) officeMin += r.netMinutes
                }

                HomeUiState(
                    today = today,
                    workDay = workDay,
                    timeBlocks = timeBlocks,
                    isClockRunning = isRunning,
                    selectedLocation = workDay?.location ?: WorkLocation.OFFICE,
                    selectedDayType = workDay?.dayType ?: DayType.WORK,
                    dayWorkTime = dayResult,
                    flextimeBalance = flextime,
                    quotaStatus = quota,
                    settings = settings,
                    effectiveQuotaPercent = qPercent,
                    effectiveQuotaMinDays = qDays,
                    officeMinutes = officeMin,
                    requiredOfficeMinutes = requiredMin
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun clockIn() {
        viewModelScope.launch {
            val state = _uiState.value
            val now = LocalTime.now().withSecond(0).withNano(0)

            val workDayId = if (state.workDay == null) {
                workDayRepository.saveWorkDay(
                    WorkDay(
                        date = state.today,
                        location = state.selectedLocation,
                        dayType = state.selectedDayType
                    )
                )
            } else {
                // Reset isPlanned when user actually clocks in
                if (state.workDay.isPlanned) {
                    workDayRepository.saveWorkDay(state.workDay.copy(isPlanned = false))
                }
                state.workDay.id
            }

            workDayRepository.saveTimeBlock(
                TimeBlock(workDayId = workDayId, startTime = now)
            )
        }
    }

    fun clockOut() {
        viewModelScope.launch {
            val state = _uiState.value
            val runningBlock = state.timeBlocks.find { it.endTime == null } ?: return@launch
            val now = LocalTime.now().withSecond(0).withNano(0)

            workDayRepository.saveTimeBlock(
                runningBlock.copy(endTime = now)
            )
        }
    }

    fun setLocation(location: WorkLocation) {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.value = state.copy(selectedLocation = location)
            state.workDay?.let { workDay ->
                workDayRepository.saveWorkDay(workDay.copy(location = location))
            }
        }
    }

    fun setDayType(dayType: DayType) {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.value = state.copy(selectedDayType = dayType)
            state.workDay?.let { workDay ->
                workDayRepository.saveWorkDay(workDay.copy(dayType = dayType))
            }
        }
    }

    fun saveManualEntry(startTime: LocalTime, endTime: LocalTime) {
        viewModelScope.launch {
            val state = _uiState.value

            val workDayId = if (state.workDay == null) {
                workDayRepository.saveWorkDay(
                    WorkDay(
                        date = state.today,
                        location = state.selectedLocation,
                        dayType = state.selectedDayType
                    )
                )
            } else {
                if (state.workDay.isPlanned) {
                    workDayRepository.saveWorkDay(state.workDay.copy(isPlanned = false))
                }
                state.workDay.id
            }

            workDayRepository.saveTimeBlock(
                TimeBlock(workDayId = workDayId, startTime = startTime, endTime = endTime)
            )
        }
    }

    fun saveDurationEntry(totalMinutes: Int) {
        viewModelScope.launch {
            val state = _uiState.value
            val start = LocalTime.of(8, 0)
            val end = start.plusMinutes(totalMinutes.toLong())

            val workDayId = if (state.workDay == null) {
                workDayRepository.saveWorkDay(
                    WorkDay(
                        date = state.today,
                        location = state.selectedLocation,
                        dayType = state.selectedDayType
                    )
                )
            } else {
                if (state.workDay.isPlanned) {
                    workDayRepository.saveWorkDay(state.workDay.copy(isPlanned = false))
                }
                state.workDay.id
            }

            workDayRepository.saveTimeBlock(
                TimeBlock(workDayId = workDayId, startTime = start, endTime = end, isDuration = true)
            )
        }
    }

    fun deleteTimeBlock(timeBlock: TimeBlock) {
        viewModelScope.launch {
            workDayRepository.deleteTimeBlock(timeBlock)
        }
    }

    fun markAsNonWorkDay(dayType: DayType) {
        viewModelScope.launch {
            val state = _uiState.value
            workDayRepository.saveWorkDay(
                WorkDay(
                    id = state.workDay?.id ?: 0,
                    date = state.today,
                    location = state.selectedLocation,
                    dayType = dayType
                )
            )
        }
    }
}
