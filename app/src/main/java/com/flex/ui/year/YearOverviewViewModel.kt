package com.flex.ui.year

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flex.domain.model.DEFAULT_WORK_DAYS
import com.flex.domain.model.DayType
import com.flex.domain.model.PublicHolidays
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    val isPlanned: Boolean,
    val isScheduledWorkDay: Boolean = true,
    val dailyTargetMinutes: Int = 426
)

data class YearSummary(
    val year: Int,
    val totalWorkDays: Int = 0,
    val totalWorkMinutes: Long = 0L,
    val vacationDays: Int = 0,
    val specialVacationDays: Int = 0,
    val specialVacationPaidOut: Boolean = false,
    val sickDays: Int = 0,
    val flexDays: Int = 0,
    val saturdayBonusDays: Int = 0,
    val businessTripDays: Int = 0,
    val publicHolidayCount: Int = 0,
    val officeWorkDays: Int = 0,
    val homeOfficeWorkDays: Int = 0
)

data class YearMonthTileData(
    val yearMonth: java.time.YearMonth,
    val workDays: Int,
    val totalWorkMinutes: Long,
    val officeDays: Int,
    val homeOfficeDays: Int,
    val vacationDays: Int,
    val isFuture: Boolean
)

data class YearOverviewUiState(
    val year: Int = LocalDate.now().year,
    val heatmapEntries: Map<LocalDate, DayHeatmapEntry> = emptyMap(),
    val summary: YearSummary = YearSummary(year = LocalDate.now().year),
    val monthSummaries: List<YearMonthTileData> = emptyList(),
    val dailyWorkMinutes: Int = 426
)

