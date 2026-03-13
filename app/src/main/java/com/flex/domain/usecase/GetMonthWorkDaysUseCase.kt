package com.flex.domain.usecase

import com.flex.domain.model.WorkDay
import com.flex.domain.repository.WorkDayRepository
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
