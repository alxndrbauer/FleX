package com.vrema.domain.usecase

import com.vrema.domain.model.WorkDay
import com.vrema.domain.repository.WorkDayRepository
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth
import javax.inject.Inject

class GetMonthWorkDaysUseCase @Inject constructor(
    private val repository: WorkDayRepository
) {
    operator fun invoke(yearMonth: YearMonth): Flow<List<WorkDay>> {
        return repository.getWorkDaysForMonth(yearMonth)
    }
}
