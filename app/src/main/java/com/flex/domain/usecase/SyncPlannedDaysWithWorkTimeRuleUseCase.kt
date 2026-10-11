package com.flex.domain.usecase

import com.flex.domain.model.DEFAULT_WORK_DAYS
import com.flex.domain.model.DayType
import com.flex.domain.model.SyncPlannedDaysResult
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.getRuleForDate
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.time.YearMonth
import javax.inject.Inject

class SyncPlannedDaysWithWorkTimeRuleUseCase @Inject constructor(
    private val workDayRepository: WorkDayRepository,
    private val settingsRepository: SettingsRepository,
    private val getSettings: GetSettingsUseCase,
    private val calculateDayWorkTime: CalculateDayWorkTimeUseCase
) {

    suspend fun syncMonth(
        month: YearMonth,
        removeNonWorkDays: Boolean = false,
        previousDailyMinutes: Int? = null
    ): SyncPlannedDaysResult {
        val settings = getSettings().first()
        val rules = settingsRepository.getWorkTimeRules().first()
        val days = workDayRepository.getWorkDaysForMonth(month).firstOrNull() ?: emptyList()
        val plannedDays = days.filter { it.isPlanned && it.dayType in PLANNED_WORK_DAY_TYPES }

        var updatedCount = 0
        var removedCount = 0
        var untouchedCustomCount = 0

        val knownStandardDurations = (rules.map { it.dailyWorkMinutes } +
            settings.dailyWorkMinutes +
            listOfNotNull(previousDailyMinutes)
        ).toSet()

        val effectivePreviousDailyMinutes = previousDailyMinutes?.takeIf { prev ->
            val ruleForMonth = rules.getRuleForDate(month.atDay(1))
            val target = ruleForMonth?.dailyWorkMinutes ?: settings.dailyWorkMinutes
            prev != target
        }

        for (day in plannedDays) {
            val rule = rules.getRuleForDate(day.date)
            val activeWorkDays = rule?.workDays ?: DEFAULT_WORK_DAYS
            val targetDailyMinutes = rule?.dailyWorkMinutes ?: settings.dailyWorkMinutes

            if (removeNonWorkDays && day.date.dayOfWeek !in activeWorkDays) {
                workDayRepository.deleteWorkDay(day)
                removedCount++
            } else {
                val currentNetMinutes = calculateDayWorkTime(day.timeBlocks).netMinutes
                if (currentNetMinutes == targetDailyMinutes.toLong() && day.timeBlocks.isNotEmpty()) {
                    // Tag entspricht bereits der aktuellen Regel
                    continue
                }

                val duration = currentNetMinutes.toInt()
                val isStandard = if (effectivePreviousDailyMinutes != null) {
                    duration == effectivePreviousDailyMinutes || duration in knownStandardDurations
                } else {
                    day.timeBlocks.isEmpty() || duration in knownStandardDurations
                }

                if (!isStandard) {
                    untouchedCustomCount++
                } else {
                    day.timeBlocks.forEach { workDayRepository.deleteTimeBlock(it) }
                    val start = settings.defaultStartTime
                    val end = start.plusMinutes(targetDailyMinutes.toLong())
                    workDayRepository.saveTimeBlock(
                        TimeBlock(
                            workDayId = day.id,
                            startTime = start,
                            endTime = end,
                            isDuration = true,
                            location = day.location
                        )
                    )
                    updatedCount++
                }
            }
        }

        return SyncPlannedDaysResult(
            updatedDaysCount = updatedCount,
            removedDaysCount = removedCount,
            untouchedCustomDaysCount = untouchedCustomCount
        )
    }

    suspend fun syncRange(
        fromMonth: YearMonth,
        toMonth: YearMonth? = null,
        removeNonWorkDays: Boolean = false,
        previousDailyMinutes: Int? = null
    ): SyncPlannedDaysResult {
        val endMonth = toMonth ?: fromMonth.plusMonths(11)
        var updated = 0
        var removed = 0
        var untouched = 0
        var current = fromMonth

        while (!current.isAfter(endMonth)) {
            val result = syncMonth(current, removeNonWorkDays, previousDailyMinutes)
            updated += result.updatedDaysCount
            removed += result.removedDaysCount
            untouched += result.untouchedCustomDaysCount
            current = current.plusMonths(1)
        }

        return SyncPlannedDaysResult(
            updatedDaysCount = updated,
            removedDaysCount = removed,
            untouchedCustomDaysCount = untouched
        )
    }

    suspend fun countAffectedPlannedDays(
        fromMonth: YearMonth,
        toMonth: YearMonth? = null
    ): Int {
        val endMonth = toMonth ?: fromMonth.plusMonths(11)
        var count = 0
        var current = fromMonth

        while (!current.isAfter(endMonth)) {
            val days = workDayRepository.getWorkDaysForMonth(current).firstOrNull() ?: emptyList()
            count += days.count { it.isPlanned && it.dayType in PLANNED_WORK_DAY_TYPES }
            current = current.plusMonths(1)
        }

        return count
    }

    companion object {
        val PLANNED_WORK_DAY_TYPES: Set<DayType> = setOf(
            DayType.WORK,
            DayType.BUSINESS_TRIP,
            DayType.SATURDAY_BONUS
        )
    }
}
