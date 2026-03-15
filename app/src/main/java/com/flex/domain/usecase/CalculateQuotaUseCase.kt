package com.flex.domain.usecase

import com.flex.domain.model.DayType
import com.flex.domain.model.QuotaStatus
import com.flex.domain.model.Settings
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

class CalculateQuotaUseCase @Inject constructor(
    private val calculateDayWorkTime: CalculateDayWorkTimeUseCase
) {

    operator fun invoke(
        workDays: List<WorkDay>,
        settings: Settings,
        yearMonth: YearMonth,
        quotaPercent: Int = settings.officeQuotaPercent,
        quotaMinDays: Int = settings.officeQuotaMinDays
    ): QuotaStatus {
        val neutralTypes = setOf(DayType.VACATION, DayType.SPECIAL_VACATION, DayType.FLEX_DAY, DayType.SICK_DAY)
        val workingDays = workDays.filter { it.dayType !in neutralTypes }

        var officeMinutes = 0L
        var homeOfficeMinutes = 0L
        var officeDays = 0
        var homeOfficeDays = 0

        for (day in workingDays) {
            val adjustedBlocks = CalculateDayWorkTimeUseCase.adjustTimeBlocks(day.timeBlocks)
            val dayResult = calculateDayWorkTime(day.timeBlocks)
            val totalGross = dayResult.grossMinutes

            var dayOfficeGross = 0L
            var dayHomeOfficeGross = 0L
            for (block in adjustedBlocks) {
                val blockEnd = block.endTime ?: continue
                val blockMinutes = java.time.Duration.between(block.startTime, blockEnd).toMinutes()
                if (blockMinutes <= 0) continue
                when (block.location) {
                    WorkLocation.OFFICE -> dayOfficeGross += blockMinutes
                    WorkLocation.HOME_OFFICE -> dayHomeOfficeGross += blockMinutes
                }
            }
            // Only count days that have at least one completed block
            val hasCompletedBlocks = day.timeBlocks.any { it.endTime != null }
            if (hasCompletedBlocks) {
                if (dayOfficeGross >= dayHomeOfficeGross) officeDays++ else homeOfficeDays++
            }
            // Distribute net time proportionally across locations
            if (totalGross > 0) {
                officeMinutes += dayOfficeGross * dayResult.netMinutes / totalGross
                homeOfficeMinutes += dayHomeOfficeGross * dayResult.netMinutes / totalGross
            }
        }

        val neutralDayCount = workDays.count { it.dayType in neutralTypes }
        val fixedTarget = (settings.monthlyWorkMinutes - neutralDayCount.toLong() * settings.dailyWorkMinutes).coerceAtLeast(0)
        val officePercent = if (fixedTarget > 0) {
            (officeMinutes.toDouble() / fixedTarget) * 100
        } else 0.0

        val percentQuotaMet = officePercent >= quotaPercent
        val daysQuotaMet = officeDays >= quotaMinDays

        val today = LocalDate.now()
        val remainingWorkDays = if (yearMonth.isAfter(YearMonth.from(today)) ||
            yearMonth == YearMonth.from(today)
        ) {
            val startDay = if (yearMonth == YearMonth.from(today)) today.dayOfMonth + 1 else 1
            val endDay = yearMonth.lengthOfMonth()
            (startDay..endDay).count { dayNum ->
                val date = yearMonth.atDay(dayNum)
                date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
            }
        } else 0

        val requiredOfficeDays = (quotaMinDays - officeDays).coerceAtLeast(0)

        return QuotaStatus(
            officeMinutes = officeMinutes,
            homeOfficeMinutes = homeOfficeMinutes,
            officeDays = officeDays,
            homeOfficeDays = homeOfficeDays,
            officePercent = officePercent,
            percentQuotaMet = percentQuotaMet,
            daysQuotaMet = daysQuotaMet,
            remainingWorkDays = remainingWorkDays,
            requiredOfficeDaysForQuota = requiredOfficeDays
        )
    }
}
