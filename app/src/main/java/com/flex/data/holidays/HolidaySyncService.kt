package com.flex.data.holidays

import com.flex.domain.model.PublicHolidays
import com.flex.domain.repository.HolidayRepository
import com.flex.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HolidaySyncService @Inject constructor(
    private val holidayRepository: HolidayRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend fun syncForCurrentAndNextYear() {
        val settings = settingsRepository.getSettings().first()
        val currentYear = LocalDate.now().year
        listOf(currentYear, currentYear + 1).forEach { year ->
            val holidays = holidayRepository.getHolidays(year, settings.federalState)
            PublicHolidays.updateCache(year, holidays)
        }
    }
}
