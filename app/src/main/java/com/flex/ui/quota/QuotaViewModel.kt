package com.flex.ui.quota

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flex.domain.model.DayType
import com.flex.domain.model.FlextimeBalance
import com.flex.domain.model.QuotaStatus
import com.flex.domain.model.Settings
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.model.getRuleForMonth
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.GetMonthWorkDaysUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class VacationInfo(
    val annualDays: Int = 30,
    val usedVacationDays: Int = 0,
    val carryOverDays: Int = 0,
    val specialDays: Int = 5,
    val usedSpecialDays: Int = 0,
    val plannedVacationDays: Int = 0,
    val plannedSpecialDays: Int = 0
) {
    val remainingVacationDays: Int get() = annualDays + carryOverDays - usedVacationDays
    val remainingSpecialDays: Int get() = specialDays - usedSpecialDays
}

data class QuotaUiState(
    val quotaStatus: QuotaStatus = QuotaStatus(),
    val flextimeBalance: FlextimeBalance = FlextimeBalance(),
    val vacationInfo: VacationInfo = VacationInfo(),
    val settings: Settings = Settings(),
    val yearMonth: YearMonth = YearMonth.now(),
    val effectiveQuotaPercent: Int = 40,
    val effectiveQuotaMinDays: Int = 8,
    val totalWorkMinutes: Long = 0,
    val requiredOfficeMinutes: Long = 0,
    val sickDays: Int = 0
)

@HiltViewModel
class QuotaViewModel @Inject constructor(
    private val getMonthWorkDays: GetMonthWorkDaysUseCase,
    private val getSettings: GetSettingsUseCase,
    private val workDayRepository: WorkDayRepository,
    private val settingsRepository: SettingsRepository,
    private val calculateQuota: CalculateQuotaUseCase,
    private val calculateFlextime: CalculateFlextimeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuotaUiState())
    val uiState: StateFlow<QuotaUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val yearMonth = YearMonth.now()
        val year = yearMonth.year

        viewModelScope.launch {
            combine(
                getMonthWorkDays(yearMonth),
                getSettings(),
                workDayRepository.getWorkDaysForYear(year),
                settingsRepository.getQuotaRules(),
                settingsRepository.getWorkTimeRules()
            ) { arr ->
                @Suppress("UNCHECKED_CAST")
                val monthDays = arr[0] as List<WorkDay>
                val settings = arr[1] as Settings
                @Suppress("UNCHECKED_CAST")
                val yearDays = arr[2] as List<WorkDay>
                @Suppress("UNCHECKED_CAST")
                val rules = arr[3] as List<com.flex.domain.model.QuotaRule>
                @Suppress("UNCHECKED_CAST")
                val workTimeRules = arr[4] as List<WorkTimeRule>

                val rule = settingsRepository.getQuotaRuleForMonth(yearMonth, rules)
                val qPercent = rule?.officeQuotaPercent ?: settings.officeQuotaPercent
                val qDays = rule?.officeQuotaMinDays ?: settings.officeQuotaMinDays

                val quota = calculateQuota(monthDays, settings, yearMonth, qPercent, qDays, workTimeRules)
                // Cumulative flextime from all year's actual data
                val actualYearDays = yearDays.filter { !it.isPlanned }
                val flextime = calculateFlextime(actualYearDays, settings, yearMonth)

                // Fixed monthly target, reduced by neutral days
                // Use workTimeRule-aware targets (same logic as HomeViewModel / MonthViewModel)
                val neutralTypes = setOf(DayType.VACATION, DayType.SPECIAL_VACATION, DayType.FLEX_DAY, DayType.SICK_DAY)
                val neutralDays = monthDays.filter { it.dayType in neutralTypes }
                val neutralDaysDeduction = neutralDays.sumOf { day ->
                    (settingsRepository.getWorkTimeRuleForDate(day.date, workTimeRules)?.dailyWorkMinutes
                        ?: settings.dailyWorkMinutes).toLong()
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

                QuotaUiState(
                    quotaStatus = quota,
                    flextimeBalance = flextime,
                    vacationInfo = VacationInfo(
                        annualDays = settings.annualVacationDays,
                        usedVacationDays = usedVacation,
                        carryOverDays = settings.carryOverVacationDays,
                        specialDays = settings.specialVacationDays,
                        usedSpecialDays = usedSpecial,
                        plannedVacationDays = plannedVacation,
                        plannedSpecialDays = plannedSpecial
                    ),
                    settings = settings,
                    yearMonth = yearMonth,
                    effectiveQuotaPercent = qPercent,
                    effectiveQuotaMinDays = qDays,
                    totalWorkMinutes = totalMin,
                    requiredOfficeMinutes = requiredMin,
                    sickDays = sickDays
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
