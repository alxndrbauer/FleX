package com.vrema.ui.quota

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vrema.domain.model.DayType
import com.vrema.domain.model.FlextimeBalance
import com.vrema.domain.model.QuotaStatus
import com.vrema.domain.model.Settings
import com.vrema.domain.model.WorkDay
import com.vrema.domain.repository.SettingsRepository
import com.vrema.domain.repository.WorkDayRepository
import com.vrema.domain.usecase.CalculateFlextimeUseCase
import com.vrema.domain.usecase.CalculateQuotaUseCase
import com.vrema.domain.usecase.GetMonthWorkDaysUseCase
import com.vrema.domain.usecase.GetSettingsUseCase
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
    val requiredOfficeMinutes: Long = 0
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
                settingsRepository.getQuotaRules()
            ) { monthDays, settings, yearDays, rules ->
                val rule = settingsRepository.getQuotaRuleForMonth(yearMonth, rules)
                val qPercent = rule?.officeQuotaPercent ?: settings.officeQuotaPercent
                val qDays = rule?.officeQuotaMinDays ?: settings.officeQuotaMinDays

                val quota = calculateQuota(monthDays, settings, yearMonth, qPercent, qDays)
                // Cumulative flextime from all year's actual data
                val actualYearDays = yearDays.filter { !it.isPlanned }
                val flextime = calculateFlextime(actualYearDays, settings, yearMonth)

                // Fixed monthly target, reduced by neutral days
                val neutralTypes = setOf(DayType.VACATION, DayType.SPECIAL_VACATION, DayType.FLEX_DAY)
                val neutralDayCount = monthDays.count { it.dayType in neutralTypes }
                val totalMin = (settings.monthlyWorkMinutes - neutralDayCount.toLong() * settings.dailyWorkMinutes).coerceAtLeast(0)
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
                    requiredOfficeMinutes = requiredMin
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
