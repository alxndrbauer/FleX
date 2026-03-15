package com.flex.data.repository

import com.flex.data.local.dao.TimeBlockDao
import com.flex.data.local.dao.WorkDayDao
import com.flex.data.local.entity.TimeBlockEntity
import com.flex.data.local.entity.WorkDayEntity
import com.flex.domain.model.DayType
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.WorkDayRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkDayRepositoryImpl @Inject constructor(
    private val workDayDao: WorkDayDao,
    private val timeBlockDao: TimeBlockDao
) : WorkDayRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getWorkDaysForMonth(yearMonth: YearMonth): Flow<List<WorkDay>> {
        val start = yearMonth.atDay(1).toString()
        val end = yearMonth.atEndOfMonth().toString()
        return workDayDao.getWorkDaysBetween(start, end).flatMapLatest { entities ->
            if (entities.isEmpty()) return@flatMapLatest flowOf(emptyList<WorkDay>())
            val ids = entities.map { it.id }.toSet()
            timeBlockDao.getAllTimeBlocksFlow().map { allBlocks ->
                val blocksByDayId = allBlocks.filter { it.workDayId in ids }.groupBy { it.workDayId }
                entities.map { entity ->
                    entity.toDomain().copy(
                        timeBlocks = blocksByDayId[entity.id]?.map { it.toDomain() } ?: emptyList()
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getWorkDay(date: LocalDate): Flow<WorkDay?> {
        return workDayDao.getWorkDayByDate(date.toString()).flatMapLatest { entity ->
            if (entity == null) return@flatMapLatest flowOf(null)
            timeBlockDao.getTimeBlocksForDay(entity.id).map { blocks ->
                entity.toDomain().copy(timeBlocks = blocks.map { it.toDomain() })
            }
        }
    }

    override suspend fun saveWorkDay(workDay: WorkDay): Long {
        val entity = workDay.toEntity()
        return if (entity.id == 0L) {
            workDayDao.insert(entity)
        } else {
            workDayDao.update(entity)
            entity.id
        }
    }

    override suspend fun deleteWorkDay(workDay: WorkDay) {
        workDayDao.delete(workDay.toEntity())
    }

    override suspend fun saveTimeBlock(timeBlock: TimeBlock): Long {
        return timeBlockDao.insert(timeBlock.toEntity())
    }

    override suspend fun deleteTimeBlock(timeBlock: TimeBlock) {
        timeBlockDao.delete(timeBlock.toEntity())
    }

    override fun getTimeBlocksForDay(workDayId: Long): Flow<List<TimeBlock>> {
        return timeBlockDao.getTimeBlocksForDay(workDayId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getWorkDaysForYear(year: Int): Flow<List<WorkDay>> {
        val start = LocalDate.of(year, 1, 1).toString()
        val end = LocalDate.of(year, 12, 31).toString()
        return workDayDao.getWorkDaysBetween(start, end).flatMapLatest { entities ->
            if (entities.isEmpty()) return@flatMapLatest flowOf(emptyList<WorkDay>())
            val ids = entities.map { it.id }.toSet()
            timeBlockDao.getAllTimeBlocksFlow().map { allBlocks ->
                val blocksByDayId = allBlocks.filter { it.workDayId in ids }.groupBy { it.workDayId }
                entities.map { entity ->
                    entity.toDomain().copy(
                        timeBlocks = blocksByDayId[entity.id]?.map { it.toDomain() } ?: emptyList()
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getWorkDaysInRange(start: LocalDate, end: LocalDate): Flow<List<WorkDay>> {
        return workDayDao.getWorkDaysBetween(start.toString(), end.toString()).flatMapLatest { entities ->
            if (entities.isEmpty()) return@flatMapLatest flowOf(emptyList<WorkDay>())
            val ids = entities.map { it.id }.toSet()
            timeBlockDao.getAllTimeBlocksFlow().map { allBlocks ->
                val blocksByDayId = allBlocks.filter { it.workDayId in ids }.groupBy { it.workDayId }
                entities.map { entity ->
                    entity.toDomain().copy(
                        timeBlocks = blocksByDayId[entity.id]?.map { it.toDomain() } ?: emptyList()
                    )
                }
            }
        }
    }

    override suspend fun getTimeBlockById(id: Long): TimeBlock? {
        return timeBlockDao.getTimeBlockById(id)?.toDomain()
    }

    override suspend fun confirmPlannedDays(yearMonth: YearMonth) {
        val start = yearMonth.atDay(1).toString()
        val end = yearMonth.atEndOfMonth().toString()
        workDayDao.confirmPlannedDays(start, end)
    }

    private fun WorkDayEntity.toDomain() = WorkDay(
        id = id,
        date = LocalDate.parse(date),
        location = WorkLocation.valueOf(location),
        dayType = DayType.valueOf(dayType),
        isPlanned = isPlanned,
        note = note
    )

    private fun WorkDay.toEntity() = WorkDayEntity(
        id = id,
        date = date.toString(),
        location = location.name,
        dayType = dayType.name,
        isPlanned = isPlanned,
        note = note
    )

    private fun TimeBlockEntity.toDomain() = TimeBlock(
        id = id,
        workDayId = workDayId,
        startTime = LocalTime.parse(startTime),
        endTime = endTime?.let { LocalTime.parse(it) },
        isDuration = isDuration,
        location = WorkLocation.valueOf(location)
    )

    private fun TimeBlock.toEntity() = TimeBlockEntity(
        id = id,
        workDayId = workDayId,
        startTime = startTime.toString(),
        endTime = endTime?.toString(),
        isDuration = isDuration,
        location = location.name
    )
}
