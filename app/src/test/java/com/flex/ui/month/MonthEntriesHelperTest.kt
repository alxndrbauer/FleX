package com.flex.ui.month

import androidx.compose.ui.graphics.Color
import com.flex.domain.model.DayType
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class MonthEntriesHelperTest {

    @Test
    fun `formatDailyFlextime formats positive and negative with two-digit minutes`() {
        assertEquals("+0h 24m", MonthEntriesHelper.formatDailyFlextime(24))
        assertEquals("-1h 05m", MonthEntriesHelper.formatDailyFlextime(-65))
        assertEquals("+0h 00m", MonthEntriesHelper.formatDailyFlextime(0))
        assertEquals("-0h 05m", MonthEntriesHelper.formatDailyFlextime(-5))
        assertEquals("+1h 00m", MonthEntriesHelper.formatDailyFlextime(60))
        assertEquals("+21h 32m", MonthEntriesHelper.formatDailyFlextime(1292))
    }

    @Test
    fun `formatCumulativeFlextime formats correctly with Stand prefix`() {
        assertEquals("Stand: +21h 32m", MonthEntriesHelper.formatCumulativeFlextime(1292))
        assertEquals("Stand: -1h 05m", MonthEntriesHelper.formatCumulativeFlextime(-65))
        assertEquals("Stand: +0h 00m", MonthEntriesHelper.formatCumulativeFlextime(0))
    }

    @Test
    fun `formatNetWorkTime formats correctly`() {
        assertEquals("7h 30m", MonthEntriesHelper.formatNetWorkTime(450))
        assertEquals("8h", MonthEntriesHelper.formatNetWorkTime(480))
        assertEquals("8h 05m", MonthEntriesHelper.formatNetWorkTime(485))
        assertEquals("", MonthEntriesHelper.formatNetWorkTime(0))
        assertEquals("", MonthEntriesHelper.formatNetWorkTime(-10))
    }

    @Test
    fun `formatMonthEntriesSummary formats count and net minutes`() {
        assertEquals("18 Einträge • 126h 45m Netto", MonthEntriesHelper.formatMonthEntriesSummary(18, 7605))
        assertEquals("1 Eintrag • 7h 30m Netto", MonthEntriesHelper.formatMonthEntriesSummary(1, 450))
        assertEquals("2 Einträge • 8h 00m Netto", MonthEntriesHelper.formatMonthEntriesSummary(2, 480))
        assertEquals("0 Einträge", MonthEntriesHelper.formatMonthEntriesSummary(0, 0))
        assertEquals("3 Einträge • 0h 00m Netto", MonthEntriesHelper.formatMonthEntriesSummary(3, 0))
    }

    private fun blend(fg: Color, bg: Color): Color {
        val r = fg.red * fg.alpha + bg.red * (1f - fg.alpha)
        val g = fg.green * fg.alpha + bg.green * (1f - fg.alpha)
        val b = fg.blue * fg.alpha + bg.blue * (1f - fg.alpha)
        return Color(r, g, b, 1f)
    }

    private fun contrastRatio(fg: Color, bg: Color): Double {
        fun lum(c: Color): Double {
            fun ch(v: Float): Double = if (v <= 0.04045f) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
            return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
        }
        val l1 = lum(fg)
        val l2 = lum(bg)
        val high = maxOf(l1, l2)
        val low = minOf(l1, l2)
        return (high + 0.05) / (low + 0.05)
    }

    @Test
    fun `verify WCAG AA contrast for badge text in all themes`() {
        val lightCardBg = Color(0xFFF4F3F7)
        val darkCardBg = Color(0xFF1A1C1E)
        val amoledCardBg = Color(0xFF0A0A0A)

        val sampleDays = listOf(
            WorkDay(id = 1L, date = LocalDate.of(2026, 10, 1), dayType = DayType.WORK, location = WorkLocation.OFFICE),
            WorkDay(id = 2L, date = LocalDate.of(2026, 10, 2), dayType = DayType.WORK, location = WorkLocation.HOME_OFFICE),
            WorkDay(id = 3L, date = LocalDate.of(2026, 10, 3), dayType = DayType.BUSINESS_TRIP),
            WorkDay(id = 4L, date = LocalDate.of(2026, 10, 4), dayType = DayType.VACATION),
            WorkDay(id = 5L, date = LocalDate.of(2026, 10, 5), dayType = DayType.SPECIAL_VACATION),
            WorkDay(id = 6L, date = LocalDate.of(2026, 10, 6), dayType = DayType.FLEX_DAY),
            WorkDay(id = 7L, date = LocalDate.of(2026, 10, 7), dayType = DayType.SICK_DAY),
            WorkDay(id = 8L, date = LocalDate.of(2026, 10, 8), dayType = DayType.OVERTIME_DAY),
            WorkDay(id = 9L, date = LocalDate.of(2026, 10, 9), dayType = DayType.SATURDAY_BONUS)
        )

        for (day in sampleDays) {
            val badge = MonthEntriesHelper.getDayTypeBadgeInfo(day)

            // Light mode
            val lightPillBg = blend(badge.accentColor.copy(alpha = 0.12f), lightCardBg)
            val lightFg = MonthEntriesHelper.getAccessibleBadgeTextColor(day.dayType, badge.isOffice, isDark = false)
            val lightRatio = contrastRatio(lightFg, lightPillBg)
            println("Light ${day.dayType}: $lightRatio")
            assertTrue(lightRatio >= 4.5, "Light mode contrast for ${day.dayType} (${badge.label}) is $lightRatio < 4.5")

            // Dark mode
            val darkPillBg = blend(badge.accentColor.copy(alpha = 0.22f), darkCardBg)
            val darkFg = MonthEntriesHelper.getAccessibleBadgeTextColor(day.dayType, badge.isOffice, isDark = true)
            val darkRatio = contrastRatio(darkFg, darkPillBg)
            println("Dark ${day.dayType}: $darkRatio")
            assertTrue(darkRatio >= 4.5, "Dark mode contrast for ${day.dayType} (${badge.label}) is $darkRatio < 4.5")

            // AMOLED mode
            val amoledPillBg = blend(badge.accentColor.copy(alpha = 0.22f), amoledCardBg)
            val amoledRatio = contrastRatio(darkFg, amoledPillBg)
            println("AMOLED ${day.dayType}: $amoledRatio")
            assertTrue(amoledRatio >= 4.5, "AMOLED mode contrast for ${day.dayType} (${badge.label}) is $amoledRatio < 4.5")
        }

    }

    @Test
    fun `verify WCAG AA contrast for location text in all themes`() {
        val lightTileBg = Color(0xFFE8E8EC)
        val darkTileBg = Color(0xFF282A2D)
        val amoledTileBg = Color(0xFF1A1A1A)

        for (loc in listOf(WorkLocation.OFFICE, WorkLocation.HOME_OFFICE)) {
            val lightFg = MonthEntriesHelper.getAccessibleLocationTextColor(loc, isDark = false)
            val lightRatio = contrastRatio(lightFg, lightTileBg)
            assertTrue(lightRatio >= 4.5, "Light mode location text $loc contrast is $lightRatio < 4.5")

            val darkFg = MonthEntriesHelper.getAccessibleLocationTextColor(loc, isDark = true)
            val darkRatio = contrastRatio(darkFg, darkTileBg)
            assertTrue(darkRatio >= 4.5, "Dark mode location text $loc contrast is $darkRatio < 4.5")

            val amoledRatio = contrastRatio(darkFg, amoledTileBg)
            assertTrue(amoledRatio >= 4.5, "AMOLED mode location text $loc contrast is $amoledRatio < 4.5")
        }
    }



    @Test
    fun `getDayTypeBadgeInfo provides correct icons and labels for all day types`() {
        val officeDay = WorkDay(
            id = 1L,
            date = LocalDate.of(2026, 10, 19),
            dayType = DayType.WORK,
            location = WorkLocation.OFFICE,
            timeBlocks = listOf(
                TimeBlock(id = 1L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 30), location = WorkLocation.OFFICE)
            )
        )
        val officeBadge = MonthEntriesHelper.getDayTypeBadgeInfo(officeDay)
        assertEquals("🏢", officeBadge.icon)
        assertEquals("Büro", officeBadge.label)
        assertTrue(officeBadge.isOffice)

        val hoDay = WorkDay(
            id = 2L,
            date = LocalDate.of(2026, 10, 20),
            dayType = DayType.WORK,
            location = WorkLocation.HOME_OFFICE,
            timeBlocks = listOf(
                TimeBlock(id = 2L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 30), location = WorkLocation.HOME_OFFICE)
            )
        )
        val hoBadge = MonthEntriesHelper.getDayTypeBadgeInfo(hoDay)
        assertEquals("🏠", hoBadge.icon)
        assertEquals("Homeoffice", hoBadge.label)
        assertFalse(hoBadge.isOffice)

        val businessTrip = WorkDay(id = 3L, date = LocalDate.of(2026, 10, 21), dayType = DayType.BUSINESS_TRIP)
        val btBadge = MonthEntriesHelper.getDayTypeBadgeInfo(businessTrip)
        assertEquals("✈️", btBadge.icon)
        assertEquals("Dienstreise", btBadge.label)

        val vacation = WorkDay(id = 4L, date = LocalDate.of(2026, 10, 22), dayType = DayType.VACATION)
        val vacBadge = MonthEntriesHelper.getDayTypeBadgeInfo(vacation)
        assertEquals("🏖️", vacBadge.icon)
        assertEquals("Urlaub", vacBadge.label)

        val flexDay = WorkDay(id = 5L, date = LocalDate.of(2026, 10, 23), dayType = DayType.FLEX_DAY)
        val flexBadge = MonthEntriesHelper.getDayTypeBadgeInfo(flexDay)
        assertEquals("☕", flexBadge.icon)
        assertEquals("Gleittag", flexBadge.label)

        val sickDay = WorkDay(id = 6L, date = LocalDate.of(2026, 10, 24), dayType = DayType.SICK_DAY)
        val sickBadge = MonthEntriesHelper.getDayTypeBadgeInfo(sickDay)
        assertEquals("🩺", sickBadge.icon)
        assertEquals("Krank", sickBadge.label)

        val specialVacation = WorkDay(id = 7L, date = LocalDate.of(2026, 10, 25), dayType = DayType.SPECIAL_VACATION)
        val specBadge = MonthEntriesHelper.getDayTypeBadgeInfo(specialVacation)
        assertEquals("⭐", specBadge.icon)
        assertEquals("Sonderurlaub", specBadge.label)

        val mixedDay = WorkDay(
            id = 8L,
            date = LocalDate.of(2026, 10, 26),
            dayType = DayType.WORK,
            location = WorkLocation.OFFICE,
            timeBlocks = listOf(
                TimeBlock(id = 10L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.OFFICE),
                TimeBlock(id = 11L, startTime = LocalTime.of(13, 0), endTime = LocalTime.of(17, 0), location = WorkLocation.HOME_OFFICE)
            )
        )
        val mixedBadge = MonthEntriesHelper.getDayTypeBadgeInfo(mixedDay)
        assertEquals("🏢/🏠", mixedBadge.icon)
        assertEquals("Gemischt", mixedBadge.label)
    }

    @Test
    fun `filterWorkDays filters properly by category including mixed days`() {
        val mixedDay = WorkDay(
            id = 6L,
            date = LocalDate.of(2026, 10, 6),
            dayType = DayType.WORK,
            location = WorkLocation.OFFICE,
            timeBlocks = listOf(
                TimeBlock(id = 20L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.OFFICE),
                TimeBlock(id = 21L, startTime = LocalTime.of(13, 0), endTime = LocalTime.of(17, 0), location = WorkLocation.HOME_OFFICE)
            )
        )
        val days = listOf(
            WorkDay(id = 1L, date = LocalDate.of(2026, 10, 1), dayType = DayType.WORK, location = WorkLocation.OFFICE),
            WorkDay(id = 2L, date = LocalDate.of(2026, 10, 2), dayType = DayType.WORK, location = WorkLocation.HOME_OFFICE),
            WorkDay(id = 3L, date = LocalDate.of(2026, 10, 3), dayType = DayType.BUSINESS_TRIP),
            WorkDay(id = 4L, date = LocalDate.of(2026, 10, 4), dayType = DayType.VACATION),
            WorkDay(id = 5L, date = LocalDate.of(2026, 10, 5), dayType = DayType.FLEX_DAY),
            mixedDay
        )

        assertEquals(6, MonthEntriesHelper.filterWorkDays(days, MonthEntriesFilter.ALL).size)

        val officeFiltered = MonthEntriesHelper.filterWorkDays(days, MonthEntriesFilter.OFFICE)
        assertEquals(3, officeFiltered.size) // OFFICE + BUSINESS_TRIP + MIXED
        assertTrue(officeFiltered.any { it.id == 1L })
        assertTrue(officeFiltered.any { it.id == 3L })
        assertTrue(officeFiltered.any { it.id == 6L })

        val hoFiltered = MonthEntriesHelper.filterWorkDays(days, MonthEntriesFilter.HOME_OFFICE)
        assertEquals(2, hoFiltered.size) // HO + MIXED
        assertTrue(hoFiltered.any { it.id == 2L })
        assertTrue(hoFiltered.any { it.id == 6L })

        val absenceFiltered = MonthEntriesHelper.filterWorkDays(days, MonthEntriesFilter.ABSENCES)
        assertEquals(2, absenceFiltered.size) // VACATION + FLEX_DAY
        assertTrue(absenceFiltered.any { it.id == 4L })
        assertTrue(absenceFiltered.any { it.id == 5L })
    }

    @Test
    fun `filterWorkDays handles days with active in-progress time blocks`() {
        val activeOfficeDay = WorkDay(
            id = 10L,
            date = LocalDate.of(2026, 10, 10),
            dayType = DayType.WORK,
            location = WorkLocation.HOME_OFFICE, // fallback location is HO, but current active block is OFFICE
            timeBlocks = listOf(
                TimeBlock(id = 100L, startTime = LocalTime.of(9, 0), endTime = null, location = WorkLocation.OFFICE)
            )
        )
        val list = listOf(activeOfficeDay)
        val officeFiltered = MonthEntriesHelper.filterWorkDays(list, MonthEntriesFilter.OFFICE)
        assertEquals(1, officeFiltered.size)

        val hoFiltered = MonthEntriesHelper.filterWorkDays(list, MonthEntriesFilter.HOME_OFFICE)
        assertEquals(0, hoFiltered.size)
    }

    @Test
    fun `filterWorkDays handles empty list`() {
        val emptyList = emptyList<WorkDay>()
        assertEquals(0, MonthEntriesHelper.filterWorkDays(emptyList, MonthEntriesFilter.ALL).size)
        assertEquals(0, MonthEntriesHelper.filterWorkDays(emptyList, MonthEntriesFilter.OFFICE).size)
        assertEquals(0, MonthEntriesHelper.filterWorkDays(emptyList, MonthEntriesFilter.HOME_OFFICE).size)
        assertEquals(0, MonthEntriesHelper.filterWorkDays(emptyList, MonthEntriesFilter.ABSENCES).size)
    }

    @Test
    fun `getDayTypeBadgeInfo handles active in-progress time block in office when day location is home office`() {
        val activeOfficeDay = WorkDay(
            id = 10L,
            date = LocalDate.of(2026, 10, 10),
            dayType = DayType.WORK,
            location = WorkLocation.HOME_OFFICE, // fallback location is HO, but current active block is OFFICE
            timeBlocks = listOf(
                TimeBlock(id = 100L, startTime = LocalTime.of(9, 0), endTime = null, location = WorkLocation.OFFICE)
            )
        )
        val badge = MonthEntriesHelper.getDayTypeBadgeInfo(activeOfficeDay)
        assertEquals("🏢", badge.icon)
        assertEquals("Büro", badge.label)
        assertTrue(badge.isOffice)
    }

    @Test
    fun `getDayTypeBadgeInfo handles mixed day with completed home office block and active office block`() {
        val mixedDay = WorkDay(
            id = 11L,
            date = LocalDate.of(2026, 10, 11),
            dayType = DayType.WORK,
            location = WorkLocation.HOME_OFFICE,
            timeBlocks = listOf(
                TimeBlock(id = 101L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.HOME_OFFICE),
                TimeBlock(id = 102L, startTime = LocalTime.of(13, 0), endTime = null, location = WorkLocation.OFFICE)
            )
        )
        val badge = MonthEntriesHelper.getDayTypeBadgeInfo(mixedDay)
        assertEquals("🏢/🏠", badge.icon)
        assertEquals("Gemischt", badge.label)
    }

    @Test
    fun `isCompoundBadgeIcon returns true only for mixed badge and false for single emojis including vacation beach`() {
        // Vacation beach emoji UTF-16 length is 3 (surrogate pair + variation selector), but it is NOT compound
        assertEquals(3, "🏖️".length)
        assertFalse(MonthEntriesHelper.isCompoundBadgeIcon("🏖️"))
        assertFalse(MonthEntriesHelper.isCompoundBadgeIcon("🏢"))
        assertFalse(MonthEntriesHelper.isCompoundBadgeIcon("🏠"))
        assertFalse(MonthEntriesHelper.isCompoundBadgeIcon("✈️"))
        assertFalse(MonthEntriesHelper.isCompoundBadgeIcon("☕"))
        assertFalse(MonthEntriesHelper.isCompoundBadgeIcon("🩺"))
        assertFalse(MonthEntriesHelper.isCompoundBadgeIcon("⭐"))
        assertFalse(MonthEntriesHelper.isCompoundBadgeIcon("⏰"))
        assertTrue(MonthEntriesHelper.isCompoundBadgeIcon("🏢/🏠"))
    }

    @Test
    fun `formatWorkDayHeaderDate formats day name and date in German locale properly`() {
        val (prefix, suffix) = MonthEntriesHelper.formatWorkDayHeaderDate(LocalDate.of(2026, 10, 19))
        assertEquals("Mo, ", prefix)
        assertEquals("19. Okt.", suffix)

        val (prefixSun, suffixSun) = MonthEntriesHelper.formatWorkDayHeaderDate(LocalDate.of(2026, 5, 3))
        assertEquals("So, ", prefixSun)
        assertEquals("3. Mai.", suffixSun)
    }
}
