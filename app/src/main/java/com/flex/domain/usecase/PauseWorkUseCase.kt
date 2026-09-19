package com.flex.domain.usecase

import com.flex.data.local.PausePreferences
import com.flex.domain.repository.WorkDayRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

class PauseWorkUseCase @Inject constructor(
    private val workDayRepository: WorkDayRepository,
    private val pausePreferences: PausePreferences
) {
    suspend operator fun invoke(): Boolean {
        val today = LocalDate.now()
        val workDay = workDayRepository.getWorkDay(today).first()
        val runningBlock = workDay?.timeBlocks?.find { it.endTime == null }
        if (runningBlock == null) return false

        val now = LocalTime.now().withSecond(0).withNano(0)
        pausePreferences.pauseStartTime = System.currentTimeMillis()
        workDayRepository.saveTimeBlock(runningBlock.copy(endTime = now))
        return true
    }
}
