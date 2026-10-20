package com.flex.widget

import com.flex.data.local.PausePreferences
import com.flex.domain.events.DataChangeEventBus
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateProjectedEndTimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlexWidgetDataProvider @Inject constructor(
    private val workDayRepository: WorkDayRepository,
    private val settingsRepository: SettingsRepository,
    private val calculateDayWorkTime: CalculateDayWorkTimeUseCase,
    private val calculateFlextime: CalculateFlextimeUseCase,
    private val calculateQuota: CalculateQuotaUseCase? = null,
    private val pausePreferences: PausePreferences? = null,
    private val dataChangeEventBus: DataChangeEventBus? = null,
    private val calculateProjectedEndTime: CalculateProjectedEndTimeUseCase = CalculateProjectedEndTimeUseCase()
) {

    fun getWidgetStateFlow(): Flow<WidgetState> {
        val today = LocalDate.now()
        val pauseFlow = pausePreferences?.isPausedFlow ?: flowOf(false)

        return combine(
            workDayRepository.getWorkDay(today),
            settingsRepository.getSettings(),
            settingsRepository.getWorkTimeRules(),
            workDayRepository.getWorkDaysForYear(today.year),
            pauseFlow
        ) { workDay, settings, workTimeRules, yearDays, isPausedFromFlow ->
            calculateState(today, workDay, settings, workTimeRules, yearDays, isPausedFromFlow)
        }
    }

    suspend fun getWidgetState(): WidgetState {
        val today = LocalDate.now()
        val workDay = workDayRepository.getWorkDay(today).first()
        val settings = settingsRepository.getSettings().first()
        val workTimeRules = settingsRepository.getWorkTimeRules().first()
        val yearDays = workDayRepository.getWorkDaysForYear(today.year).first()
        val isPaused = pausePreferences?.isPaused ?: false
        return calculateState(today, workDay, settings, workTimeRules, yearDays, isPaused)
    }

    private fun calculateState(
        today: LocalDate,
        workDay: WorkDay?,
        settings: Settings,
        workTimeRules: List<WorkTimeRule>,
        yearDays: List<WorkDay>,
        isPaused: Boolean
    ): WidgetState {
        val rule = settingsRepository.getWorkTimeRuleForDate(today, workTimeRules)
        val targetMinutesToday = if (workDay?.dayType == DayType.SATURDAY_BONUS) {
            0L
        } else if (rule != null) {
            if (today.dayOfWeek in rule.workDays) rule.dailyWorkMinutes.toLong() else 0L
        } else {
            settings.dailyWorkMinutes.toLong()
        }

        val allBlocks = workDay?.timeBlocks ?: emptyList()
        val runningBlock = allBlocks.find { it.endTime == null }
        val isClockRunning = runningBlock != null
        val effectivePaused = isPaused && !isClockRunning

        val currentLocation = runningBlock?.location
            ?: allBlocks.lastOrNull()?.location
            ?: workDay?.location
            ?: WorkLocation.OFFICE

        val now = LocalTime.now().withSecond(0).withNano(0)
        val blocksForCalc = if (runningBlock != null) {
            allBlocks.map { if (it.endTime == null) it.copy(endTime = now) else it }
        } else {
            allBlocks
        }

        val isBusinessTrip = workDay?.dayType == DayType.BUSINESS_TRIP
        val netMinutesToday = if (blocksForCalc.isNotEmpty()) {
            calculateDayWorkTime(blocksForCalc, isBusinessTrip).netMinutes
        } else {
            0L
        }

        val prognosis = if (isClockRunning && targetMinutesToday > 0 && netMinutesToday < targetMinutesToday) {
            val blocksToUse = if (allBlocks.any { it.endTime == null }) allBlocks else blocksForCalc
            calculateProjectedEndTime(blocksToUse, targetMinutesToday.toInt(), now, isBusinessTrip)
                ?: calculateProjectedEndTime(blocksForCalc, targetMinutesToday.toInt(), now, isBusinessTrip)
        } else {
            null
        }

        // Flextime Balance calculation (year days including live today)
        val todayWithNow = (workDay ?: WorkDay(date = today, location = currentLocation, dayType = DayType.WORK))
            .copy(dayType = workDay?.dayType ?: DayType.WORK, timeBlocks = blocksForCalc)
        val liveYearDays = yearDays.filter { !it.isPlanned && it.date != today } + listOf(todayWithNow)
        val flextimeBalance = calculateFlextime(
            liveYearDays,
            settings,
            YearMonth.from(today),
            workTimeRules
        )

        // Office Quota & Days calculation for current month
        val currentYearMonth = YearMonth.from(today)
        val monthDays = yearDays.filter { it.date.year == today.year && it.date.monthValue == today.monthValue }
        val liveMonthDays = monthDays.filter { !it.isPlanned && it.date != today } + listOf(todayWithNow)
        val targetQuotaPercent = settings.officeQuotaPercent
        val targetOfficeDays = settings.officeQuotaMinDays
        val quotaStatus = calculateQuota?.invoke(
            workDays = liveMonthDays,
            settings = settings,
            yearMonth = currentYearMonth,
            quotaPercent = targetQuotaPercent,
            quotaMinDays = targetOfficeDays,
            workTimeRules = workTimeRules
        )

        return WidgetState(
            isClockRunning = isClockRunning,
            isPaused = effectivePaused,
            currentLocation = currentLocation,
            netMinutesToday = netMinutesToday,
            targetMinutesToday = targetMinutesToday,
            flextimeBalanceMinutes = flextimeBalance.totalMinutes,
            officeQuotaPercent = quotaStatus?.officePercent ?: 0.0,
            targetQuotaPercent = targetQuotaPercent,
            officeDays = quotaStatus?.officeDays ?: 0,
            targetOfficeDays = targetOfficeDays,
            isQuotaMet = quotaStatus?.quotaMet ?: false,
            projectedEndTime = prognosis?.projectedEndTime
        )
    }
}
