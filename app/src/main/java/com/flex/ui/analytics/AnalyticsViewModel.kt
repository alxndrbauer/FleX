package com.flex.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flex.domain.model.AnalyticsData
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeRange
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateAnalyticsUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class AnalyticsUiState(
    val timeRange: TimeRange = TimeRange.Month(YearMonth.now()),
    val analyticsData: AnalyticsData? = null,
    val settings: Settings = Settings(),
    val isLoading: Boolean = false
)

private data class AnalyticsConfig(
    val range: TimeRange,
    val settings: Settings,
    val workTimeRules: List<com.flex.domain.model.WorkTimeRule>
)

private data class AnalyticsDataRequest(
    val workDays: List<com.flex.domain.model.WorkDay>,
    val settings: Settings,
    val range: TimeRange,
    val workTimeRules: List<com.flex.domain.model.WorkTimeRule>,
    val priorWorkDays: List<com.flex.domain.model.WorkDay> = emptyList()
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val workDayRepository: WorkDayRepository,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val settingsRepository: SettingsRepository,
    private val calculateAnalyticsUseCase: CalculateAnalyticsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    private val _timeRange = MutableStateFlow<TimeRange>(TimeRange.Month(YearMonth.now()))

    init {
        loadAnalyticsData()
    }

    private fun loadAnalyticsData() {
        viewModelScope.launch {
            combine(
                _timeRange,
                getSettingsUseCase(),
                settingsRepository.getWorkTimeRules()
            ) { range, settings, workTimeRules ->
                _uiState.value = _uiState.value.copy(
                    timeRange = range,
                    settings = settings,
                    isLoading = true
                )
                AnalyticsConfig(range, settings, workTimeRules)
            }.flatMapLatest { (range, settings, workTimeRules) ->
                when (range) {
                    is TimeRange.Month -> {
                        combine(
                            workDayRepository.getWorkDaysForMonth(range.yearMonth),
                            workDayRepository.getWorkDaysForYear(range.yearMonth.year)
                        ) { monthDays, yearDays ->
                            val priorDays = yearDays.filter {
                                it.date.isBefore(range.yearMonth.atDay(1)) && !it.isPlanned
                            }
                            AnalyticsDataRequest(
                                workDays = monthDays,
                                settings = settings,
                                range = range,
                                workTimeRules = workTimeRules,
                                priorWorkDays = priorDays
                            )
                        }
                    }
                    is TimeRange.Year -> {
                        workDayRepository.getWorkDaysForYear(range.year).map { yearDays ->
                            AnalyticsDataRequest(
                                workDays = yearDays,
                                settings = settings,
                                range = range,
                                workTimeRules = workTimeRules,
                                priorWorkDays = emptyList()
                            )
                        }
                    }
                    is TimeRange.Custom -> {
                        val yearStart = java.time.LocalDate.of(range.start.year, 1, 1)
                        val priorFlow = if (range.start.isAfter(yearStart)) {
                            workDayRepository.getWorkDaysInRange(yearStart, range.start.minusDays(1))
                        } else {
                            kotlinx.coroutines.flow.flowOf(emptyList())
                        }
                        combine(
                            workDayRepository.getWorkDaysInRange(range.start, range.end),
                            priorFlow
                        ) { customDays, priorDays ->
                            AnalyticsDataRequest(
                                workDays = customDays,
                                settings = settings,
                                range = range,
                                workTimeRules = workTimeRules,
                                priorWorkDays = priorDays.filter { !it.isPlanned }
                            )
                        }
                    }
                }
            }.catch {
                _uiState.value = _uiState.value.copy(
                    analyticsData = null,
                    isLoading = false
                )
            }.collect { req ->
                val analyticsData = calculateAnalyticsUseCase(
                    workDays = req.workDays,
                    settings = req.settings,
                    timeRange = req.range,
                    workTimeRules = req.workTimeRules,
                    priorWorkDays = req.priorWorkDays
                )

                _uiState.value = _uiState.value.copy(
                    timeRange = req.range,
                    analyticsData = analyticsData,
                    settings = req.settings,
                    isLoading = false
                )
            }
        }
    }

    fun setTimeRange(timeRange: TimeRange) {
        _timeRange.value = timeRange
    }

    fun toggleTimeRange(increment: Boolean) {
        val currentRange = _timeRange.value
        val newRange = when (currentRange) {
            is TimeRange.Month -> {
                if (increment) {
                    TimeRange.Month(currentRange.yearMonth.plusMonths(1))
                } else {
                    TimeRange.Month(currentRange.yearMonth.minusMonths(1))
                }
            }
            is TimeRange.Year -> {
                if (increment) {
                    TimeRange.Year(currentRange.year + 1)
                } else {
                    TimeRange.Year(currentRange.year - 1)
                }
            }
            is TimeRange.Custom -> {
                // No-op for custom ranges
                currentRange
            }
        }
        _timeRange.value = newRange
    }
}
