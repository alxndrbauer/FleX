package com.flex.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flex.domain.events.DataChangeEvent
import com.flex.domain.events.DataChangeEventBus
import com.flex.domain.events.UndoEvent
import com.flex.domain.model.DayType
import com.flex.domain.model.FlextimeBalance
import com.flex.domain.model.QuotaStatus
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.DayWorkTimeResult
import com.flex.domain.usecase.GetMonthWorkDaysUseCase
import com.flex.domain.model.BreakCheckResult
import com.flex.domain.usecase.CheckBreakViolationUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.notification.BreakWarningScheduler
import com.flex.wearable.WearSyncHelper
import android.content.Context
import android.content.Intent
import com.flex.notification.WorkTimerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject

data class HomeUiState(
    val today: LocalDate = LocalDate.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val workDay: WorkDay? = null,
    val timeBlocks: List<TimeBlock> = emptyList(),
    val isClockRunning: Boolean = false,
    val selectedLocation: WorkLocation = WorkLocation.OFFICE,
    val selectedDayType: DayType = DayType.WORK,
    val dayWorkTime: DayWorkTimeResult = DayWorkTimeResult(0, 0, 0, false),
    val baseDayNetMinutes: Long = 0,
    val liveFlextimeDelta: Long = 0,
    val flextimeBalance: FlextimeBalance = FlextimeBalance(),
    val monthlyFlextimeBalance: FlextimeBalance = FlextimeBalance(),
    val quotaStatus: QuotaStatus = QuotaStatus(),
    val settings: Settings = Settings(),
    val effectiveQuotaPercent: Int = 40,
    val effectiveQuotaMinDays: Int = 8,
    val officeMinutes: Long = 0,
    val requiredOfficeMinutes: Long = 0,
    val breakCheckResult: BreakCheckResult = BreakCheckResult(emptyList(), skipped = false)
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workDayRepository: WorkDayRepository,
    private val settingsRepository: SettingsRepository,
    private val getMonthWorkDays: GetMonthWorkDaysUseCase,
    private val getSettings: GetSettingsUseCase,
    private val calculateDayWorkTime: CalculateDayWorkTimeUseCase,
    private val calculateFlextime: CalculateFlextimeUseCase,
    private val calculateQuota: CalculateQuotaUseCase,
    private val dataChangeEventBus: DataChangeEventBus,
    private val wearSyncHelper: WearSyncHelper,
    private val checkBreakViolation: CheckBreakViolationUseCase,
    private val breakWarningScheduler: BreakWarningScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _undoEvent = MutableSharedFlow<UndoEvent>()
    val undoEvent: SharedFlow<UndoEvent> = _undoEvent.asSharedFlow()

    private val _remainingMinutes = MutableStateFlow<Int?>(null)
    val remainingMinutes: StateFlow<Int?> = _remainingMinutes.asStateFlow()

    private fun computeRemainingMinutes(state: HomeUiState): Int? {
        val today = LocalDate.now()
        if (state.selectedDate != today) return null
        if (state.timeBlocks.isEmpty()) return null

        val now = LocalTime.now()
        val blocksForCalc = state.timeBlocks.map { block ->
            if (block.endTime == null) block.copy(endTime = now) else block
        }
        val result = calculateDayWorkTime(blocksForCalc)
        return (state.settings.dailyWorkMinutes - result.netMinutes).toInt().coerceAtLeast(0)
    }

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _refreshTrigger = MutableStateFlow(Unit)
    private val _localDayTypeOverride = MutableStateFlow<DayType?>(null)

    init {
        loadDayData()
        launchRemainingMinutesTicker()
        launchMidnightRefresh()
    }

    private fun launchMidnightRefresh() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                val currentDate = LocalDate.now()
                val millisUntilMidnight = java.time.Duration.between(
                    java.time.LocalDateTime.now(),
                    currentDate.plusDays(1).atStartOfDay()
                ).toMillis()
                delay(millisUntilMidnight + 500)
                if (_selectedDate.value == currentDate) {
                    _selectedDate.value = LocalDate.now()
                }
            }
        }
    }

    private fun launchRemainingMinutesTicker() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(30_000L)
                val state = _uiState.value
                if (state.isClockRunning) {
                    val now = LocalTime.now()
                    val blocksForCalc = state.timeBlocks.map { block ->
                        if (block.endTime == null) block.copy(endTime = now) else block
                    }
                    val liveWorkTime = calculateDayWorkTime(blocksForCalc)
                    val liveFlexDelta = liveWorkTime.netMinutes - state.baseDayNetMinutes
                    val liveBreakCheck = if (state.settings.breakWarningEnabled)
                        checkBreakViolation(blocksForCalc)
                    else BreakCheckResult(emptyList(), skipped = false)
                    _uiState.update { it.copy(dayWorkTime = liveWorkTime, liveFlextimeDelta = liveFlexDelta, breakCheckResult = liveBreakCheck) }
                }
                _remainingMinutes.value = computeRemainingMinutes(_uiState.value)
            }
        }
    }

    private fun loadDayData() {
        viewModelScope.launch {
            combine(
                dataChangeEventBus.events.onStart { emit(DataChangeEvent.WorkDayChanged) },
                _refreshTrigger,
                _selectedDate,
                _localDayTypeOverride
            ) { _, _, date, override -> Pair(date, override) }
                .flatMapLatest { (date, override) ->
                    val today = LocalDate.now()
                    val yearMonth = YearMonth.from(date)
                    val todayYearMonth = YearMonth.from(today)
                    combine(
                        workDayRepository.getWorkDay(date),
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
                        val rules = arr[3] as List<com.flex.domain.model.QuotaRule>
                        @Suppress("UNCHECKED_CAST")
                        val yearDays = arr[4] as List<WorkDay>

                        val rule = settingsRepository.getQuotaRuleForMonth(todayYearMonth, rules)
                        val qPercent = rule?.officeQuotaPercent ?: settings.officeQuotaPercent
                        val qDays = rule?.officeQuotaMinDays ?: settings.officeQuotaMinDays

                        val timeBlocks = workDay?.timeBlocks ?: emptyList()
                        val isRunning = timeBlocks.any { it.endTime == null }
                        val dayResult = calculateDayWorkTime(timeBlocks)
                        val breakCheckResult = if (settings.breakWarningEnabled)
                            checkBreakViolation(timeBlocks)
                        else
                            BreakCheckResult(emptyList(), skipped = false)
                        // Exclude planned days from calculations in current month.
                        // Also exclude today if it's a WORK day with no completed time blocks yet
                        // (running blocks count as 0 min, empty entries from auto-clockin etc.
                        // would wrongly deduct the full daily target before the day is over).
                        fun hasCompletedBlocks(day: WorkDay) = day.timeBlocks.any { it.endTime != null }
                        val actualMonthDays = monthDays.filter { !it.isPlanned }.mapNotNull { day ->
                            val resolved = if (day.date == today && workDay != null && date == today) workDay else day
                            if (resolved.date == today && resolved.dayType == DayType.WORK && !hasCompletedBlocks(resolved)) null
                            else resolved
                        }

                        // Cumulative flextime: all year's actual days (not planned), with today replaced
                        val actualYearDays = yearDays.filter { !it.isPlanned }.mapNotNull { day ->
                            val resolved = if (day.date == today && workDay != null && date == today) workDay else day
                            if (resolved.date == today && resolved.dayType == DayType.WORK && !hasCompletedBlocks(resolved)) null
                            else resolved
                        }
                        val flextime = calculateFlextime(actualYearDays, settings, todayYearMonth)
                        val monthlyFlextime = calculateFlextime(actualMonthDays, settings, yearMonth)
                        val quota = calculateQuota(actualMonthDays, settings, yearMonth, qPercent, qDays)

                        // Fixed monthly target, reduced by neutral days
                        val neutralTypes = setOf(DayType.VACATION, DayType.SPECIAL_VACATION, DayType.FLEX_DAY, DayType.SICK_DAY)
                        val neutralDayCount = actualMonthDays.count { it.dayType in neutralTypes }
                        val totalMin = (settings.monthlyWorkMinutes - neutralDayCount.toLong() * settings.dailyWorkMinutes).coerceAtLeast(0)
                        val requiredMin = (totalMin * qPercent / 100.0).toLong()

                        val workingDays = actualMonthDays.filter { it.dayType !in neutralTypes }
                        var officeMin = 0L
                        for (day in workingDays) {
                            val adjustedBlocks =
                                CalculateDayWorkTimeUseCase.adjustTimeBlocks(day.timeBlocks)
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

                        HomeUiState(
                            today = today,
                            selectedDate = date,
                            workDay = workDay,
                            timeBlocks = timeBlocks,
                            isClockRunning = isRunning,
                            selectedLocation = workDay?.location ?: WorkLocation.OFFICE,
                            selectedDayType = override ?: workDay?.dayType ?: DayType.WORK,
                            dayWorkTime = dayResult,
                            baseDayNetMinutes = dayResult.netMinutes,
                            liveFlextimeDelta = 0,
                            flextimeBalance = flextime,
                            monthlyFlextimeBalance = monthlyFlextime,
                            quotaStatus = quota,
                            settings = settings,
                            effectiveQuotaPercent = qPercent,
                            effectiveQuotaMinDays = qDays,
                            officeMinutes = officeMin,
                            requiredOfficeMinutes = requiredMin,
                            breakCheckResult = breakCheckResult
                        )
                    }
                }
                .collect { state ->
                    val wasRunning = _uiState.value.isClockRunning
                    _uiState.value = state
                    _remainingMinutes.value = computeRemainingMinutes(state)
                    // Restart service if clocked in and it was just loaded (first emission)
                    if (!wasRunning && state.isClockRunning && state.settings.workTimerNotificationEnabled) {
                        startWorkTimerService()
                    }
                }
        }
    }

    fun goToPreviousDay() {
        _localDayTypeOverride.value = null
        _selectedDate.value = _selectedDate.value.minusDays(1)
    }

    fun goToNextDay() {
        _localDayTypeOverride.value = null
        _selectedDate.value = _selectedDate.value.plusDays(1)
    }

    fun goToToday() {
        _localDayTypeOverride.value = null
        _selectedDate.value = LocalDate.now()
    }

    fun navigateToDate(date: LocalDate) {
        _localDayTypeOverride.value = null
        _selectedDate.value = date
    }

    fun clockIn() {
        viewModelScope.launch {
            val state = _uiState.value
            val now = LocalTime.now().withSecond(0).withNano(0)

            val workDayId = if (state.workDay == null) {
                workDayRepository.saveWorkDay(
                    WorkDay(
                        date = state.selectedDate,
                        location = state.selectedLocation,
                        dayType = state.selectedDayType
                    )
                )
            } else {
                val needsUpdate = state.workDay.isPlanned ||
                    state.workDay.dayType != state.selectedDayType ||
                    state.workDay.location != state.selectedLocation
                if (needsUpdate) {
                    workDayRepository.saveWorkDay(
                        state.workDay.copy(
                            isPlanned = false,
                            dayType = state.selectedDayType,
                            location = state.selectedLocation
                        )
                    )
                }
                state.workDay.id
            }

            workDayRepository.saveTimeBlock(
                TimeBlock(workDayId = workDayId, startTime = now, location = state.selectedLocation)
            )
            if (state.settings.breakWarningEnabled) {
                breakWarningScheduler.scheduleWarning(now)
            }
            if (state.settings.workTimerNotificationEnabled) {
                startWorkTimerService()
            }
            _localDayTypeOverride.value = null
            wearSyncHelper.push()
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
            breakWarningScheduler.cancelWarning()
            stopWorkTimerService()
            wearSyncHelper.push()
        }
    }

    private fun startWorkTimerService() {
        context.startForegroundService(Intent(context, WorkTimerService::class.java))
    }

    private fun stopWorkTimerService() {
        context.stopService(Intent(context, WorkTimerService::class.java))
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
        _localDayTypeOverride.value = dayType
    }

    fun saveManualEntry(startTime: LocalTime, endTime: LocalTime, location: WorkLocation) {
        viewModelScope.launch {
            val state = _uiState.value

            val workDayId = if (state.workDay == null) {
                workDayRepository.saveWorkDay(
                    WorkDay(
                        date = state.selectedDate,
                        location = state.selectedLocation,
                        dayType = state.selectedDayType
                    )
                )
            } else {
                val needsUpdate = state.workDay.isPlanned ||
                    state.workDay.dayType != state.selectedDayType ||
                    state.workDay.location != state.selectedLocation
                if (needsUpdate) {
                    workDayRepository.saveWorkDay(
                        state.workDay.copy(
                            isPlanned = false,
                            dayType = state.selectedDayType,
                            location = state.selectedLocation
                        )
                    )
                }
                state.workDay.id
            }

            workDayRepository.saveTimeBlock(
                TimeBlock(workDayId = workDayId, startTime = startTime, endTime = endTime, location = location)
            )
            _localDayTypeOverride.value = null
            wearSyncHelper.push()
        }
    }

    fun saveDurationEntry(totalMinutes: Int, location: WorkLocation) {
        viewModelScope.launch {
            val state = _uiState.value
            val start = LocalTime.of(8, 0)
            val end = start.plusMinutes(totalMinutes.toLong())

            val workDayId = if (state.workDay == null) {
                workDayRepository.saveWorkDay(
                    WorkDay(
                        date = state.selectedDate,
                        location = state.selectedLocation,
                        dayType = state.selectedDayType
                    )
                )
            } else {
                val needsUpdate = state.workDay.isPlanned ||
                    state.workDay.dayType != state.selectedDayType ||
                    state.workDay.location != state.selectedLocation
                if (needsUpdate) {
                    workDayRepository.saveWorkDay(
                        state.workDay.copy(
                            isPlanned = false,
                            dayType = state.selectedDayType,
                            location = state.selectedLocation
                        )
                    )
                }
                state.workDay.id
            }

            workDayRepository.saveTimeBlock(
                TimeBlock(workDayId = workDayId, startTime = start, endTime = end, isDuration = true, location = location)
            )
            _localDayTypeOverride.value = null
            wearSyncHelper.push()
        }
    }

    fun updateTimeBlock(block: TimeBlock, startTime: LocalTime, endTime: LocalTime?, location: WorkLocation) {
        viewModelScope.launch {
            workDayRepository.saveTimeBlock(
                block.copy(startTime = startTime, endTime = endTime, location = location)
            )
            wearSyncHelper.push()
        }
    }

    fun deleteTimeBlock(timeBlock: TimeBlock) {
        viewModelScope.launch {
            val workDay = _uiState.value.workDay
            val isLastBlock = workDay != null && workDay.timeBlocks.all { it.id == timeBlock.id }
            workDayRepository.deleteTimeBlock(timeBlock)
            if (isLastBlock && workDay != null) {
                workDayRepository.deleteWorkDay(workDay)
            }
            wearSyncHelper.push()
            _undoEvent.emit(UndoEvent("Block gelöscht") {
                if (isLastBlock && workDay != null) {
                    val newId = workDayRepository.saveWorkDay(workDay.copy(id = 0L))
                    workDayRepository.saveTimeBlock(timeBlock.copy(id = 0L, workDayId = newId))
                } else {
                    workDayRepository.saveTimeBlock(timeBlock.copy(id = 0L))
                }
                wearSyncHelper.push()
            })
        }
    }

    fun saveDayType(dayType: DayType) {
        viewModelScope.launch {
            val state = _uiState.value
            workDayRepository.saveWorkDay(
                WorkDay(
                    id = state.workDay?.id ?: 0,
                    date = state.selectedDate,
                    location = state.selectedLocation,
                    dayType = dayType
                )
            )
            _localDayTypeOverride.value = null
            wearSyncHelper.push()
        }
    }

    fun deleteDay() {
        viewModelScope.launch {
            val workDay = _uiState.value.workDay ?: return@launch
            workDay.timeBlocks.forEach { workDayRepository.deleteTimeBlock(it) }
            workDayRepository.deleteWorkDay(workDay)
            _localDayTypeOverride.value = null
            wearSyncHelper.push()
        }
    }

    fun refreshFlextimeData() {
        _refreshTrigger.value = Unit
    }
}
