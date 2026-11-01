package com.flex.domain.usecase

import com.flex.data.local.PausePreferences
import com.flex.domain.repository.WorkDayRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

class PauseWorkUseCase @Inject constructor(
    private val workDayRepository: WorkDayRepository,
    private val pausePreferences: PausePreferences,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    suspend operator fun invoke(): Boolean {
        val today = LocalDate.now(clock)
        val workDay = workDayRepository.getWorkDay(today).first()
        val runningBlock = workDay?.timeBlocks?.find { it.endTime == null }
        if (runningBlock == null) return false

        val now = LocalTime.now(clock).withSecond(0).withNano(0)
        pausePreferences.pauseStartTime = System.currentTimeMillis()
        workDayRepository.saveTimeBlock(runningBlock.copy(endTime = now))
        return true
    }
}
