package com.flex.data.export

import com.flex.calendar.CalendarEventMapper
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IcsExportService @Inject constructor(
    private val calendarEventMapper: CalendarEventMapper
) {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun exportToIcs(workDays: List<WorkDay>, settings: Settings): Pair<String, Int> {
        val filtered = workDays.filter { day ->
            calendarEventMapper.isSyncedType(day, settings)
        }

        val content = buildString {
            append("BEGIN:VCALENDAR\r\n")
            append("VERSION:2.0\r\n")
            append("PRODID:-//FleX//Arbeitszeiterfassung//DE\r\n")
            append("CALSCALE:GREGORIAN\r\n")
            append("METHOD:PUBLISH\r\n")

            for (day in filtered) {
                val title = calendarEventMapper.eventTitle(day, settings.calendarEventPrefix)
                val dateStr = day.date.format(dateFormatter)
                val nextDateStr = day.date.plusDays(1).format(dateFormatter)

                append("BEGIN:VEVENT\r\n")
                append("UID:flex-${day.date}-${UUID.randomUUID()}@flex\r\n")
                append("DTSTART;VALUE=DATE:$dateStr\r\n")
                append("DTEND;VALUE=DATE:$nextDateStr\r\n")
                append("SUMMARY:$title\r\n")
                if (!day.note.isNullOrBlank()) {
                    append("DESCRIPTION:${day.note.escapeIcs()}\r\n")
                }
                val location = when (calendarEventMapper.effectiveLocation(day)) {
                    WorkLocation.OFFICE -> "Büro"
                    WorkLocation.HOME_OFFICE -> "Homeoffice"
                }
                if (day.dayType == DayType.WORK) {
                    append("LOCATION:$location\r\n")
                }
                append("END:VEVENT\r\n")
            }

            append("END:VCALENDAR\r\n")
        }
        return Pair(content, filtered.size)
    }


    private fun String.escapeIcs() = replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")
}
