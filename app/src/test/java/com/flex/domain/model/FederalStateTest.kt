package com.flex.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FederalStateTest {

    @Test
    fun testAllSixteenBundeslaenderPresent() {
        assertThat(FederalState.entries).hasSize(16)
    }

    @Test
    fun testCountyCodeHasFormatDEDash() {
        for (state in FederalState.entries) {
            assertThat(state.countyCode).startsWith("DE-")
            assertThat(state.countyCode).hasLength(5)
        }
    }

    @Test
    fun testHamburgHasCodeHH() {
        assertThat(FederalState.HAMBURG.code).isEqualTo("HH")
        assertThat(FederalState.HAMBURG.countyCode).isEqualTo("DE-HH")
    }

    @Test
    fun testNoDuplicateCodes() {
        val codes = FederalState.entries.map { it.code }
        assertThat(codes).containsNoDuplicates()
    }

    @Test
    fun testAllDisplayNamesNonBlank() {
        for (state in FederalState.entries) {
            assertThat(state.displayName).isNotEmpty()
        }
    }

    @Test
    fun testKnownStatesPresent() {
        val codes = FederalState.entries.map { it.code }.toSet()
        assertThat(codes).containsAtLeast("BW", "BY", "BE", "BB", "HB", "HH", "HE", "MV", "NI", "NW", "RP", "SL", "SN", "ST", "SH", "TH")
    }
}
