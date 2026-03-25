package com.flex.ui.year

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flex.domain.model.DayType
import com.flex.domain.model.PublicHolidays
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DayHeatmapEntry(
    val date: LocalDate,
    val dayType: DayType?,
    val isPublicHoliday: Boolean,
    val holidayName: String?,
    val netMinutes: Long,
    val location: WorkLocation,
    val isPlanned: Boolean
)

data class YearSummary(
    val year: Int,
    val totalWorkDays: Int = 0,
    val totalWorkMinutes: Long = 0L,
    val vacationDays: Int = 0,
    val specialVacationDays: Int = 0,
    val sickDays: Int = 0,
    val flexDays: Int = 0,
    val saturdayBonusDays: Int = 0,
    val publicHolidayCount: Int = 0,
    val officeWorkDays: Int = 0,
    val homeOfficeWorkDays: Int = 0
)

data class YearOverviewUiState(
    val year: Int = LocalDate.now().year,
    val heatmapEntries: Map<LocalDate, DayHeatmapEntry> = emptyMap(),
    val summary: YearSummary = YearSummary(year = LocalDate.now().year),
    val dailyWorkMinutes: Int = 426
)

@HiltViewModel
class YearOverviewViewModel @Inject constructor(
    private val workDayRepository: WorkDayRepository,
    private val getSettings: GetSettingsUseCase,
    private val calculateDayWorkTime: CalculateDayWorkTimeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(YearOverviewUiState())
    val uiState: StateFlow<YearOverviewUiState> = _uiState.asStateFlow()

    private val _year = MutableStateFlow(LocalDate.now().year)

    init {
        loadData()
    }

    fun previousYear() { _year.value-- }
    fun nextYear() { _year.value++ }

    private fun loadData() {
        viewModelScope.launch {
            _year.collect { year ->
                combine(
                    workDayRepository.getWorkDaysForYear(year),
                    getSettings()
                ) { workDays, settings ->
                    val holidays = PublicHolidays.getHolidaysForYear(year)
                    val workDayMap = workDays.filter { !it.isPlanned }.associateBy { it.date }

                    // Build heatmap entries for all days of the year
                    val entries = mutableMapOf<LocalDate, DayHeatmapEntry>()
                    val jan1 = LocalDate.of(year, 1, 1)
                    val dec31 = LocalDate.of(year, 12, 31)
                    var d = jan1
                    while (!d.isAfter(dec31)) {
                        val wd = workDayMap[d]
                        val holidayName = holidays[d]
                        val netMin = if (wd != null) calculateDayWorkTime(wd.timeBlocks).netMinutes else 0L
                        entries[d] = DayHeatmapEntry(
                            date = d,
                            dayType = wd?.dayType,
                            isPublicHoliday = holidayName != null,
                            holidayName = holidayName,
                            netMinutes = netMin,
                            location = wd?.location ?: WorkLocation.HOME_OFFICE,
                            isPlanned = wd?.isPlanned ?: false
                        )
                        d = d.plusDays(1)
                    }

                    // Build summary
                    val actualDays = workDays.filter { !it.isPlanned }
                    val workDaysCount = actualDays.count { it.dayType == DayType.WORK }
                    val totalWorkMinutes = actualDays
                        .filter { it.dayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS) }
                        .sumOf { calculateDayWorkTime(it.timeBlocks).netMinutes }

                    // Count office vs HO days
                    var officeDays = 0
                    var hoDays = 0
                    actualDays.filter { it.dayType == DayType.WORK }.forEach { day ->
                        val blocks = day.timeBlocks.filter { it.endTime != null }
                        if (blocks.isEmpty()) {
                            if (day.location == WorkLocation.OFFICE) officeDays++ else hoDays++
                        } else {
                            var offMin = 0L; var hoMin = 0L
                            blocks.forEach { b ->
                                val min = java.time.Duration.between(b.startTime, b.endTime!!).toMinutes()
                                if (b.location == WorkLocation.OFFICE) offMin += min else hoMin += min
                            }
                            if (offMin >= hoMin) officeDays++ else hoDays++
                        }
                    }

                    val summary = YearSummary(
                        year = year,
                        totalWorkDays = workDaysCount,
                        totalWorkMinutes = totalWorkMinutes,
                        vacationDays = actualDays.count { it.dayType == DayType.VACATION },
                        specialVacationDays = actualDays.count { it.dayType == DayType.SPECIAL_VACATION },
                        sickDays = actualDays.count { it.dayType == DayType.SICK_DAY },
                        flexDays = actualDays.count { it.dayType == DayType.FLEX_DAY },
                        saturdayBonusDays = actualDays.count { it.dayType == DayType.SATURDAY_BONUS },
                        publicHolidayCount = holidays.count { it.key.year == year },
                        officeWorkDays = officeDays,
                        homeOfficeWorkDays = hoDays
                    )

                    YearOverviewUiState(
                        year = year,
                        heatmapEntries = entries,
                        summary = summary,
                        dailyWorkMinutes = settings.dailyWorkMinutes
                    )
                }.collect { state ->
                    _uiState.value = state
                }
            }
        }
    }
}
