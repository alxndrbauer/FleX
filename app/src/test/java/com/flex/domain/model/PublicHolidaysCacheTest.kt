package com.flex.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PublicHolidaysCacheTest {

    @AfterEach
    fun tearDown() {
        // Reset cache between tests
        PublicHolidays.clearCache()
    }

    @Test
    fun testDefaultWithNoCacheReturnsBuiltinHolidays() {
        val holidays = PublicHolidays.getHolidaysForYear(2025)
        // Built-in Hamburg holidays include Neujahr
        assertThat(holidays).containsKey(LocalDate.of(2025, 1, 1))
        assertThat(holidays[LocalDate.of(2025, 1, 1)]).isEqualTo("Neujahr")
    }

    @Test
    fun testAfterUpdateCacheReturnsCachedHolidays() {
        val customHolidays = mapOf(
            LocalDate.of(2025, 6, 15) to "TestFeiertag"
        )
        PublicHolidays.updateCache(2025, customHolidays)

        val result = PublicHolidays.getHolidaysForYear(2025)
        assertThat(result).containsKey(LocalDate.of(2025, 6, 15))
        assertThat(result[LocalDate.of(2025, 6, 15)]).isEqualTo("TestFeiertag")
    }

    @Test
    fun testCachedHolidaysOverrideBuiltin() {
        // Override with empty map — Neujahr should be gone
        PublicHolidays.updateCache(2025, emptyMap())

        val result = PublicHolidays.getHolidaysForYear(2025)
        assertThat(result).doesNotContainKey(LocalDate.of(2025, 1, 1))
    }

    @Test
    fun testUncachedYearFallsBackToBuiltin() {
        // Cache only 2025, 2026 should still use builtin
        PublicHolidays.updateCache(2025, mapOf(LocalDate.of(2025, 6, 15) to "TestFeiertag"))

        val result = PublicHolidays.getHolidaysForYear(2026)
        assertThat(result).containsKey(LocalDate.of(2026, 1, 1))
    }

    @Test
    fun testGetHolidayNameUsesCache() {
        val date = LocalDate.of(2025, 7, 4)
        PublicHolidays.updateCache(2025, mapOf(date to "Cached Holiday"))

        assertThat(PublicHolidays.getHolidayName(date)).isEqualTo("Cached Holiday")
    }

    @Test
    fun testIsHolidayUsesCache() {
        val date = LocalDate.of(2025, 7, 4)
        PublicHolidays.updateCache(2025, mapOf(date to "Cached Holiday"))

        assertThat(PublicHolidays.isHoliday(date)).isTrue()
    }

    @Test
    fun testGetBuiltinHolidaysIsIndependentOfCache() {
        PublicHolidays.updateCache(2025, emptyMap())

        // Builtin method always returns Hamburg holidays regardless of cache
        val builtin = PublicHolidays.getBuiltinHolidays(2025)
        assertThat(builtin).containsKey(LocalDate.of(2025, 1, 1))
    }
}
