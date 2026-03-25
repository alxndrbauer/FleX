package com.flex.domain.usecase

import com.flex.domain.repository.WorkDayRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

class AutoClockOutUseCase @Inject constructor(
    private val workDayRepository: WorkDayRepository
) {
    suspend operator fun invoke(): Boolean {
        val today = LocalDate.now()
        val workDay = workDayRepository.getWorkDay(today).first() ?: return false
        val runningBlock = workDay.timeBlocks.find { it.endTime == null } ?: return false
        val now = LocalTime.now().withSecond(0).withNano(0)
        workDayRepository.saveTimeBlock(runningBlock.copy(endTime = now))
        return true
    }
}
