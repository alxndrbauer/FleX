package com.flex.calendar

import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarEventMapper @Inject constructor() {

    fun effectiveLocation(workDay: WorkDay): WorkLocation {
        val blocks = workDay.timeBlocks
        if (blocks.isEmpty()) return workDay.location
        val officeCount = blocks.count { it.location == WorkLocation.OFFICE }
        val homeCount = blocks.count { it.location == WorkLocation.HOME_OFFICE }
        return if (officeCount >= homeCount) WorkLocation.OFFICE else WorkLocation.HOME_OFFICE
    }

    fun isSyncedType(workDay: WorkDay, settings: Settings): Boolean {
        val enabledTypes = settings.calendarSyncTypes.split(",").map { it.trim() }
        if (workDay.dayType.name !in enabledTypes) return false
        if (workDay.dayType == DayType.WORK) {
            val blocks = workDay.timeBlocks
            if (blocks.isNotEmpty()) {
                val hasOffice = blocks.any { it.location == WorkLocation.OFFICE }
                val hasHomeOffice = blocks.any { it.location == WorkLocation.HOME_OFFICE }
                val officeAllowed = settings.calendarSyncOffice && hasOffice
                val homeOfficeAllowed = settings.calendarSyncHomeOffice && hasHomeOffice
                return officeAllowed || homeOfficeAllowed
            }
            if (workDay.location == WorkLocation.OFFICE && !settings.calendarSyncOffice) return false
            if (workDay.location == WorkLocation.HOME_OFFICE && !settings.calendarSyncHomeOffice) return false
        }
        return true
    }

    fun eventTitle(workDay: WorkDay, prefix: String): String {
        val label = when (workDay.dayType) {
            DayType.WORK -> when (effectiveLocation(workDay)) {
                WorkLocation.OFFICE -> "Büro 🏢"
                WorkLocation.HOME_OFFICE -> "Homeoffice 🏠"
            }
            DayType.VACATION -> "Urlaub 🏖"
            DayType.SICK_DAY -> "Krank 🤒"
            DayType.FLEX_DAY -> "Gleittag ☀️"
            DayType.SPECIAL_VACATION -> "Sonderurlaub"
            DayType.OVERTIME_DAY -> "Überstunden-Tag"
            DayType.SATURDAY_BONUS -> "Samstag-Bonus"
        }
        return if (prefix.isBlank()) label else "$prefix: $label"
    }
}
