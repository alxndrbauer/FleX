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

class WearClockInUseCase @Inject constructor(
    private val workDayRepository: WorkDayRepository
) {
    suspend operator fun invoke() {
        val today = LocalDate.now()
        val existingDay = workDayRepository.getWorkDay(today).first()
        if (existingDay?.timeBlocks?.any { it.endTime == null } == true) return

        val now = LocalTime.now().withSecond(0).withNano(0)
        val workDayId = if (existingDay == null) {
            workDayRepository.saveWorkDay(
                WorkDay(date = today, location = WorkLocation.OFFICE, dayType = DayType.WORK)
            )
        } else {
            existingDay.id
        }

        workDayRepository.saveTimeBlock(
            TimeBlock(workDayId = workDayId, startTime = now, location = WorkLocation.OFFICE)
        )
    }
}
