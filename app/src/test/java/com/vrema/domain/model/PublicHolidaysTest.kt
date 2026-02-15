package com.vrema.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PublicHolidaysTest {

    // Fixed holidays tests (non-moving)

    @Test
    fun testNeujahr2026ExpectHoliday() {
        val date = LocalDate.of(2026, 1, 1)

        assertThat(PublicHolidays.isHoliday(date)).isTrue()
        assertThat(PublicHolidays.getHolidayName(date)).isEqualTo("Neujahr")
    }

    @Test
    fun testTagDerArbeit2026ExpectHoliday() {
        val date = LocalDate.of(2026, 5, 1)

        assertThat(PublicHolidays.isHoliday(date)).isTrue()
        assertThat(PublicHolidays.getHolidayName(date)).isEqualTo("Tag der Arbeit")
    }

    @Test
    fun testTagDerDeutschenEinheit2026ExpectHoliday() {
        val date = LocalDate.of(2026, 10, 3)

        assertThat(PublicHolidays.isHoliday(date)).isTrue()
        assertThat(PublicHolidays.getHolidayName(date)).isEqualTo("Tag der Dt. Einheit")
    }

    @Test
    fun testReformationstag2026ExpectHoliday() {
        val date = LocalDate.of(2026, 10, 31)

        assertThat(PublicHolidays.isHoliday(date)).isTrue()
        assertThat(PublicHolidays.getHolidayName(date)).isEqualTo("Reformationstag")
    }

    @Test
    fun testHeiligabend2026ExpectHoliday() {
        val date = LocalDate.of(2026, 12, 24)

        assertThat(PublicHolidays.isHoliday(date)).isTrue()
        assertThat(PublicHolidays.getHolidayName(date)).isEqualTo("Heiligabend")
    }

    @Test
    fun testErsterWeihnachtstag2026ExpectHoliday() {
        val date = LocalDate.of(2026, 12, 25)

        assertThat(PublicHolidays.isHoliday(date)).isTrue()
        assertThat(PublicHolidays.getHolidayName(date)).isEqualTo("1. Weihnachtstag")
    }

    @Test
    fun testZweiterWeihnachtstag2026ExpectHoliday() {
        val date = LocalDate.of(2026, 12, 26)

        assertThat(PublicHolidays.isHoliday(date)).isTrue()
        assertThat(PublicHolidays.getHolidayName(date)).isEqualTo("2. Weihnachtstag")
    }

    @Test
    fun testSilvester2026ExpectHoliday() {
        val date = LocalDate.of(2026, 12, 31)

        assertThat(PublicHolidays.isHoliday(date)).isTrue()
        assertThat(PublicHolidays.getHolidayName(date)).isEqualTo("Silvester")
    }

    // Easter-based moving holidays tests (Gauss algorithm)

    @Test
    fun testKarfreitag2026ExpectCorrectDate() {
        // Easter 2026: April 5
        // Good Friday (Karfreitag): 2 days before Easter = April 3
        val karfreitag = LocalDate.of(2026, 4, 3)

        assertThat(PublicHolidays.isHoliday(karfreitag)).isTrue()
        assertThat(PublicHolidays.getHolidayName(karfreitag)).isEqualTo("Karfreitag")
    }

    @Test
    fun testOstermontag2026ExpectCorrectDate() {
        // Easter Monday: 1 day after Easter = April 6
        val ostermontag = LocalDate.of(2026, 4, 6)

        assertThat(PublicHolidays.isHoliday(ostermontag)).isTrue()
        assertThat(PublicHolidays.getHolidayName(ostermontag)).isEqualTo("Ostermontag")
    }

    @Test
    fun testChristiHimmelfahrt2026ExpectCorrectDate() {
        // Ascension Day (Christi Himmelfahrt): 39 days after Easter = May 14
        val christiHimmelfahrt = LocalDate.of(2026, 5, 14)

        assertThat(PublicHolidays.isHoliday(christiHimmelfahrt)).isTrue()
        assertThat(PublicHolidays.getHolidayName(christiHimmelfahrt)).isEqualTo("Christi Himmelfahrt")
    }

    @Test
    fun testPfingstmontag2026ExpectCorrectDate() {
        // Whit Monday (Pfingstmontag): 50 days after Easter = May 25
        val pfingstmontag = LocalDate.of(2026, 5, 25)

        assertThat(PublicHolidays.isHoliday(pfingstmontag)).isTrue()
        assertThat(PublicHolidays.getHolidayName(pfingstmontag)).isEqualTo("Pfingstmontag")
    }

    // Easter calculation accuracy tests (multiple years)

    @Test
    fun testEaster2024ExpectMarch31() {
        // Known: Easter 2024 is March 31
        val karfreitag2024 = LocalDate.of(2024, 3, 29) // 2 days before
        val ostermontag2024 = LocalDate.of(2024, 4, 1) // 1 day after

        assertThat(PublicHolidays.isHoliday(karfreitag2024)).isTrue()
        assertThat(PublicHolidays.getHolidayName(karfreitag2024)).isEqualTo("Karfreitag")
        assertThat(PublicHolidays.isHoliday(ostermontag2024)).isTrue()
        assertThat(PublicHolidays.getHolidayName(ostermontag2024)).isEqualTo("Ostermontag")
    }

    @Test
    fun testEaster2025ExpectApril20() {
        // Known: Easter 2025 is April 20
        val karfreitag2025 = LocalDate.of(2025, 4, 18) // 2 days before
        val ostermontag2025 = LocalDate.of(2025, 4, 21) // 1 day after

        assertThat(PublicHolidays.isHoliday(karfreitag2025)).isTrue()
        assertThat(PublicHolidays.isHoliday(ostermontag2025)).isTrue()
    }

    @Test
    fun testEaster2027ExpectMarch28() {
        // Known: Easter 2027 is March 28
        val karfreitag2027 = LocalDate.of(2027, 3, 26) // 2 days before
        val ostermontag2027 = LocalDate.of(2027, 3, 29) // 1 day after

        assertThat(PublicHolidays.isHoliday(karfreitag2027)).isTrue()
        assertThat(PublicHolidays.isHoliday(ostermontag2027)).isTrue()
    }

    // Non-holiday dates tests

    @Test
    fun testRegularWorkdayExpectNotHoliday() {
        // Random Tuesday in February
        val regularDay = LocalDate.of(2026, 2, 10)

        assertThat(PublicHolidays.isHoliday(regularDay)).isFalse()
        assertThat(PublicHolidays.getHolidayName(regularDay)).isNull()
    }

    @Test
    fun testDayBeforeHolidayExpectNotHoliday() {
        // December 23 (day before Heiligabend)
        val dayBefore = LocalDate.of(2026, 12, 23)

        assertThat(PublicHolidays.isHoliday(dayBefore)).isFalse()
        assertThat(PublicHolidays.getHolidayName(dayBefore)).isNull()
    }

    @Test
    fun testDayAfterHolidayExpectNotHoliday() {
        // January 2 (day after Neujahr)
        val dayAfter = LocalDate.of(2026, 1, 2)

        assertThat(PublicHolidays.isHoliday(dayAfter)).isFalse()
        assertThat(PublicHolidays.getHolidayName(dayAfter)).isNull()
    }

    @Test
    fun testWeekendDayExpectNotHolidayUnlessActualHoliday() {
        // Saturday not a holiday (unless it coincides with one)
        val saturday = LocalDate.of(2026, 2, 14)

        assertThat(PublicHolidays.isHoliday(saturday)).isFalse()
    }

    // Year-specific holiday list tests

    @Test
    fun testGetHolidaysForYear2026ExpectCorrectCount() {
        val holidays = PublicHolidays.getHolidaysForYear(2026)

        // Hamburg public holidays: 12 total
        // Neujahr, Karfreitag, Ostermontag, Tag der Arbeit,
        // Christi Himmelfahrt, Pfingstmontag, Tag der Dt. Einheit,
        // Reformationstag, Heiligabend, 1. Weihnachtstag, 2. Weihnachtstag, Silvester
        assertThat(holidays).hasSize(12)
    }

    @Test
    fun testGetHolidaysForYear2026ExpectAllEntriesValid() {
        val holidays = PublicHolidays.getHolidaysForYear(2026)

        // All dates should be in 2026
        holidays.keys.forEach { date ->
            assertThat(date.year).isEqualTo(2026)
        }

        // All names should be non-empty
        holidays.values.forEach { name ->
            assertThat(name).isNotEmpty()
        }
    }

    @Test
    fun testGetHolidaysForDifferentYearsExpectDifferentEasterDates() {
        val holidays2025 = PublicHolidays.getHolidaysForYear(2025)
        val holidays2026 = PublicHolidays.getHolidaysForYear(2026)

        val karfreitag2025 = holidays2025.entries.find { it.value == "Karfreitag" }?.key
        val karfreitag2026 = holidays2026.entries.find { it.value == "Karfreitag" }?.key

        // Easter dates differ year to year
        assertThat(karfreitag2025).isNotEqualTo(karfreitag2026)
    }

    // Edge cases

    @Test
    fun testLeapYearHolidaysExpectCorrectCalculation() {
        // 2024 is a leap year
        val holidays2024 = PublicHolidays.getHolidaysForYear(2024)

        assertThat(holidays2024).hasSize(12)
        // Verify fixed holidays still work
        assertThat(holidays2024).containsKey(LocalDate.of(2024, 1, 1))
        assertThat(holidays2024).containsKey(LocalDate.of(2024, 12, 25))
    }

    @Test
    fun testCenturyTransitionExpectCorrectEasterCalculation() {
        // Test around year 2000 (century transition)
        val holidays2000 = PublicHolidays.getHolidaysForYear(2000)

        assertThat(holidays2000).hasSize(12)
        // Easter 2000 was April 23
        val ostermontag = holidays2000.entries.find { it.value == "Ostermontag" }?.key
        assertThat(ostermontag?.month?.value).isEqualTo(4) // April
    }

    @Test
    fun testFarFutureYearExpectStillWorks() {
        // Test year 2100 (far future)
        val holidays2100 = PublicHolidays.getHolidaysForYear(2100)

        assertThat(holidays2100).hasSize(12)
        assertThat(holidays2100).containsKey(LocalDate.of(2100, 1, 1))
    }
}
