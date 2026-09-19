package com.flex.domain.usecase

import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.WorkDayRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

class SwitchLocationUseCase @Inject constructor(
    private val workDayRepository: WorkDayRepository
) {
    suspend operator fun invoke(targetLocation: WorkLocation? = null): Boolean {
        val today = LocalDate.now()
        val workDay = workDayRepository.getWorkDay(today).first() ?: return false
        val runningBlock = workDay.timeBlocks.find { it.endTime == null } ?: return false

        val newLocation = targetLocation ?: when (runningBlock.location) {
            WorkLocation.OFFICE -> WorkLocation.HOME_OFFICE
            WorkLocation.HOME_OFFICE -> WorkLocation.OFFICE
        }

        if (runningBlock.location == newLocation) return false

        // Update location of the currently running block
        workDayRepository.saveTimeBlock(runningBlock.copy(location = newLocation))

        // Update workday default location
        workDayRepository.saveWorkDay(workDay.copy(location = newLocation))
        return true
    }
}