@HiltViewModel
class YearOverviewViewModel @Inject constructor(
    private val workDayRepository: WorkDayRepository,
    private val getSettings: GetSettingsUseCase,
    private val calculateDayWorkTime: CalculateDayWorkTimeUseCase,
    private val settingsRepository: SettingsRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(YearOverviewUiState())
    val uiState: StateFlow<YearOverviewUiState> = _uiState.asStateFlow()

    private val _year = MutableStateFlow(LocalDate.now().year)

    init {
        loadData()
    }

    fun previousYear() { _year.value-- }
    fun nextYear() { _year.value++ }
    fun selectYear(year: Int) { _year.value = year }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadData() {
        viewModelScope.launch {
            _year.flatMapLatest { year ->
                val rulesFlow = settingsRepository?.getWorkTimeRules() ?: flowOf(emptyList())
                combine(
                    workDayRepository.getWorkDaysForYear(year),
                    getSettings(),
                    rulesFlow
                ) { workDays, settings, workTimeRules ->
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
                        val netMin = if (wd != null) {
                            if (wd.dayType == DayType.BUSINESS_TRIP) calculateDayWorkTime(wd.timeBlocks, isBusinessTrip = true).netMinutes
                            else calculateDayWorkTime(wd.timeBlocks).netMinutes
                        } else 0L

                        val rule = settingsRepository?.getWorkTimeRuleForDate(d, workTimeRules)
                        val activeWorkDays = rule?.workDays ?: DEFAULT_WORK_DAYS
                        val isScheduledWorkDay = d.dayOfWeek in activeWorkDays
                        val dailyTarget = rule?.dailyWorkMinutes ?: settings.dailyWorkMinutes

                        entries[d] = DayHeatmapEntry(
                            date = d,
                            dayType = wd?.dayType,
                            isPublicHoliday = holidayName != null,
                            holidayName = holidayName,
                            netMinutes = netMin,
                            location = wd?.location ?: WorkLocation.HOME_OFFICE,
                            isPlanned = wd?.isPlanned ?: false,
                            isScheduledWorkDay = isScheduledWorkDay,
                            dailyTargetMinutes = dailyTarget
                        )
                        d = d.plusDays(1)
                    }

                    // Build summary
                    val actualDays = workDays.filter { !it.isPlanned }
                    val workDaysCount = actualDays.count { it.dayType == DayType.WORK }
                    val totalWorkMinutes = actualDays
                        .filter { it.dayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS, DayType.BUSINESS_TRIP) }
                        .sumOf {
                            if (it.dayType == DayType.BUSINESS_TRIP) calculateDayWorkTime(it.timeBlocks, isBusinessTrip = true).netMinutes
                            else calculateDayWorkTime(it.timeBlocks).netMinutes
                        }

                    val (officeDays, hoDays) = calculateOfficeAndHoDays(actualDays)

                    val summary = YearSummary(
                        year = year,
                        totalWorkDays = workDaysCount,
                        totalWorkMinutes = totalWorkMinutes,
                        vacationDays = actualDays.count { it.dayType == DayType.VACATION },
                        specialVacationDays = actualDays.count { it.dayType == DayType.SPECIAL_VACATION },
                        specialVacationPaidOut = settings.specialVacationPaidOut && (settings.settingsYear == year),
                        sickDays = actualDays.count { it.dayType == DayType.SICK_DAY },
                        flexDays = actualDays.count { it.dayType == DayType.FLEX_DAY },
                        saturdayBonusDays = actualDays.count { it.dayType == DayType.SATURDAY_BONUS },
                        businessTripDays = actualDays.count { it.dayType == DayType.BUSINESS_TRIP },
                        publicHolidayCount = holidays.count { it.key.year == year },
                        officeWorkDays = officeDays,
                        homeOfficeWorkDays = hoDays
                    )

                    val nowMonth = java.time.YearMonth.now()
                    val actualDaysByMonth = actualDays.groupBy { it.date.monthValue }
                    val monthSummaries = (1..12).map { monthNum ->
                        val ym = java.time.YearMonth.of(year, monthNum)
                        val mDays = actualDaysByMonth[monthNum] ?: emptyList()
                        val mWorkDaysCount = mDays.count { it.dayType == DayType.WORK }
                        val mTotalWorkMinutes = mDays
                            .filter { it.dayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS, DayType.BUSINESS_TRIP) }
                            .sumOf {
                                if (it.dayType == DayType.BUSINESS_TRIP) calculateDayWorkTime(it.timeBlocks, isBusinessTrip = true).netMinutes
                                else calculateDayWorkTime(it.timeBlocks).netMinutes
                            }

                        val (mOfficeDays, mHoDays) = calculateOfficeAndHoDays(mDays)

                        YearMonthTileData(
                            yearMonth = ym,
                            workDays = mWorkDaysCount,
                            totalWorkMinutes = mTotalWorkMinutes,
                            officeDays = mOfficeDays,
                            homeOfficeDays = mHoDays,
                            vacationDays = mDays.count { it.dayType == DayType.VACATION },
                            isFuture = ym.isAfter(nowMonth)
                        )
                    }

                    YearOverviewUiState(
                        year = year,
                        heatmapEntries = entries,
                        summary = summary,
                        monthSummaries = monthSummaries,
                        dailyWorkMinutes = settings.dailyWorkMinutes
                    )
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun calculateOfficeAndHoDays(days: List<com.flex.domain.model.WorkDay>): Pair<Int, Int> {
        var officeDays = 0
        var hoDays = 0
        days.filter { it.dayType == DayType.WORK }.forEach { day ->
            val blocks = day.timeBlocks.filter { it.endTime != null }
            if (blocks.isEmpty()) {
                if (day.location == WorkLocation.OFFICE) officeDays++ else hoDays++
            } else {
                val offMin = blocks.filter { it.location == WorkLocation.OFFICE }
                    .sumOf { java.time.Duration.between(it.startTime, it.endTime!!).toMinutes() }
                val hoMin = blocks.filter { it.location == WorkLocation.HOME_OFFICE }
                    .sumOf { java.time.Duration.between(it.startTime, it.endTime!!).toMinutes() }
                if (offMin >= hoMin) officeDays++ else hoDays++
            }
        }
        return officeDays to hoDays
    }
}
