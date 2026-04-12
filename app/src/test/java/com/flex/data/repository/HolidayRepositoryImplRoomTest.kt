package com.flex.data.repository

import com.flex.data.holidays.HolidayApiService
import com.flex.data.local.dao.HolidayCacheDao
import com.flex.data.local.entity.HolidayCacheEntity
import com.flex.domain.model.FederalState
import com.flex.domain.model.PublicHolidays
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class HolidayRepositoryImplRoomTest {

    private lateinit var apiService: HolidayApiService
    private lateinit var holidayCacheDao: HolidayCacheDao
    private lateinit var repository: HolidayRepositoryImpl

    private val year = 2025
    private val state = FederalState.HAMBURG

    private val apiHolidays = mapOf(
        LocalDate.of(2025, 1, 1) to "Neujahr",
        LocalDate.of(2025, 4, 18) to "Karfreitag"
    )

    private val roomEntities = listOf(
        HolidayCacheEntity(
            id = "2025-01-01_HH",
            date = "2025-01-01",
            name = "Neujahr",
            federalState = "HH",
            year = 2025
        ),
        HolidayCacheEntity(
            id = "2025-04-18_HH",
            date = "2025-04-18",
            name = "Karfreitag",
            federalState = "HH",
            year = 2025
        )
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
    fun `API success - insertAll is called with persisted holidays`() = runTest {
        whenever(apiService.fetchHolidays(year, state)).thenReturn(apiHolidays)

        repository.getHolidays(year, state)

        verify(holidayCacheDao, times(1)).deleteForYearAndState(year, state.code)
        verify(holidayCacheDao, times(1)).insertAll(any())
    }

    @Test
    fun `API failure - getHolidays is called on DAO and result is returned`() = runTest {
        whenever(apiService.fetchHolidays(any(), any())).thenThrow(RuntimeException("Network error"))
        whenever(holidayCacheDao.getHolidays(year, state.code)).thenReturn(roomEntities)

        val result = repository.getHolidays(year, state)

        verify(holidayCacheDao, times(1)).getHolidays(year, state.code)
        assertThat(result).containsKey(LocalDate.of(2025, 1, 1))
        assertThat(result[LocalDate.of(2025, 1, 1)]).isEqualTo("Neujahr")
    }

    @Test
    fun `API failure and Room empty - builtin fallback is returned`() = runTest {
        whenever(apiService.fetchHolidays(any(), any())).thenThrow(RuntimeException("Network error"))
        whenever(holidayCacheDao.getHolidays(year, state.code)).thenReturn(emptyList())

        val result = repository.getHolidays(year, state)

        verify(holidayCacheDao, times(1)).getHolidays(year, state.code)
        // Built-in Hamburg holidays always contain Neujahr
        assertThat(result).containsKey(LocalDate.of(2025, 1, 1))
        assertThat(result[LocalDate.of(2025, 1, 1)]).isEqualTo("Neujahr")
    }

    @Test
    fun `memory cache hit - neither API nor DAO is called on second request`() = runTest {
        whenever(apiService.fetchHolidays(year, state)).thenReturn(apiHolidays)

        // First call populates memory cache
        repository.getHolidays(year, state)
        // Second call should hit memory cache only
        repository.getHolidays(year, state)

        verify(apiService, times(1)).fetchHolidays(year, state)
        verify(holidayCacheDao, times(1)).insertAll(any())
        verify(holidayCacheDao, never()).getHolidays(any(), any())
    }
}
