package com.flex.ui.quota

import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.GetMonthWorkDaysUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.ui.dashboard.DashboardViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

typealias VacationInfo = com.flex.ui.dashboard.VacationInfo
typealias QuotaUiState = com.flex.ui.dashboard.DashboardUiState

@HiltViewModel
class QuotaViewModel @Inject constructor(
    getMonthWorkDays: GetMonthWorkDaysUseCase,
    getSettings: GetSettingsUseCase,
    workDayRepository: WorkDayRepository,
    settingsRepository: SettingsRepository,
    calculateQuota: CalculateQuotaUseCase,
    calculateFlextime: CalculateFlextimeUseCase
) : DashboardViewModel(
    getMonthWorkDays,
    getSettings,
    workDayRepository,
    settingsRepository,
    calculateQuota,
    calculateFlextime
)
