package com.flex.domain.usecase

import com.flex.domain.model.DayType
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.WorkDayRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

class ClockInUseCase @Inject constructor(
    private val workDayRepository: WorkDayRepository
) {
    suspend operator fun invoke(locationOverride: WorkLocation? = null): Boolean {
        val today = LocalDate.now()
        val existingDay = workDayRepository.getWorkDay(today).first()
        if (existingDay?.timeBlocks?.any { it.endTime == null } == true) return false

        val location = locationOverride ?: existingDay?.location ?: WorkLocation.OFFICE
        val now = LocalTime.now().withSecond(0).withNano(0)

        val workDayId = if (existingDay == null) {
            workDayRepository.saveWorkDay(
                WorkDay(date = today, location = location, dayType = DayType.WORK)
            )
        } else {
            if (existingDay.isPlanned || existingDay.location != location) {
                workDayRepository.saveWorkDay(
                    existingDay.copy(isPlanned = false, location = location)
                )
            }
            existingDay.id
        }

        workDayRepository.saveTimeBlock(
            TimeBlock(workDayId = workDayId, startTime = now, location = location)
        )
        return true
    }
}
