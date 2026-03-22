package com.flex.wearable

import android.content.Context
import com.flex.domain.model.WorkDay
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.GetMonthWorkDaysUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearSyncHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workDayRepository: WorkDayRepository,
    private val getSettings: GetSettingsUseCase,
    private val getMonthWorkDays: GetMonthWorkDaysUseCase,
    private val calculateDayWorkTime: CalculateDayWorkTimeUseCase,
    private val calculateQuota: CalculateQuotaUseCase,
    private val calculateFlextime: CalculateFlextimeUseCase
) {

    suspend fun push() {
        try {
            val today = LocalDate.now()
            val workDay: WorkDay? = workDayRepository.getWorkDay(today).first()

            val isRunning = workDay?.timeBlocks?.any { it.endTime == null } ?: false
            val clockStartMinutes: Int = workDay?.timeBlocks
                ?.firstOrNull { it.endTime == null }
                ?.startTime
                ?.let { it.hour * 60 + it.minute }
                ?: -1

            val todayMinutes: Long = if (workDay != null) {
                val now = LocalTime.now()
                val blocks = workDay.timeBlocks.map { block ->
                    if (block.endTime == null) block.copy(endTime = now) else block
                }
                calculateDayWorkTime(blocks).netMinutes
            } else 0L

            val settings = getSettings().first()
            val yearMonth = YearMonth.now()
            val monthDays = getMonthWorkDays(yearMonth).first().filter { !it.isPlanned }
            val quota = calculateQuota(monthDays, settings, yearMonth)
            // Cumulative year-to-date flextime (same as HomeScreen), with today replaced by live data
            val yearDays = workDayRepository.getWorkDaysForYear(today.year).first()
                .filter { !it.isPlanned }
                .map { day -> if (day.date == today && workDay != null) workDay else day }
            val flextime = calculateFlextime(yearDays, settings, yearMonth)

            val request = PutDataMapRequest.create(WearContract.DATA_PATH).apply {
                dataMap.putBoolean(WearContract.KEY_IS_RUNNING, isRunning)
                dataMap.putLong(WearContract.KEY_TODAY_MIN, todayMinutes)
                dataMap.putDouble(WearContract.KEY_OFFICE_PCT, quota.officePercent)
                dataMap.putInt(WearContract.KEY_OFFICE_DAYS, quota.officeDays)
                dataMap.putBoolean(WearContract.KEY_QUOTA_MET, quota.quotaMet)
                dataMap.putInt(WearContract.KEY_CLOCK_START, clockStartMinutes)
                dataMap.putLong(WearContract.KEY_FLEXTIME_MIN, flextime.totalMinutes)
                dataMap.putLong(WearContract.KEY_OVERTIME_MIN, flextime.overtimeMinutes)
                dataMap.putInt(WearContract.KEY_REQUIRED_OFFICE_DAYS, quota.requiredOfficeDaysForQuota)
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(context).putDataItem(request).await()
        } catch (_: Exception) {
            // Silently ignore if no watch is connected
        }
    }
}
