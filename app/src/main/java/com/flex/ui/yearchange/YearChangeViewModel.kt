package com.flex.ui.yearchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class YearChangeState(
    val targetYear: Int = LocalDate.now().year,
    val sourceYear: Int = LocalDate.now().year - 1,
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
    private val calculateFlextime: CalculateFlextimeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(YearChangeState())
    val uiState: StateFlow<YearChangeState> = _uiState.asStateFlow()

    private val _manuallyOpened = MutableStateFlow(false)
    private val _dismissed = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            getSettings()
                .flatMapLatest { settings ->
                    combine(
                        workDayRepository.getWorkDaysForYear(settings.settingsYear),
                        _manuallyOpened,
                        _dismissed
                    ) { workDays, manuallyOpened, dismissed ->
                        val actualDays = workDays.filter { !it.isPlanned }
                        val usedVacation = actualDays.count { it.dayType == DayType.VACATION }
                        val remaining = maxOf(
                            0,
                            settings.annualVacationDays + settings.carryOverVacationDays - usedVacation
                        )
                        val balance = calculateFlextime(actualDays, settings)
                        val shouldShowAuto = LocalDate.now().year > settings.settingsYear
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
            val state = _uiState.value
            settingsRepository.saveSettings(
                settings.copy(
                    settingsYear = state.targetYear,
                    carryOverVacationDays = carryOverDays,
                    annualVacationDays = annualDays,
                    specialVacationDays = 0,
                    initialFlextimeMinutes = state.flextimeMinutes.toInt(),
                    initialOvertimeMinutes = state.overtimeMinutes.toInt()
                )
            )
            _manuallyOpened.value = false
            _dismissed.value = false
        }
    }
}
