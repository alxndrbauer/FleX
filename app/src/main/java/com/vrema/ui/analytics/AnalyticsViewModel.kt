package com.vrema.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vrema.domain.model.AnalyticsData
import com.vrema.domain.model.Settings
import com.vrema.domain.model.TimeRange
import com.vrema.domain.repository.WorkDayRepository
import com.vrema.domain.usecase.CalculateAnalyticsUseCase
import com.vrema.domain.usecase.GetSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class AnalyticsUiState(
    val timeRange: TimeRange = TimeRange.Month(YearMonth.now()),
    val analyticsData: AnalyticsData? = null,
    val settings: Settings = Settings(),
    val isLoading: Boolean = false
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val workDayRepository: WorkDayRepository,
    private val getSettingsUseCase: GetSettingsUseCase,
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
                getSettingsUseCase()
            ) { range, settings ->
                _uiState.value = _uiState.value.copy(
                    timeRange = range,
                    settings = settings,
                    isLoading = true
                )
                range to settings
            }.flatMapLatest { (range, settings) ->
                val workDaysFlow = when (range) {
                    is TimeRange.Month -> workDayRepository.getWorkDaysForMonth(range.yearMonth)
                    is TimeRange.Year -> workDayRepository.getWorkDaysForYear(range.year)
                    is TimeRange.Custom -> workDayRepository.getWorkDaysInRange(range.start, range.end)
                }

                combine(
                    workDaysFlow,
                    getSettingsUseCase()
                ) { workDays, settingsInner ->
                    Triple(workDays, settingsInner, range)
                }
            }.catch {
                _uiState.value = _uiState.value.copy(
                    analyticsData = null,
                    isLoading = false
                )
            }.collect { (workDays, settings, range) ->
                val actualWorkDays = workDays.filter { !it.isPlanned }

                val analyticsData = calculateAnalyticsUseCase(
                    workDays = actualWorkDays,
                    settings = settings,
                    timeRange = range
                )

                _uiState.value = _uiState.value.copy(
                    timeRange = range,
                    analyticsData = analyticsData,
                    settings = settings,
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
