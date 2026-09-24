package com.flex.domain.usecase

import com.flex.data.local.PausePreferences
import com.flex.domain.repository.WorkDayRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

class AutoClockOutUseCase @Inject constructor(
    private val workDayRepository: WorkDayRepository,
    private val pausePreferences: PausePreferences
) {
    suspend operator fun invoke(): Boolean {
        val wasPaused = pausePreferences.isPaused
        if (wasPaused) {
            pausePreferences.clearPause()
        }
        val today = LocalDate.now()
        val workDay = workDayRepository.getWorkDay(today).first() ?: return wasPaused
        val runningBlock = workDay.timeBlocks.find { it.endTime == null }
        if (runningBlock != null) {
            val now = LocalTime.now().withSecond(0).withNano(0)
            workDayRepository.saveTimeBlock(runningBlock.copy(endTime = now))
            return true
        }
        return wasPaused
    }
}
