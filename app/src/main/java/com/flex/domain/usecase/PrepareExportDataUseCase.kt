package com.flex.domain.usecase

import com.flex.domain.model.DayType
import com.flex.domain.model.ExportData
import com.flex.domain.model.ExportDayRow
import com.flex.domain.model.PublicHolidays
import com.flex.domain.model.Settings
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import kotlinx.coroutines.flow.firstOrNull
import java.time.DayOfWeek
import java.time.YearMonth
import javax.inject.Inject

class PrepareExportDataUseCase @Inject constructor(
    private val workDayRepository: WorkDayRepository,
    private val settingsRepository: SettingsRepository,
    private val calculateDayWorkTime: CalculateDayWorkTimeUseCase
) {
    suspend operator fun invoke(yearMonth: YearMonth): ExportData {
        val workDays = workDayRepository.getWorkDaysForMonth(yearMonth).firstOrNull() ?: emptyList()
        val settings = settingsRepository.getSettings().firstOrNull() ?: Settings()
        val workDayMap = workDays.filter { !it.isPlanned }.associateBy { it.date }

        val rows = mutableListOf<ExportDayRow>()
        var totalNet = 0L
        var totalTarget = 0L

        for (day in 1..yearMonth.lengthOfMonth()) {
            val date = yearMonth.atDay(day)
            val workDay = workDayMap[date]
            val dayOfWeek = date.dayOfWeek
            val isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
            val isHoliday = PublicHolidays.isHoliday(date)

            if (workDay != null) {
                val result = calculateDayWorkTime(workDay.timeBlocks)
                val completedBlocks = workDay.timeBlocks
                    .filter { !it.isDuration }
                    .sortedBy { it.startTime }
                val startTime = completedBlocks.firstOrNull()?.startTime
                val endTime = completedBlocks.lastOrNull()?.endTime

                val isWorkType = workDay.dayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS)
                val target = if (isWorkType) settings.dailyWorkMinutes else 0

                rows.add(ExportDayRow(
                    date = date,
                    dayType = workDay.dayType,
                    location = workDay.location,
                    startTime = startTime,
                    endTime = endTime,
                    grossMinutes = result.grossMinutes,
                    breakMinutes = result.breakMinutes,
                    netMinutes = result.netMinutes,
                    targetMinutes = target,
                    note = workDay.note
                ))
                if (isWorkType) totalNet += result.netMinutes
                totalTarget += target
            } else {
                val target = if (!isWeekend && !isHoliday) settings.dailyWorkMinutes else 0
                rows.add(ExportDayRow(
                    date = date,
                    dayType = null,
                    location = null,
                    startTime = null,
                    endTime = null,
                    grossMinutes = 0,
                    breakMinutes = 0,
                    netMinutes = 0,
                    targetMinutes = target,
                    note = null
                ))
                totalTarget += target
            }
        }

        return ExportData(
            yearMonth = yearMonth,
            rows = rows,
            totalNetMinutes = totalNet,
            totalTargetMinutes = totalTarget,
            settings = settings
        )
    }
}
