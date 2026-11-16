package com.flex.domain.usecase

import com.flex.data.local.GeofencePreferences
import com.flex.domain.repository.WorkDayRepository
import javax.inject.Inject

/**
 * True while the last block created by an automatic stamp (geofence or WiFi) is still running.
 *
 * Used after a process restart to restore the WiFi monitor state without either skipping a
 * required clock-in or losing the pending clock-out.
 */
class IsAutoClockedInUseCase @Inject constructor(
    private val workDayRepository: WorkDayRepository,
    private val geofencePreferences: GeofencePreferences
) {
    suspend operator fun invoke(): Boolean {
        val blockId = geofencePreferences.lastAutoTimeBlockId
        if (blockId <= 0L) return false
        val block = workDayRepository.getTimeBlockById(blockId) ?: return false
        return block.endTime == null
    }
}
