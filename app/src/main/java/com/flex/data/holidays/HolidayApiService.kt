package com.flex.data.holidays

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.flex.domain.model.FederalState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class NagerHolidayDto(
    val date: String,
    val localName: String,
    val counties: List<String>?
)

@Singleton
class HolidayApiService @Inject constructor() {

    private val gson = Gson()

    suspend fun fetchHolidays(year: Int, federalState: FederalState): Map<LocalDate, String> =
        withContext(Dispatchers.IO) {
            val url = "https://date.nager.at/api/v3/PublicHolidays/$year/DE"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            try {
                val json = connection.inputStream.bufferedReader().readText()
                parseHolidays(json, federalState)
            } finally {
                connection.disconnect()
            }
        }

    fun parseHolidays(json: String, federalState: FederalState): Map<LocalDate, String> {
        val type = object : TypeToken<List<NagerHolidayDto>>() {}.type
        val dtos: List<NagerHolidayDto> = gson.fromJson(json, type) ?: emptyList()
        return dtos
            .filter { dto ->
                dto.counties == null || dto.counties.contains(federalState.countyCode)
            }
            .associate { dto ->
                LocalDate.parse(dto.date) to dto.localName
            }
    }
}
