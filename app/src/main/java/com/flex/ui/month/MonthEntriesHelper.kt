package com.flex.ui.month

import androidx.compose.ui.graphics.Color
import com.flex.domain.model.DayType
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.ui.theme.BusinessTripColor
import com.flex.ui.theme.FlexDayColor
import com.flex.ui.theme.HomeOfficeColor
import com.flex.ui.theme.OfficeColor
import com.flex.ui.theme.OvertimeDayColor
import com.flex.ui.theme.SaturdayBonusColor
import com.flex.ui.theme.SickDayColor
import com.flex.ui.theme.SpecialVacationColor
import com.flex.ui.theme.VacationColor
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class MonthEntriesFilter(val label: String) {
    ALL("Alle"),
    OFFICE("Büro"),
    HOME_OFFICE("Homeoffice"),
    ABSENCES("Abwesenheit")
}

data class DayTypeBadgeInfo(
    val icon: String,
    val label: String,
    val accentColor: Color,
    val isOffice: Boolean
)

object MonthEntriesHelper {

    fun filterWorkDays(
        days: List<WorkDay>,
        filter: MonthEntriesFilter
    ): List<WorkDay> {
        val sorted = days.sortedBy { it.date }
        return when (filter) {
            MonthEntriesFilter.ALL -> sorted
            MonthEntriesFilter.OFFICE -> sorted.filter { day ->
                day.dayType == DayType.BUSINESS_TRIP || (
                    day.dayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS) && (
                        if (day.timeBlocks.isNotEmpty()) {
                            day.timeBlocks.any { it.location == WorkLocation.OFFICE }
                        } else {
                            day.location == WorkLocation.OFFICE
                        }
                    )
                )
            }
            MonthEntriesFilter.HOME_OFFICE -> sorted.filter { day ->
                day.dayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS) && (
                    if (day.timeBlocks.isNotEmpty()) {
                        day.timeBlocks.any { it.location == WorkLocation.HOME_OFFICE }
                    } else {
                        day.location == WorkLocation.HOME_OFFICE
                    }
                )
            }
            MonthEntriesFilter.ABSENCES -> sorted.filter { day ->
                day.dayType in listOf(
                    DayType.VACATION,
                    DayType.SPECIAL_VACATION,
                    DayType.FLEX_DAY,
                    DayType.SICK_DAY,
                    DayType.OVERTIME_DAY
                )
            }
        }
    }

    fun formatMonthEntriesSummary(count: Int, totalNetMinutes: Long): String {
        val entriesText = if (count == 1) "1 Eintrag" else "$count Einträge"
        if (count == 0) return entriesText
        val h = totalNetMinutes / 60
        val m = totalNetMinutes % 60
        val mStr = if (m < 10) "0$m" else "$m"
        return "$entriesText • ${h}h ${mStr}m Netto"
    }

    fun formatDailyFlextime(dailyFlextime: Long): String {
        val sign = if (dailyFlextime >= 0) "+" else "-"
        val absDaily = Math.abs(dailyFlextime)
        val h = absDaily / 60
        val m = absDaily % 60
        return "%s%dh %02dm".format(sign, h, m)
    }

    fun formatCumulativeFlextime(flextime: Long): String {
        val sign = if (flextime >= 0) "+" else "-"
        val absFlex = Math.abs(flextime)
        val h = absFlex / 60
        val m = absFlex % 60
        return "Stand: %s%dh %02dm".format(sign, h, m)
    }

    fun formatNetWorkTime(netMinutes: Long): String {
        if (netMinutes <= 0) return ""
        val h = netMinutes / 60
        val m = netMinutes % 60
        return if (m == 0L) "${h}h" else "${h}h ${"%02d".format(m)}m"
    }

    fun formatWorkDayHeaderDate(date: LocalDate): Pair<String, String> {
        val dayShort = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN).trimEnd('.')
        val monthShort = date.format(DateTimeFormatter.ofPattern("MMM", Locale.GERMAN)).trimEnd('.')
        return Pair("$dayShort, ", "${date.dayOfMonth}. $monthShort.")
    }

    fun isCompoundBadgeIcon(icon: String): Boolean = icon.contains("/")

    fun getDayTypeBadgeInfo(workDay: WorkDay): DayTypeBadgeInfo {
        val isWorkType = workDay.dayType in listOf(DayType.WORK, DayType.SATURDAY_BONUS, DayType.BUSINESS_TRIP)

        var officeMin = 0L
        var hoMin = 0L
        if (isWorkType && workDay.timeBlocks.isNotEmpty()) {
            val now = LocalTime.now()
            for (b in workDay.timeBlocks) {
                val end = b.endTime ?: now
                val duration = Duration.between(b.startTime, end).toMinutes().coerceAtLeast(0)
                val effectiveMin = if (duration > 0) duration else 1L
                if (b.location == WorkLocation.OFFICE) officeMin += effectiveMin else hoMin += effectiveMin
            }
        }

        return when (workDay.dayType) {
            DayType.VACATION -> DayTypeBadgeInfo("🏖️", "Urlaub", VacationColor, false)
            DayType.SPECIAL_VACATION -> DayTypeBadgeInfo("⭐", "Sonderurlaub", SpecialVacationColor, false)
            DayType.FLEX_DAY -> DayTypeBadgeInfo("☕", "Gleittag", FlexDayColor, false)
            DayType.SICK_DAY -> DayTypeBadgeInfo("🩺", "Krank", SickDayColor, false)
            DayType.BUSINESS_TRIP -> DayTypeBadgeInfo("✈️", "Dienstreise", BusinessTripColor, true)
            DayType.OVERTIME_DAY -> DayTypeBadgeInfo("⏰", "Überstundentag", OvertimeDayColor, false)
            DayType.SATURDAY_BONUS -> {
                val hasBlocks = workDay.timeBlocks.isNotEmpty()
                val isMixed = hasBlocks && officeMin > 0 && hoMin > 0
                val isOffice = if (!hasBlocks) workDay.location == WorkLocation.OFFICE else officeMin >= hoMin
                val label = if (!hasBlocks) {
                    if (workDay.location == WorkLocation.OFFICE) "Büro" else "Homeoffice"
                } else {
                    when {
                        isMixed -> "Gemischt"
                        officeMin > 0 -> "Büro"
                        else -> "Homeoffice"
                    }
                }
                val icon = when {
                    isMixed -> "🏢/🏠"
                    isOffice -> "🏢"
                    else -> "🏠"
                }
                DayTypeBadgeInfo(icon, label, SaturdayBonusColor, isOffice)
            }
            DayType.WORK -> {
                val hasBlocks = workDay.timeBlocks.isNotEmpty()
                val isMixed = hasBlocks && officeMin > 0 && hoMin > 0
                val isOffice = if (!hasBlocks) workDay.location == WorkLocation.OFFICE else officeMin >= hoMin
                val label = if (!hasBlocks) {
                    if (workDay.location == WorkLocation.OFFICE) "Büro" else "Homeoffice"
                } else {
                    when {
                        isMixed -> "Gemischt"
                        officeMin > 0 -> "Büro"
                        else -> "Homeoffice"
                    }
                }
                val icon = when {
                    isMixed -> "🏢/🏠"
                    isOffice -> "🏢"
                    else -> "🏠"
                }
                DayTypeBadgeInfo(
                    icon = icon,
                    label = label,
                    accentColor = if (isOffice) OfficeColor else HomeOfficeColor,
                    isOffice = isOffice
                )
            }
        }
    }

    fun getAccessibleBadgeTextColor(
        dayType: DayType,
        isOffice: Boolean,
        isDark: Boolean
    ): Color {
        return if (isDark) {
            when (dayType) {
                DayType.WORK -> if (isOffice) Color(0xFF8AB4F8) else Color(0xFF81C995)
                DayType.SATURDAY_BONUS -> Color(0xFFF28B82)
                DayType.BUSINESS_TRIP -> Color(0xFF80CBC4)
                DayType.VACATION -> Color(0xFFFDD663)
                DayType.SPECIAL_VACATION -> Color(0xFFFFB74D)
                DayType.FLEX_DAY -> Color(0xFFBDC1C6)
                DayType.SICK_DAY -> Color(0xFFFFB74D)
                DayType.OVERTIME_DAY -> Color(0xFFCE93D8)
            }
        } else {
            when (dayType) {
                DayType.WORK -> if (isOffice) Color(0xFF185ABC) else Color(0xFF137333)
                DayType.SATURDAY_BONUS -> Color(0xFFB71C1C)
                DayType.BUSINESS_TRIP -> Color(0xFF00695C)
                DayType.VACATION -> Color(0xFF7A5900)
                DayType.SPECIAL_VACATION -> Color(0xFF8D4E00)
                DayType.FLEX_DAY -> Color(0xFF49454F)
                DayType.SICK_DAY -> Color(0xFF8D4E00)
                DayType.OVERTIME_DAY -> Color(0xFF6A1B9A)
            }
        }
    }

    fun getAccessibleLocationTextColor(
        location: WorkLocation,
        isDark: Boolean
    ): Color {
        return if (isDark) {
            if (location == WorkLocation.OFFICE) Color(0xFF8AB4F8) else Color(0xFF81C995)
        } else {
            if (location == WorkLocation.OFFICE) Color(0xFF185ABC) else Color(0xFF137333)
        }
    }
}
