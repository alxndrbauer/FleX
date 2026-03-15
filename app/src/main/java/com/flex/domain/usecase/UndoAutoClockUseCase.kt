package com.flex.domain.usecase

import com.flex.data.local.GeofencePreferences
import com.flex.domain.repository.WorkDayRepository
import javax.inject.Inject

class UndoAutoClockUseCase @Inject constructor(
    private val workDayRepository: WorkDayRepository,
    private val geofencePreferences: GeofencePreferences
) {
    suspend operator fun invoke() {
        val id = geofencePreferences.lastAutoTimeBlockId
        if (id == -1L) return
        val block = workDayRepository.getTimeBlockById(id) ?: return
        workDayRepository.deleteTimeBlock(block)
        geofencePreferences.lastAutoTimeBlockId = -1L
    }
}
