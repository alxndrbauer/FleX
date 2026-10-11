package com.flex.ui.components

import androidx.compose.ui.graphics.Color
import com.flex.domain.model.DayType
import com.flex.domain.model.PublicHolidays
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.ui.theme.BusinessTripColor
import com.flex.ui.theme.FlexDayColor
import com.flex.ui.theme.HomeOfficeColor
import com.flex.ui.theme.OfficeColor
import com.flex.ui.theme.PublicHolidayColor
import com.flex.ui.theme.SaturdayBonusColor
import com.flex.ui.theme.SickDayColor
import com.flex.ui.theme.SpecialVacationColor
import com.flex.ui.theme.VacationColor
import java.time.Duration
import java.time.LocalDate

/**
 * Calculates the background color for a calendar day cell consistently across MonthView and PlanView.
 * For work days with recorded time blocks, the color reflects the location
 * (Office vs. Home-Office) that has the larger share of working time.
 */
fun getDayCellBackgroundColor(
    date: LocalDate,
    workDay: WorkDay?,
    isWeekend: Boolean = false
): Color {
    val isHoliday = PublicHolidays.isHoliday(date)
    return when {
        isHoliday -> PublicHolidayColor.copy(alpha = 0.3f)
        workDay == null -> if (isWeekend) Color.LightGray.copy(alpha = 0.2f) else Color.Transparent
        workDay.dayType == DayType.BUSINESS_TRIP -> BusinessTripColor.copy(alpha = 0.3f)
        workDay.dayType == DayType.VACATION -> VacationColor.copy(alpha = 0.3f)
        workDay.dayType == DayType.SPECIAL_VACATION -> SpecialVacationColor.copy(alpha = 0.3f)
        workDay.dayType == DayType.FLEX_DAY -> FlexDayColor.copy(alpha = 0.3f)
        workDay.dayType == DayType.SATURDAY_BONUS -> SaturdayBonusColor.copy(alpha = 0.3f)
        workDay.dayType == DayType.SICK_DAY -> SickDayColor.copy(alpha = 0.3f)
        else -> {
            val blocks = workDay.timeBlocks.filter { it.endTime != null }
            if (blocks.isEmpty()) {
                if (workDay.location == WorkLocation.OFFICE) OfficeColor.copy(alpha = 0.3f)
                else HomeOfficeColor.copy(alpha = 0.3f)
            } else {
                var officeMin = 0L
                var hoMin = 0L
                for (b in blocks) {
                    val min = Duration.between(b.startTime, b.endTime!!).toMinutes()
                    if (b.location == WorkLocation.OFFICE) officeMin += min else hoMin += min
                }
                if (officeMin >= hoMin) OfficeColor.copy(alpha = 0.3f) else HomeOfficeColor.copy(alpha = 0.3f)
            }
        }
    }
}
