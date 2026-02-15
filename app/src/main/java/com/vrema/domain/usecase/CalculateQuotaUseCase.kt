package com.vrema.domain.usecase

import com.vrema.domain.model.DayType
import com.vrema.domain.model.QuotaStatus
import com.vrema.domain.model.Settings
import com.vrema.domain.model.WorkDay
import com.vrema.domain.model.WorkLocation
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
        val neutralTypes = setOf(DayType.VACATION, DayType.SPECIAL_VACATION, DayType.FLEX_DAY)
        val workingDays = workDays.filter { it.dayType !in neutralTypes }

        var officeMinutes = 0L
        var homeOfficeMinutes = 0L
        var officeDays = 0
        var homeOfficeDays = 0

        for (day in workingDays) {
            val result = calculateDayWorkTime(day.timeBlocks)
            when (day.location) {
                WorkLocation.OFFICE -> {
                    officeMinutes += result.netMinutes
                    officeDays++
                }
                WorkLocation.HOME_OFFICE -> {
                    homeOfficeMinutes += result.netMinutes
                    homeOfficeDays++
                }
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
