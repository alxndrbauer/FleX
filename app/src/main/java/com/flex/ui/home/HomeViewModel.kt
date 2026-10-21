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
import com.flex.domain.model.getDailyWorkTargetMinutes
import com.flex.domain.model.ProjectedEndTime
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateProjectedEndTimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.DayWorkTimeResult
import com.flex.domain.usecase.GetMonthWorkDaysUseCase
import com.flex.domain.model.BreakCheckResult
import com.flex.domain.usecase.CheckBreakViolationUseCase
import com.flex.domain.usecase.CheckTimeBlockOverlapUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.domain.usecase.AutoBookPlannedDaysUseCase
import com.flex.BuildConfig
import com.flex.data.local.WhatsNewPreferences
import com.flex.notification.BreakWarningScheduler
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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
    val baseFlextimeBalance: FlextimeBalance = FlextimeBalance(),
    val selectedDateFlextimeBalance: FlextimeBalance = FlextimeBalance(),
    val monthlyFlextimeBalance: FlextimeBalance = FlextimeBalance(),
    val liveFlextimeBalance: FlextimeBalance? = null,
    val liveMonthlyFlextimeBalance: FlextimeBalance? = null,
    val quotaStatus: QuotaStatus = QuotaStatus(),
    val settings: Settings = Settings(),
    val effectiveQuotaPercent: Int = 40,
    val effectiveQuotaMinDays: Int = 8,
    val officeMinutes: Long = 0,
    val requiredOfficeMinutes: Long = 0,
    val breakCheckResult: BreakCheckResult = BreakCheckResult(emptyList(), skipped = false),
    val permissionIssues: List<String> = emptyList(),
    val hasTimeBlockOverlap: Boolean = false,
    val dailyTargetMinutes: Int = 426,
    val projectedEndTime: ProjectedEndTime? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val workDayRepository: WorkDayRepository,
    private val settingsRepository: SettingsRepository,
    private val getMonthWorkDays: GetMonthWorkDaysUseCase,
    private val getSettings: GetSettingsUseCase,
    private val calculateDayWorkTime: CalculateDayWorkTimeUseCase,
    private val calculateFlextime: CalculateFlextimeUseCase,
    private val calculateQuota: CalculateQuotaUseCase,
    private val dataChangeEventBus: DataChangeEventBus,
    private val checkBreakViolation: CheckBreakViolationUseCase,
    private val breakWarningScheduler: BreakWarningScheduler,
    private val whatsNewPreferences: WhatsNewPreferences,
    private val backupPreferences: com.flex.data.backup.BackupPreferences,
    private val autoBookPlannedDaysUseCase: AutoBookPlannedDaysUseCase,
    private val checkTimeBlockOverlap: CheckTimeBlockOverlapUseCase = CheckTimeBlockOverlapUseCase(),
    private val calculateProjectedEndTime: CalculateProjectedEndTimeUseCase = CalculateProjectedEndTimeUseCase()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _undoEvent = MutableSharedFlow<UndoEvent>()
    val undoEvent: SharedFlow<UndoEvent> = _undoEvent.asSharedFlow()

    private val _whatsNewEvent = MutableSharedFlow<String>()
    val whatsNewEvent: SharedFlow<String> = _whatsNewEvent.asSharedFlow()

    private val _remainingMinutes = MutableStateFlow<Int?>(null)
    val remainingMinutes: StateFlow<Int?> = _remainingMinutes.asStateFlow()

    private var cachedWorkTimeRules: List<WorkTimeRule> = emptyList()
    private var cachedMonthDays: List<WorkDay> = emptyList()
    private var cachedYearDays: List<WorkDay> = emptyList()

    private fun computeRemainingMinutes(state: HomeUiState, workTimeRules: List<WorkTimeRule> = cachedWorkTimeRules): Int? {
        val today = LocalDate.now()
        if (state.selectedDate != today) return null
        if (state.timeBlocks.isEmpty()) return null

        val now = LocalTime.now()
        val blocksForCalc = state.timeBlocks.map { block ->
            if (block.endTime == null) block.copy(endTime = now) else block
        }
        val isBusinessTrip = state.selectedDayType == DayType.BUSINESS_TRIP
        val result = if (isBusinessTrip) calculateDayWorkTime(blocksForCalc, isBusinessTrip = true) else calculateDayWorkTime(blocksForCalc)
        val rule = settingsRepository.getWorkTimeRuleForDate(today, workTimeRules)
        val dailyTarget = getDailyWorkTargetMinutes(
            today,
            state.selectedDayType,
            state.settings.dailyWorkMinutes,
            rule
        )
        return (dailyTarget - result.netMinutes).toInt().coerceAtLeast(0)
    }

    private fun computeProjectedEndTime(state: HomeUiState, workTimeRules: List<WorkTimeRule> = cachedWorkTimeRules): ProjectedEndTime? {
        val today = LocalDate.now()
        if (!state.isClockRunning || state.selectedDate != today || state.timeBlocks.isEmpty()) return null
        val rule = settingsRepository.getWorkTimeRuleForDate(today, workTimeRules)
        val dailyTarget = getDailyWorkTargetMinutes(
            today,
            state.selectedDayType,
            state.settings.dailyWorkMinutes,
            rule
        )
        if (dailyTarget <= 0) return null
        return calculateProjectedEndTime(
            timeBlocks = state.timeBlocks,
            dailyTargetMinutes = dailyTarget,
            now = LocalTime.now(),
            isBusinessTrip = state.selectedDayType == DayType.BUSINESS_TRIP
        )
    }

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _refreshTrigger = MutableStateFlow(Unit)
    private val _localDayTypeOverride = MutableStateFlow<DayType?>(null)

    init {
        autoBookPlannedDays()
        loadDayData()
        launchRemainingMinutesTicker()
        launchMidnightRefresh()
        checkWhatsNew()
    }

    private fun checkWhatsNew() {
        val lastSeen = whatsNewPreferences.getLastSeenVersionCode()
        if (lastSeen > 0 && BuildConfig.VERSION_CODE > lastSeen) {
            viewModelScope.launch {
                _whatsNewEvent.emit(BuildConfig.VERSION_NAME)
            }
        }
        whatsNewPreferences.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
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
                autoBookPlannedDays()
                if (_selectedDate.value == currentDate) {
                    _selectedDate.value = LocalDate.now()
                }
            }
        }
    }

    fun updateLiveWorkTime() {
        val state = _uiState.value
        val today = LocalDate.now()
        if (state.isClockRunning && state.selectedDate == today) {
            val now = LocalTime.now()
            val blocksForCalc = state.timeBlocks.map { block ->
                if (block.endTime == null) block.copy(endTime = now) else block
            }
            val isBusinessTrip = state.selectedDayType == DayType.BUSINESS_TRIP
            val liveWorkTime = if (isBusinessTrip) calculateDayWorkTime(blocksForCalc, isBusinessTrip = true) else calculateDayWorkTime(blocksForCalc)
            val liveFlexDelta = liveWorkTime.netMinutes - state.baseDayNetMinutes
            val liveBreakCheck = if (state.settings.breakWarningEnabled) {
                if (isBusinessTrip) checkBreakViolation(blocksForCalc, isBusinessTrip = true)
                else checkBreakViolation(blocksForCalc)
            } else BreakCheckResult(emptyList(), skipped = false)

            val todayWithNow = (state.workDay ?: WorkDay(date = today, location = state.selectedLocation, dayType = state.selectedDayType))
                .copy(dayType = state.selectedDayType, timeBlocks = blocksForCalc)
            val todayYearMonth = YearMonth.from(today)
            val selectedYearMonth = YearMonth.from(state.selectedDate)
            val liveMonthDays = cachedMonthDays.filter { !it.isPlanned && it.date != today } + listOf(todayWithNow)
            val liveYearDays = cachedYearDays.filter { !it.isPlanned && it.date != today } + listOf(todayWithNow)
            val liveFlextime = calculateFlextime(liveYearDays, state.settings, todayYearMonth, cachedWorkTimeRules)
            val liveMonthlyFlextime = calculateFlextime(liveMonthDays, state.settings, selectedYearMonth, cachedWorkTimeRules)
            val liveOverlap = checkTimeBlockOverlap(state.timeBlocks, now)

            _uiState.update {
                it.copy(
                    dayWorkTime = liveWorkTime,
                    liveFlextimeDelta = liveFlexDelta,
                    breakCheckResult = liveBreakCheck,
                    liveFlextimeBalance = liveFlextime,
                    liveMonthlyFlextimeBalance = liveMonthlyFlextime,
                    selectedDateFlextimeBalance = if (it.selectedDate == today) liveFlextime else it.selectedDateFlextimeBalance,
                    hasTimeBlockOverlap = liveOverlap,
                    projectedEndTime = computeProjectedEndTime(it)
                )
            }
        } else if (_uiState.value.projectedEndTime != null) {
            _uiState.update { it.copy(projectedEndTime = null) }
        }
        _remainingMinutes.value = computeRemainingMinutes(_uiState.value)
    }

    private fun launchRemainingMinutesTicker() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(15_000L)
                updateLiveWorkTime()
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
                    val currentYearDaysFlow = workDayRepository.getWorkDaysForYear(today.year)
                    val selectedYearDaysFlow = if (date.year == today.year) {
                        currentYearDaysFlow
                    } else {
                        workDayRepository.getWorkDaysForYear(date.year)
                    }
                    combine(
                        workDayRepository.getWorkDay(date),
                        getSettings(),
                        getMonthWorkDays(yearMonth),
                        settingsRepository.getQuotaRules(),
                        currentYearDaysFlow,
                        selectedYearDaysFlow,
                        settingsRepository.getWorkTimeRules()
                    ) { arr ->
                        val workDay = arr[0] as WorkDay?
                        val settings = arr[1] as Settings
                        @Suppress("UNCHECKED_CAST")
                        val monthDays = arr[2] as List<WorkDay>
                        @Suppress("UNCHECKED_CAST")
                        val rules = arr[3] as List<com.flex.domain.model.QuotaRule>
                        @Suppress("UNCHECKED_CAST")
                        val currentYearDays = arr[4] as List<WorkDay>
                        @Suppress("UNCHECKED_CAST")
                        val yearDays = arr[5] as List<WorkDay>
                        @Suppress("UNCHECKED_CAST")
                        val workTimeRules = arr[6] as List<com.flex.domain.model.WorkTimeRule>

                        val rule = settingsRepository.getQuotaRuleForMonth(yearMonth, rules)
                        val qPercent = rule?.officeQuotaPercent ?: settings.officeQuotaPercent
                        val qDays = rule?.officeQuotaMinDays ?: settings.officeQuotaMinDays

                        val rawDayType = override ?: workDay?.dayType ?: DayType.WORK
                        val selectedDayType = if (rawDayType == DayType.SPECIAL_VACATION && isSpecialVacationBlocked(date, settings)) {
                            DayType.WORK
                        } else {
                            rawDayType
                        }
                        val isBusinessTrip = selectedDayType == DayType.BUSINESS_TRIP

                        val timeBlocks = workDay?.timeBlocks ?: emptyList()
                        val isRunning = timeBlocks.any { it.endTime == null }
                        val baseDayResult = if (isBusinessTrip) calculateDayWorkTime(timeBlocks, isBusinessTrip = true) else calculateDayWorkTime(timeBlocks)
                        val hasOverlap = checkTimeBlockOverlap(timeBlocks)
                        val isToday = date == today
                        val now = LocalTime.now()
                        val blocksForCalc = if (isRunning && isToday) {
                            timeBlocks.map { block ->
                                if (block.endTime == null) block.copy(endTime = now) else block
                            }
                        } else {
                            timeBlocks
                        }
                        val initialDayResult = if (isRunning && isToday) {
                            if (isBusinessTrip) calculateDayWorkTime(blocksForCalc, isBusinessTrip = true) else calculateDayWorkTime(blocksForCalc)
                        } else {
                            baseDayResult
                        }
                        val initialLiveDelta = if (isRunning && isToday) {
                            initialDayResult.netMinutes - baseDayResult.netMinutes
                        } else {
                            0L
                        }
                        val breakCheckResult = if (settings.breakWarningEnabled) {
                            if (isBusinessTrip) checkBreakViolation(blocksForCalc, isBusinessTrip = true)
                            else checkBreakViolation(blocksForCalc)
                        } else {
                            BreakCheckResult(emptyList(), skipped = false)
                        }
                        // Exclude planned days from calculations in current month.
                        // Also exclude today if it's a WORK or BUSINESS_TRIP day with no completed time blocks yet
                        // (running blocks count as 0 min, empty entries from auto-clockin etc.
                        // would wrongly deduct the full daily target before the day is over).
                        fun hasCompletedBlocks(day: WorkDay) = day.timeBlocks.any { it.endTime != null }
                        fun isWorkLike(dayType: DayType) = dayType == DayType.WORK || dayType == DayType.BUSINESS_TRIP || dayType == DayType.SATURDAY_BONUS
                        val actualMonthDays = monthDays.filter { !it.isPlanned }.mapNotNull { day ->
                            val resolved = if (day.date == today && workDay != null && date == today) workDay else day
                            if (resolved.date == today && isWorkLike(resolved.dayType) && !hasCompletedBlocks(resolved)) null
                            else resolved
                        }

                        // Cumulative flextime: all year's actual days (not planned), with today replaced
                        val actualYearDays = currentYearDays.filter { !it.isPlanned }.mapNotNull { day ->
                            val resolved = if (day.date == today && workDay != null && date == today) workDay else day
                            if (resolved.date == today && isWorkLike(resolved.dayType) && !hasCompletedBlocks(resolved)) null
                            else resolved
                        }
                        cachedWorkTimeRules = workTimeRules
                        cachedMonthDays = monthDays
                        cachedYearDays = currentYearDays

                        val baseYDays = yearDays.filter { !it.isPlanned && it.date.isBefore(date) }
                        val baseFlextime = calculateFlextime(baseYDays, settings, yearMonth, workTimeRules)

                        val (flextime, monthlyFlextime, liveFlextime, liveMonthlyFlextime) = if (isRunning && isToday) {
                            val todayWithNow = (workDay ?: WorkDay(date = today, location = workDay?.location ?: WorkLocation.OFFICE, dayType = selectedDayType))
                                .copy(dayType = selectedDayType, timeBlocks = blocksForCalc)
                            val liveMDays = monthDays.filter { !it.isPlanned && it.date != today } + listOf(todayWithNow)
                            val liveYDays = currentYearDays.filter { !it.isPlanned && it.date != today } + listOf(todayWithNow)
                            val baseMDays = monthDays.filter { !it.isPlanned && it.date.isBefore(today) }
                            listOf(
                                baseFlextime,
                                calculateFlextime(baseMDays, settings, yearMonth, workTimeRules),
                                calculateFlextime(liveYDays, settings, todayYearMonth, workTimeRules),
                                calculateFlextime(liveMDays, settings, yearMonth, workTimeRules)
                            )
                        } else {
                            listOf(
                                calculateFlextime(actualYearDays, settings, todayYearMonth, workTimeRules),
                                calculateFlextime(actualMonthDays, settings, yearMonth, workTimeRules),
                                null,
                                null
                            )
                        }

                        val daysThroughSelectedDate = yearDays.filter { !it.isPlanned && !it.date.isAfter(date) }.mapNotNull { day ->
                            val resolved = if (day.date == today && workDay != null && date == today) workDay else day
                            if (resolved.date == today && isWorkLike(resolved.dayType) && !hasCompletedBlocks(resolved)) null
                            else resolved
                        }
                        val selectedDateFlextime = if (date == today) {
                            if (isRunning) liveFlextime ?: baseFlextime else flextime ?: FlextimeBalance()
                        } else {
                            calculateFlextime(daysThroughSelectedDate, settings, yearMonth, workTimeRules)
                        }

                        val quota = calculateQuota(actualMonthDays, settings, yearMonth, qPercent, qDays, workTimeRules)

                        val baseMonthlyTarget = (settingsRepository.getWorkTimeRuleForDate(yearMonth.atEndOfMonth(), workTimeRules)?.monthlyWorkMinutes ?: settings.monthlyWorkMinutes).toLong()
                        val neutralTypes = setOf(DayType.VACATION, DayType.SPECIAL_VACATION, DayType.FLEX_DAY, DayType.SICK_DAY)
                        val neutralDaysDeduction = actualMonthDays.filter { it.dayType in neutralTypes }.sumOf { day ->
                            val rule = settingsRepository.getWorkTimeRuleForDate(day.date, workTimeRules)
                            val isWorkDay = if (rule != null) day.date.dayOfWeek in rule.workDays else true
                            if (isWorkDay) {
                                (rule?.dailyWorkMinutes ?: settings.dailyWorkMinutes).toLong()
                            } else 0L
                        }
                        val totalMin = (baseMonthlyTarget - neutralDaysDeduction).coerceAtLeast(0)
                        val requiredMin = (totalMin * qPercent / 100.0).toLong()

                        val ruleForDate = settingsRepository.getWorkTimeRuleForDate(date, workTimeRules)
                        val dailyTarget = getDailyWorkTargetMinutes(
                            date,
                            selectedDayType,
                            settings.dailyWorkMinutes,
                            ruleForDate
                        )

                        val workingDays = actualMonthDays.filter { it.dayType !in neutralTypes }
                        var officeMin = 0L
                        for (day in workingDays) {
                            val isDayBusinessTrip = day.dayType == DayType.BUSINESS_TRIP
                            val adjustedBlocks = if (isDayBusinessTrip) {
                                day.timeBlocks.sortedBy { it.startTime }
                            } else {
                                CalculateDayWorkTimeUseCase.adjustTimeBlocks(day.timeBlocks)
                            }
                            val dayResult = if (isDayBusinessTrip) calculateDayWorkTime(day.timeBlocks, isBusinessTrip = true) else calculateDayWorkTime(day.timeBlocks)
                            val totalGross = dayResult.grossMinutes
                            if (totalGross == 0L) continue
                            var dayOfficeGross = 0L
                            for (block in adjustedBlocks) {
                                val blockEnd = block.endTime ?: continue
                                val blockMin = java.time.Duration.between(block.startTime, blockEnd).toMinutes()
                                if (blockMin > 0 && (block.location == WorkLocation.OFFICE || isDayBusinessTrip)) dayOfficeGross += blockMin
                            }
                            officeMin += dayOfficeGross * dayResult.netMinutes / totalGross
                        }

                        Pair(
                            HomeUiState(
                                today = today,
                                selectedDate = date,
                                workDay = workDay,
                                timeBlocks = timeBlocks,
                                isClockRunning = isRunning,
                                selectedLocation = workDay?.location ?: WorkLocation.OFFICE,
                                selectedDayType = selectedDayType,
                                dayWorkTime = initialDayResult,
                                baseDayNetMinutes = baseDayResult.netMinutes,
                                liveFlextimeDelta = initialLiveDelta,
                                flextimeBalance = flextime ?: FlextimeBalance(),
                                baseFlextimeBalance = baseFlextime,
                                selectedDateFlextimeBalance = selectedDateFlextime,
                                monthlyFlextimeBalance = monthlyFlextime ?: FlextimeBalance(),
                                liveFlextimeBalance = liveFlextime,
                                liveMonthlyFlextimeBalance = liveMonthlyFlextime,
                                quotaStatus = quota,
                                settings = settings,
                                effectiveQuotaPercent = qPercent,
                                effectiveQuotaMinDays = qDays,
                                officeMinutes = officeMin,
                                requiredOfficeMinutes = requiredMin,
                                breakCheckResult = breakCheckResult,
                                hasTimeBlockOverlap = hasOverlap,
                                dailyTargetMinutes = dailyTarget
                            ),
                            workTimeRules
                        )
                    }
                }
                .collect { (state, rules) ->
                    val wasRunning = _uiState.value.isClockRunning
                    _uiState.value = state.copy(projectedEndTime = computeProjectedEndTime(state, rules))
                    _remainingMinutes.value = computeRemainingMinutes(state, rules)
                    checkPermissions(state.settings)
                    // Restart service if clocked in and it was just loaded (first emission)
                    if (!wasRunning && state.isClockRunning && state.settings.workTimerNotificationEnabled) {
                        startWorkTimerService()
                    }
                }
        }
    }

    fun autoBookPlannedDays() {
        viewModelScope.launch {
            autoBookPlannedDaysUseCase()
        }
    }

    fun onResume() {
        checkPermissions()
        autoBookPlannedDays()
        updateLiveWorkTime()
    }

    fun checkPermissions(settings: Settings = _uiState.value.settings) {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        val issues = buildList {
            if (settings.geofenceEnabled) {
                val bgOk = granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                if (!granted(Manifest.permission.ACCESS_FINE_LOCATION) || !bgOk)
                    add("Geofencing")
            }
            if (settings.wifiAutoStampEnabled && !granted(Manifest.permission.ACCESS_FINE_LOCATION))
                add("WLAN-Stempel")
            if (settings.calendarSyncEnabled && settings.calendarId != -1L &&
                (!granted(Manifest.permission.READ_CALENDAR) || !granted(Manifest.permission.WRITE_CALENDAR)))
                add("Kalender-Sync")
            if (backupPreferences.isAutoBackupEnabled) {
                val dirUriString = backupPreferences.autoBackupDirectoryUri
                if (dirUriString != null) {
                    val hasPersistedPermission = context.contentResolver.persistedUriPermissions
                        .any { it.uri.toString() == dirUriString && it.isWritePermission }
                    if (!hasPersistedPermission) add("Auto-Backup")
                }
            }
        }
        _uiState.update { it.copy(permissionIssues = issues) }
    }

    fun goToPreviousDay() {        _localDayTypeOverride.value = null
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
            val workLikeTypes = setOf(DayType.WORK, DayType.BUSINESS_TRIP, DayType.SATURDAY_BONUS)
            val targetDayType = when {
                state.selectedDayType in workLikeTypes -> state.selectedDayType
                state.workDay != null -> state.workDay.dayType
                else -> DayType.WORK
            }
            val isBT = targetDayType == DayType.BUSINESS_TRIP
            val effectiveLocation = if (isBT) WorkLocation.OFFICE else state.selectedLocation

            val workDayId = if (state.workDay == null) {
                workDayRepository.saveWorkDay(
                    WorkDay(
                        date = state.selectedDate,
                        location = effectiveLocation,
                        dayType = targetDayType
                    )
                )
            } else {
                val needsUpdate = state.workDay.isPlanned ||
                    state.workDay.dayType != targetDayType ||
                    state.workDay.location != effectiveLocation
                if (needsUpdate) {
                    workDayRepository.saveWorkDay(
                        state.workDay.copy(
                            isPlanned = false,
                            dayType = targetDayType,
                            location = effectiveLocation
                        )
                    )
                }
                if (state.workDay.isPlanned) {
                    state.workDay.timeBlocks.forEach { workDayRepository.deleteTimeBlock(it) }
                }
                state.workDay.id
            }

            workDayRepository.saveTimeBlock(
                TimeBlock(workDayId = workDayId, startTime = now, location = effectiveLocation)
            )
            if (state.settings.breakWarningEnabled && !isBT) {
                breakWarningScheduler.scheduleWarning(now)
            }
            if (state.settings.workTimerNotificationEnabled) {
                startWorkTimerService()
            }
            _localDayTypeOverride.value = null
            updateQuickSettingsTile()
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
            updateQuickSettingsTile()
        }
    }

    private fun updateQuickSettingsTile() {
        android.service.quicksettings.TileService.requestListeningState(
            context,
            android.content.ComponentName(context, com.flex.tile.QuickSettingsTileService::class.java)
        )
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

    private fun isSpecialVacationBlocked(date: LocalDate, settings: Settings): Boolean {
        val isPaidOut = settings.specialVacationPaidOut && settings.settingsYear == date.year
        val isAfterOct31 = date.isAfter(LocalDate.of(date.year, 10, 31))
        return isPaidOut || isAfterOct31
    }

    fun setDayType(dayType: DayType) {
        val state = _uiState.value
        if (dayType == DayType.SPECIAL_VACATION && isSpecialVacationBlocked(state.selectedDate, state.settings)) {
            return
        }
        _localDayTypeOverride.value = dayType
    }

    fun saveManualEntry(startTime: LocalTime, endTime: LocalTime, location: WorkLocation) {
        viewModelScope.launch {
            val state = _uiState.value
            val targetDayType = if (state.selectedDayType == DayType.SPECIAL_VACATION && isSpecialVacationBlocked(state.selectedDate, state.settings)) {
                DayType.WORK
            } else {
                state.selectedDayType
            }
            val isBT = targetDayType == DayType.BUSINESS_TRIP
            val effectiveLocation = if (isBT) WorkLocation.OFFICE else location
            val effectiveDayLocation = if (isBT) WorkLocation.OFFICE else state.selectedLocation

            val workDayId = if (state.workDay == null) {
                workDayRepository.saveWorkDay(
                    WorkDay(
                        date = state.selectedDate,
                        location = effectiveDayLocation,
                        dayType = targetDayType
                    )
                )
            } else {
                val needsUpdate = state.workDay.isPlanned ||
                    state.workDay.dayType != targetDayType ||
                    state.workDay.location != effectiveDayLocation
                if (needsUpdate) {
                    workDayRepository.saveWorkDay(
                        state.workDay.copy(
                            isPlanned = false,
                            dayType = targetDayType,
                            location = effectiveDayLocation
                        )
                    )
                }
                if (state.workDay.isPlanned) {
                    state.workDay.timeBlocks.forEach { workDayRepository.deleteTimeBlock(it) }
                }
                state.workDay.id
            }

            workDayRepository.saveTimeBlock(
                TimeBlock(workDayId = workDayId, startTime = startTime, endTime = endTime, location = effectiveLocation)
            )
            _localDayTypeOverride.value = null
        }
    }

    fun saveDurationEntry(totalMinutes: Int, location: WorkLocation) {
        viewModelScope.launch {
            val state = _uiState.value
            val targetDayType = if (state.selectedDayType == DayType.SPECIAL_VACATION && isSpecialVacationBlocked(state.selectedDate, state.settings)) {
                DayType.WORK
            } else {
                state.selectedDayType
            }
            val start = state.settings.defaultStartTime
            val end = start.plusMinutes(totalMinutes.toLong())
            val isBT = targetDayType == DayType.BUSINESS_TRIP
            val effectiveLocation = if (isBT) WorkLocation.OFFICE else location
            val effectiveDayLocation = if (isBT) WorkLocation.OFFICE else state.selectedLocation

            val workDayId = if (state.workDay == null) {
                workDayRepository.saveWorkDay(
                    WorkDay(
                        date = state.selectedDate,
                        location = effectiveDayLocation,
                        dayType = targetDayType
                    )
                )
            } else {
                val needsUpdate = state.workDay.isPlanned ||
                    state.workDay.dayType != targetDayType ||
                    state.workDay.location != effectiveDayLocation
                if (needsUpdate) {
                    workDayRepository.saveWorkDay(
                        state.workDay.copy(
                            isPlanned = false,
                            dayType = targetDayType,
                            location = effectiveDayLocation
                        )
                    )
                }
                if (state.workDay.isPlanned) {
                    state.workDay.timeBlocks.forEach { workDayRepository.deleteTimeBlock(it) }
                }
                state.workDay.id
            }

            workDayRepository.saveTimeBlock(
                TimeBlock(workDayId = workDayId, startTime = start, endTime = end, isDuration = true, location = effectiveLocation)
            )
            _localDayTypeOverride.value = null
        }
    }

    fun updateTimeBlock(
        block: TimeBlock,
        startTime: LocalTime,
        endTime: LocalTime?,
        location: WorkLocation,
        isDuration: Boolean = block.isDuration
    ) {
        viewModelScope.launch {
            val wasRunning = block.endTime == null
            val isNowRunning = endTime == null
            val workDay = _uiState.value.workDay
            val isBT = _uiState.value.selectedDayType == DayType.BUSINESS_TRIP || workDay?.dayType == DayType.BUSINESS_TRIP
            val effectiveLocation = if (isBT) WorkLocation.OFFICE else location
            if (workDay != null && workDay.isPlanned) {
                workDayRepository.saveWorkDay(workDay.copy(isPlanned = false, location = effectiveLocation))
                workDay.timeBlocks.filter { it.id != block.id && it.isDuration }.forEach {
                    workDayRepository.deleteTimeBlock(it)
                }
            }
            workDayRepository.saveTimeBlock(
                block.copy(startTime = startTime, endTime = endTime, location = effectiveLocation, isDuration = isDuration)
            )
            if (wasRunning && !isNowRunning) {
                breakWarningScheduler.cancelWarning()
                stopWorkTimerService()
                updateQuickSettingsTile()
            } else if (!wasRunning && isNowRunning) {
                val state = _uiState.value
                if (state.settings.breakWarningEnabled) {
                    breakWarningScheduler.scheduleWarning(startTime)
                }
                if (state.settings.workTimerNotificationEnabled) {
                    startWorkTimerService()
                }
                updateQuickSettingsTile()
            } else if (wasRunning && isNowRunning) {
                val intent = Intent(context, WorkTimerService::class.java).apply {
                    action = WorkTimerService.ACTION_UPDATE
                }
                context.startForegroundService(intent)
            }
        }
    }

    fun toggleTimeBlockLocation(block: TimeBlock) {
        if (_uiState.value.selectedDayType == DayType.BUSINESS_TRIP || _uiState.value.workDay?.dayType == DayType.BUSINESS_TRIP) return
        val newLocation = if (block.location == WorkLocation.OFFICE) WorkLocation.HOME_OFFICE else WorkLocation.OFFICE
        val wasRunning = block.endTime == null
        viewModelScope.launch {
            workDayRepository.saveTimeBlock(block.copy(location = newLocation))
            val currentWorkDay = _uiState.value.workDay
            if (currentWorkDay != null && (wasRunning || currentWorkDay.timeBlocks.size <= 1)) {
                workDayRepository.saveWorkDay(currentWorkDay.copy(location = newLocation))
            }
            if (wasRunning) {
                _uiState.update { it.copy(selectedLocation = newLocation) }
                val intent = Intent(context, WorkTimerService::class.java).apply {
                    action = WorkTimerService.ACTION_UPDATE
                }
                context.startForegroundService(intent)
                updateQuickSettingsTile()
            }
        }
    }


    fun bookTimeBlock(
        block: TimeBlock,
        startTime: LocalTime,
        endTime: LocalTime?,
        location: WorkLocation,
        isDuration: Boolean = block.isDuration
    ) {
        viewModelScope.launch {
            val wasRunning = block.endTime == null
            val isNowRunning = endTime == null
            val workDay = _uiState.value.workDay
            if (workDay != null && workDay.isPlanned) {
                workDayRepository.saveWorkDay(workDay.copy(isPlanned = false, location = location))
                workDay.timeBlocks.filter { it.id != block.id && it.isDuration }.forEach {
                    workDayRepository.deleteTimeBlock(it)
                }
            }
            workDayRepository.saveTimeBlock(
                block.copy(startTime = startTime, endTime = endTime, location = location, isDuration = isDuration)
            )
            if (wasRunning && !isNowRunning) {
                breakWarningScheduler.cancelWarning()
                stopWorkTimerService()
                updateQuickSettingsTile()
            } else if (!wasRunning && isNowRunning) {
                val state = _uiState.value
                if (state.settings.breakWarningEnabled) {
                    breakWarningScheduler.scheduleWarning(startTime)
                }
                if (state.settings.workTimerNotificationEnabled) {
                    startWorkTimerService()
                }
                updateQuickSettingsTile()
            } else if (isNowRunning) {
                updateQuickSettingsTile()
            }
        }
    }

    fun unplanWorkDay() {
        viewModelScope.launch {
            val workDay = _uiState.value.workDay ?: return@launch
            workDayRepository.saveWorkDay(workDay.copy(isPlanned = false))
        }
    }

    fun deleteTimeBlock(timeBlock: TimeBlock) {
        viewModelScope.launch {
            val isRunning = timeBlock.endTime == null
            val workDay = _uiState.value.workDay
            val isLastBlock = workDay != null && workDay.timeBlocks.all { it.id == timeBlock.id }
            workDayRepository.deleteTimeBlock(timeBlock)
            if (isLastBlock) {
                workDayRepository.deleteWorkDay(workDay)
            }
            if (isRunning) {
                breakWarningScheduler.cancelWarning()
                stopWorkTimerService()
                updateQuickSettingsTile()
            }
            _undoEvent.emit(UndoEvent("Block gelöscht") {
                if (isLastBlock) {
                    val newId = workDayRepository.saveWorkDay(workDay.copy(id = 0L))
                    workDayRepository.saveTimeBlock(timeBlock.copy(id = 0L, workDayId = newId))
                } else {
                    workDayRepository.saveTimeBlock(timeBlock.copy(id = 0L))
                }
                if (isRunning) {
                    val state = _uiState.value
                    if (state.settings.breakWarningEnabled) {
                        breakWarningScheduler.scheduleWarning(timeBlock.startTime)
                    }
                    if (state.settings.workTimerNotificationEnabled) {
                        startWorkTimerService()
                    }
                    updateQuickSettingsTile()
                }
            })
        }
    }

    fun saveDayType(dayType: DayType) {
        val state = _uiState.value
        if (dayType == DayType.SPECIAL_VACATION && isSpecialVacationBlocked(state.selectedDate, state.settings)) {
            return
        }
        viewModelScope.launch {
            val isBT = dayType == DayType.BUSINESS_TRIP
            val effectiveLoc = if (isBT) WorkLocation.OFFICE else state.selectedLocation
            val existing = state.workDay
            if (existing != null && existing.timeBlocks.isEmpty() && dayType == DayType.WORK) {
                workDayRepository.deleteWorkDay(existing)
                _localDayTypeOverride.value = null
                return@launch
            }
            val updated = if (existing != null) {
                existing.copy(dayType = dayType, location = effectiveLoc, isPlanned = false)
            } else {
                WorkDay(
                    id = 0,
                    date = state.selectedDate,
                    location = effectiveLoc,
                    dayType = dayType,
                    isPlanned = false
                )
            }
            workDayRepository.saveWorkDay(updated)
            if (isBT && existing != null) {
                existing.timeBlocks.forEach { block ->
                    if (block.location != WorkLocation.OFFICE) {
                        workDayRepository.saveTimeBlock(block.copy(location = WorkLocation.OFFICE))
                    }
                }
            }
            if (state.isClockRunning) {
                if (isBT) {
                    breakWarningScheduler.cancelWarning()
                } else if (state.settings.breakWarningEnabled) {
                    val runningBlock = state.timeBlocks.find { it.endTime == null }
                    if (runningBlock != null) {
                        breakWarningScheduler.scheduleWarning(runningBlock.startTime)
                    }
                }
                val intent = Intent(context, WorkTimerService::class.java).apply {
                    action = WorkTimerService.ACTION_UPDATE
                }
                context.startForegroundService(intent)
            }
            _localDayTypeOverride.value = null
        }
    }

    fun deleteDay() {
        viewModelScope.launch {
            val workDay = _uiState.value.workDay ?: return@launch
            val wasRunning = workDay.timeBlocks.any { it.endTime == null }
            workDay.timeBlocks.forEach { workDayRepository.deleteTimeBlock(it) }
            workDayRepository.deleteWorkDay(workDay)
            if (wasRunning) {
                breakWarningScheduler.cancelWarning()
                stopWorkTimerService()
                updateQuickSettingsTile()
            }
            _localDayTypeOverride.value = null
        }
    }

    fun refreshFlextimeData() {
        _refreshTrigger.value = Unit
    }
}
