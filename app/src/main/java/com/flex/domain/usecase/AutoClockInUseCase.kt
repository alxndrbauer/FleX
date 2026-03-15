package com.flex.domain.usecase

import com.flex.data.local.GeofencePreferences
import com.flex.domain.model.DayType
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.WorkDayRepository
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

class AutoClockInUseCase @Inject constructor(
    private val workDayRepository: WorkDayRepository,
    private val geofencePreferences: GeofencePreferences
) {
    suspend operator fun invoke(): Long? {
        val today = LocalDate.now()
        if (today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY) return null

        val existingDay = workDayRepository.getWorkDay(today).first()
        if (existingDay?.timeBlocks?.any { it.endTime == null } == true) return null

        val now = LocalTime.now().withSecond(0).withNano(0)
        val workDayId = if (existingDay == null) {
            workDayRepository.saveWorkDay(
                WorkDay(date = today, location = WorkLocation.OFFICE, dayType = DayType.WORK)
            )
        } else {
            existingDay.id
        }

        val blockId = workDayRepository.saveTimeBlock(
            TimeBlock(workDayId = workDayId, startTime = now, location = WorkLocation.OFFICE)
        )
        geofencePreferences.lastAutoTimeBlockId = blockId
        return blockId
    }
}
