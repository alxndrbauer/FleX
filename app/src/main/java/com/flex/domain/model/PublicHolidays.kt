package com.flex.domain.model

import java.time.LocalDate

object PublicHolidays {

    /**
     * Returns the name of the public holiday for the given date in Hamburg, or null if it's not a holiday.
     */
    fun getHolidayName(date: LocalDate): String? {
        val holidays = getHolidaysForYear(date.year)
        return holidays[date]
    }

    fun isHoliday(date: LocalDate): Boolean = getHolidayName(date) != null

    fun getHolidaysForYear(year: Int): Map<LocalDate, String> {
        val easter = calculateEaster(year)
        return mapOf(
            LocalDate.of(year, 1, 1) to "Neujahr",
            easter.minusDays(2) to "Karfreitag",
            easter.plusDays(1) to "Ostermontag",
            LocalDate.of(year, 5, 1) to "Tag der Arbeit",
            easter.plusDays(39) to "Christi Himmelfahrt",
            easter.plusDays(50) to "Pfingstmontag",
            LocalDate.of(year, 10, 3) to "Tag der Dt. Einheit",
            LocalDate.of(year, 10, 31) to "Reformationstag",
            LocalDate.of(year, 12, 24) to "Heiligabend",
            LocalDate.of(year, 12, 25) to "1. Weihnachtstag",
            LocalDate.of(year, 12, 26) to "2. Weihnachtstag",
            LocalDate.of(year, 12, 31) to "Silvester"
        )
    }

    /**
     * Gauss algorithm to calculate Easter Sunday for a given year.
     */
    private fun calculateEaster(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }
}
