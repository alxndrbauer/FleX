package com.flex.data.holidays

import com.google.common.truth.Truth.assertThat
import com.flex.domain.model.FederalState
import org.junit.jupiter.api.Test
import java.time.LocalDate

class HolidayApiServiceTest {

    private val testJson = """
        [
          {"date":"2025-01-01","localName":"Neujahr","counties":null},
          {"date":"2025-10-31","localName":"Reformationstag","counties":["DE-HH","DE-HB","DE-MV","DE-NI","DE-SH"]},
          {"date":"2025-11-01","localName":"Allerheiligen","counties":["DE-BW","DE-BY","DE-NW","DE-RP","DE-SL"]}
        ]
    """.trimIndent()

    @Test
    fun testHamburgGetsNeujahrAndReformationstag() {
        val service = HolidayApiService()
        val result = service.parseHolidays(testJson, FederalState.HAMBURG)

        assertThat(result).containsKey(LocalDate.of(2025, 1, 1))
        assertThat(result[LocalDate.of(2025, 1, 1)]).isEqualTo("Neujahr")
        assertThat(result).containsKey(LocalDate.of(2025, 10, 31))
        assertThat(result[LocalDate.of(2025, 10, 31)]).isEqualTo("Reformationstag")
    }

    @Test
    fun testHamburgDoesNotGetAllerheiligen() {
        val service = HolidayApiService()
        val result = service.parseHolidays(testJson, FederalState.HAMBURG)

        assertThat(result).doesNotContainKey(LocalDate.of(2025, 11, 1))
    }

    @Test
    fun testBavariaGetsNeujahrAndAllerheiligen() {
        val service = HolidayApiService()
        val result = service.parseHolidays(testJson, FederalState.BAVARIA)

        assertThat(result).containsKey(LocalDate.of(2025, 1, 1))
        assertThat(result[LocalDate.of(2025, 1, 1)]).isEqualTo("Neujahr")
        assertThat(result).containsKey(LocalDate.of(2025, 11, 1))
        assertThat(result[LocalDate.of(2025, 11, 1)]).isEqualTo("Allerheiligen")
    }

    @Test
    fun testBavariaDoesNotGetReformationstag() {
        val service = HolidayApiService()
        val result = service.parseHolidays(testJson, FederalState.BAVARIA)

        assertThat(result).doesNotContainKey(LocalDate.of(2025, 10, 31))
    }

    @Test
    fun testNationalHolidayIncludedForAllStates() {
        val service = HolidayApiService()
        for (state in FederalState.entries) {
            val result = service.parseHolidays(testJson, state)
            assertThat(result).containsKey(LocalDate.of(2025, 1, 1))
        }
    }

    @Test
    fun testEmptyJsonReturnsEmptyMap() {
        val service = HolidayApiService()
        val result = service.parseHolidays("[]", FederalState.HAMBURG)
        assertThat(result).isEmpty()
    }
}
