package com.flex.domain.repository

import com.flex.domain.model.FederalState
import java.time.LocalDate

interface HolidayRepository {
    suspend fun getHolidays(year: Int, federalState: FederalState): Map<LocalDate, String>
}
