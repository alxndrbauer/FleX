package com.flex.data.repository

import com.google.common.truth.Truth.assertThat
import com.flex.data.holidays.HolidayApiService
import com.flex.data.local.dao.HolidayCacheDao
import com.flex.domain.model.FederalState
import com.flex.domain.model.PublicHolidays
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class HolidayRepositoryImplTest {

    private lateinit var apiService: HolidayApiService
    private lateinit var holidayCacheDao: HolidayCacheDao
    private lateinit var repository: HolidayRepositoryImpl

    private val apiHolidays = mapOf(
        LocalDate.of(2025, 4, 18) to "Karfreitag",
        LocalDate.of(2025, 1, 1) to "Neujahr"
    )

    @BeforeEach
    fun setUp() {
        apiService = mock()
        holidayCacheDao = mock()
        repository = HolidayRepositoryImpl(apiService, holidayCacheDao)
        PublicHolidays.clearCache()
    }

    @AfterEach
    fun tearDown() {
        PublicHolidays.clearCache()
    }

    @Test
    fun testSuccessfulApiCallReturnsApiData() = runTest {
        whenever(apiService.fetchHolidays(2025, FederalState.HAMBURG)).thenReturn(apiHolidays)

        val result = repository.getHolidays(2025, FederalState.HAMBURG)

        // Result contains all API holidays plus cultural days (Ostersonntag, Pfingstsonntag, etc.)
        assertThat(result).containsEntry(LocalDate.of(2025, 1, 1), "Neujahr")
        assertThat(result).containsEntry(LocalDate.of(2025, 4, 18), "Karfreitag")
        assertThat(result.size).isAtLeast(apiHolidays.size)
    }

    @Test
    fun testFailedApiCallReturnsBuiltinFallback() = runTest {
        whenever(apiService.fetchHolidays(any(), any())).thenThrow(RuntimeException("Network error"))
        whenever(holidayCacheDao.getHolidays(any(), any())).thenReturn(emptyList())

        val result = repository.getHolidays(2025, FederalState.HAMBURG)

        // Should fall back to builtin Hamburg holidays
        assertThat(result).containsKey(LocalDate.of(2025, 1, 1))
        assertThat(result[LocalDate.of(2025, 1, 1)]).isEqualTo("Neujahr")
    }

    @Test
    fun testCacheHitDoesNotCallApiAgain() = runTest {
        whenever(apiService.fetchHolidays(2025, FederalState.HAMBURG)).thenReturn(apiHolidays)

        // Call twice
        repository.getHolidays(2025, FederalState.HAMBURG)
        repository.getHolidays(2025, FederalState.HAMBURG)

        // API should only be called once
        verify(apiService, times(1)).fetchHolidays(2025, FederalState.HAMBURG)
    }

    @Test
    fun testDifferentYearOrStateBypassesCache() = runTest {
        whenever(apiService.fetchHolidays(any(), any())).thenReturn(apiHolidays)

        repository.getHolidays(2025, FederalState.HAMBURG)
        repository.getHolidays(2026, FederalState.HAMBURG)
        repository.getHolidays(2025, FederalState.BAVARIA)

        verify(apiService, times(1)).fetchHolidays(2025, FederalState.HAMBURG)
        verify(apiService, times(1)).fetchHolidays(2026, FederalState.HAMBURG)
        verify(apiService, times(1)).fetchHolidays(2025, FederalState.BAVARIA)
    }
}
