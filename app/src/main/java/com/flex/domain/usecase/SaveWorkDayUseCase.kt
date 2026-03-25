package com.flex.domain.usecase

import com.flex.domain.model.WorkDay
import com.flex.domain.repository.WorkDayRepository
import javax.inject.Inject

class SaveWorkDayUseCase @Inject constructor(
    private val repository: WorkDayRepository
) {
    suspend operator fun invoke(workDay: WorkDay): Long {
        return repository.saveWorkDay(workDay)
    }
}
