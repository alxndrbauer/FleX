package com.flex.domain.usecase

import com.flex.domain.events.DataChangeEvent
import com.flex.domain.events.DataChangeEventBus
import com.flex.domain.repository.WorkDayRepository
import com.flex.wearable.WearSyncHelper
import java.time.LocalDate
import javax.inject.Inject

class AutoBookPlannedDaysUseCase @Inject constructor(
    private val workDayRepository: WorkDayRepository,
    private val wearSyncHelper: WearSyncHelper,
    private val dataChangeEventBus: DataChangeEventBus
) {
    suspend operator fun invoke(upToDate: LocalDate = LocalDate.now()): Int {
        val count = workDayRepository.confirmPlannedDaysUpTo(upToDate)
        if (count > 0) {
            dataChangeEventBus.emit(DataChangeEvent.WorkDayChanged)
            wearSyncHelper.push()
        }
        return count
    }
}
