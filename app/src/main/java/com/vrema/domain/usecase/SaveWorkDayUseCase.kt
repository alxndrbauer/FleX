package com.vrema.domain.usecase

import com.vrema.domain.model.WorkDay
import com.vrema.domain.repository.WorkDayRepository
import javax.inject.Inject

class SaveWorkDayUseCase @Inject constructor(
    private val repository: WorkDayRepository
) {
    suspend operator fun invoke(workDay: WorkDay): Long {
        return repository.saveWorkDay(workDay)
    }
}
