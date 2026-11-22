package com.flex.ui.yearchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.YearClosing
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class YearChangeState(
    val targetYear: Int = 2026,
    val sourceYear: Int = 2025,
    val usedVacationDays: Int = 0,
    val remainingVacationDays: Int = 0,
    val currentAnnualVacationDays: Int = 30,
    val flextimeMinutes: Long = 0L,
    val overtimeMinutes: Long = 0L,
    val showDialog: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class YearChangeViewModel @Inject constructor(
    private val workDayRepository: WorkDayRepository,
    private val settingsRepository: SettingsRepository,
    private val getSettings: GetSettingsUseCase,
    private val calculateFlextime: CalculateFlextimeUseCase,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        YearChangeState(
            targetYear = LocalDate.now(clock).year,
            sourceYear = LocalDate.now(clock).year - 1
        )
    )
    val uiState: StateFlow<YearChangeState> = _uiState.asStateFlow()

    private val _manuallyOpened = MutableStateFlow(false)
    private val _dismissed = MutableStateFlow(false)
    private val _dateCheckTrigger = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            getSettings()
                .flatMapLatest { settings ->
                    combine(
                        workDayRepository.getWorkDaysForYear(settings.settingsYear),
                        settingsRepository.getWorkTimeRules().onStart { emit(emptyList()) },
                        _manuallyOpened,
                        _dismissed,
                        _dateCheckTrigger
                    ) { workDays, workTimeRules, manuallyOpened, dismissed, _ ->
                        val actualDays = workDays.filter { !it.isPlanned }
                        val usedVacation = actualDays.count { it.dayType == DayType.VACATION }
                        val remaining = maxOf(
                            0,
                            settings.annualVacationDays + settings.carryOverVacationDays - usedVacation
                        )
                        val balance = calculateFlextime(actualDays, settings, null, workTimeRules)
                        val shouldShowAuto = LocalDate.now(clock).year > settings.settingsYear
                        val showDialog = (shouldShowAuto || manuallyOpened) && !dismissed

                        YearChangeState(
                            targetYear = settings.settingsYear + 1,
                            sourceYear = settings.settingsYear,
                            usedVacationDays = usedVacation,
                            remainingVacationDays = remaining,
                            currentAnnualVacationDays = settings.annualVacationDays,
                            flextimeMinutes = balance.totalMinutes,
                            overtimeMinutes = balance.overtimeMinutes,
                            showDialog = showDialog
                        )
                    }
                }
                .collect { state -> _uiState.value = state }
        }
    }

    fun refreshDateCheck() {
        _dateCheckTrigger.value++
    }

    fun openDialog() {
        _dismissed.value = false
        _manuallyOpened.value = true
    }

    fun dismiss() {
        _dismissed.value = true
        _manuallyOpened.value = false
    }

    fun applyYearChange(carryOverDays: Int, annualDays: Int) {
        viewModelScope.launch {
            val settings = getSettings().first()
            val today = LocalDate.now(clock)
            val yearEnd = LocalDate.of(settings.settingsYear, 12, 31)
            val confirmUpTo = minOf(today, yearEnd)
            if (!confirmUpTo.isBefore(LocalDate.of(settings.settingsYear, 1, 1))) {
                workDayRepository.confirmPlannedDaysUpTo(confirmUpTo)
            }

            val workDays = workDayRepository.getWorkDaysForYear(settings.settingsYear).first()
            val workTimeRules = settingsRepository.getWorkTimeRules().firstOrNull() ?: emptyList()
            val actualDays = workDays.filter { !it.isPlanned }
            val usedVacation = actualDays.count { it.dayType == DayType.VACATION }
            val calculatedRemaining = maxOf(
                0,
                settings.annualVacationDays + settings.carryOverVacationDays - usedVacation
            )
            val balance = calculateFlextime(actualDays, settings, null, workTimeRules)
            val endFlex = balance.totalMinutes.toInt()
            val endOvertime = balance.overtimeMinutes.toInt()

            val closing = YearClosing(
                year = settings.settingsYear,
                annualVacationDays = settings.annualVacationDays,
                carryOverVacationDays = settings.carryOverVacationDays,
                specialVacationDays = settings.specialVacationDays,
                specialVacationPaidOut = settings.specialVacationPaidOut,
                startFlextimeMinutes = settings.initialFlextimeMinutes,
                startOvertimeMinutes = settings.initialOvertimeMinutes,
                endFlextimeMinutes = endFlex,
                endOvertimeMinutes = endOvertime,
                usedVacationDays = usedVacation,
                carryOverToNextYearManual = carryOverDays != calculatedRemaining,
                closedAt = clock.millis()
            )
            val newSettings = settings.copy(
                settingsYear = settings.settingsYear + 1,
                carryOverVacationDays = carryOverDays,
                annualVacationDays = annualDays,
                specialVacationDays = 0,
                specialVacationPaidOut = false,
                initialFlextimeMinutes = endFlex,
                initialOvertimeMinutes = endOvertime
            )
            settingsRepository.applyYearClosing(closing, newSettings)
            _manuallyOpened.value = false
            _dismissed.value = false
        }
    }
}
