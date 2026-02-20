package com.vrema.domain.repository

import com.vrema.domain.model.TimeBlock
import com.vrema.domain.model.WorkDay
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

interface WorkDayRepository {
    fun getWorkDaysForMonth(yearMonth: YearMonth): Flow<List<WorkDay>>
    fun getWorkDay(date: LocalDate): Flow<WorkDay?>
    suspend fun saveWorkDay(workDay: WorkDay): Long
    suspend fun deleteWorkDay(workDay: WorkDay)
    suspend fun saveTimeBlock(timeBlock: TimeBlock): Long
    suspend fun deleteTimeBlock(timeBlock: TimeBlock)
    fun getTimeBlocksForDay(workDayId: Long): Flow<List<TimeBlock>>
    fun getWorkDaysForYear(year: Int): Flow<List<WorkDay>>
    fun getWorkDaysInRange(start: LocalDate, end: LocalDate): Flow<List<WorkDay>>
    suspend fun confirmPlannedDays(yearMonth: YearMonth)
}
