package com.flex.data.repository

import com.flex.data.holidays.HolidayApiService
import com.flex.data.local.dao.HolidayCacheDao
import com.flex.data.local.entity.HolidayCacheEntity
import com.flex.domain.model.FederalState
import com.flex.domain.model.PublicHolidays
import com.flex.domain.repository.HolidayRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HolidayRepositoryImpl @Inject constructor(
    private val apiService: HolidayApiService,
    private val holidayCacheDao: HolidayCacheDao
) : HolidayRepository {

    private val memoryCache = mutableMapOf<Pair<Int, FederalState>, Map<LocalDate, String>>()

    override suspend fun getHolidays(year: Int, federalState: FederalState): Map<LocalDate, String> {
        val key = year to federalState
        memoryCache[key]?.let { return it }

        // 1. Try API → on success persist to Room + memory cache
        val apiResult = runCatching { apiService.fetchHolidays(year, federalState) }.getOrNull()
        if (apiResult != null) {
            val withCultural = apiResult + culturalDays(year)
            persistToRoom(year, federalState, withCultural)
            memoryCache[key] = withCultural
            return withCultural
        }

        // 2. Room fallback
        val roomResult = loadFromRoom(year, federalState)
        if (roomResult.isNotEmpty()) {
            memoryCache[key] = roomResult
            return roomResult
        }

        // 3. Built-in Hamburg fallback
        val builtin = PublicHolidays.getBuiltinHolidays(year) + culturalDays(year)
        memoryCache[key] = builtin
        return builtin
    }

    private suspend fun persistToRoom(
        year: Int,
        federalState: FederalState,
        holidays: Map<LocalDate, String>
    ) {
        holidayCacheDao.deleteForYearAndState(year, federalState.code)
        val entities = holidays.map { (date, name) ->
            HolidayCacheEntity(
                id = "${date}_${federalState.code}",
                date = date.toString(),
                name = name,
                federalState = federalState.code,
                year = year
            )
        }
        holidayCacheDao.insertAll(entities)
    }

    private suspend fun loadFromRoom(year: Int, federalState: FederalState): Map<LocalDate, String> {
        return holidayCacheDao.getHolidays(year, federalState.code)
            .associate { LocalDate.parse(it.date) to it.name }
    }

    /**
     * Culturally significant days that are not official public holidays but were traditionally
     * shown in the app (e.g. Ostersonntag is only legal in Brandenburg, but shown everywhere).
     * These are merged with the API results so they always appear regardless of Bundesland.
     */
    private fun culturalDays(year: Int): Map<LocalDate, String> {
        val easter = PublicHolidays.calculateEasterPublic(year)
        return mapOf(
            easter to "Ostersonntag",
            easter.plusDays(49) to "Pfingstsonntag",
            LocalDate.of(year, 12, 24) to "Heiligabend",
            LocalDate.of(year, 12, 31) to "Silvester"
        )
    }
}
