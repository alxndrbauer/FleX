package com.flex.domain.usecase

import com.flex.data.local.GeofencePreferences
import com.flex.domain.repository.WorkDayRepository
import kotlinx.coroutines.flow.first
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

        val snapshot = geofencePreferences.lastAutoClockInPlannedDaySnapshot
        if (snapshot?.workDayId == block.workDayId) {
            val currentDay = workDayRepository.getWorkDay(snapshot.date).first()
            if (currentDay?.id == snapshot.workDayId) {
                snapshot.durationBlocks.forEach { workDayRepository.saveTimeBlock(it) }
                workDayRepository.saveWorkDay(currentDay.copy(isPlanned = snapshot.wasPlanned))
            }
        }

        geofencePreferences.lastAutoClockInPlannedDaySnapshot = null
        geofencePreferences.lastAutoTimeBlockId = -1L
    }
}
