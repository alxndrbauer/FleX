package com.flex.support

import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Feste Kalenderdaten für Tests. Nie [LocalDate.now] aus einem Test heraus aufrufen:
 * die Suite muss an jedem Wochentag, zu jeder Uhrzeit und in jeder Zeitzone grün sein.
 */
object TestDates {
    val MONDAY = LocalDate.of(2026, 10, 19)
    val TUESDAY = LocalDate.of(2026, 10, 20)
    val WEDNESDAY = LocalDate.of(2026, 10, 21)
    val THURSDAY = LocalDate.of(2026, 10, 22)
    val FRIDAY = LocalDate.of(2026, 10, 16)
    val SATURDAY = LocalDate.of(2026, 10, 17)
    val SUNDAY = LocalDate.of(2026, 10, 18)
    val MONTH_START = LocalDate.of(2026, 10, 1)
    val YEAR_END = LocalDate.of(2026, 12, 31)
}

/**
 * [Clock] mit festem Datum und fester Uhrzeit, gedacht für Produktionsklassen, die
 * `LocalDate.now(clock)` / `LocalTime.now(clock)` verwenden.
 */
fun fixedClock(
    date: LocalDate = TestDates.FRIDAY,
    time: LocalTime = LocalTime.of(12, 0),
    zone: ZoneId = ZoneId.systemDefault()
): Clock = Clock.fixed(date.atTime(time).atZone(zone).toInstant(), zone)
