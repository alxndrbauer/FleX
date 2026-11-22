package com.flex.data.repository

import com.flex.calendar.CalendarSyncService
import com.flex.calendar.VacationGroupUtil
import com.flex.calendar.VacationGroupUtil.VACATION_TYPES
import com.flex.data.local.dao.TimeBlockDao
import com.flex.data.local.dao.WorkDayDao
import com.flex.data.local.entity.TimeBlockEntity
import com.flex.data.local.entity.WorkDayEntity
import com.flex.domain.model.DayType
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.model.YearClosing
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
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
    private val timeBlockDao: TimeBlockDao,
    private val calendarSyncService: CalendarSyncService,
    private val settingsRepository: SettingsRepository,
    private val calculateFlextime: CalculateFlextimeUseCase = CalculateFlextimeUseCase(CalculateDayWorkTimeUseCase())
) : WorkDayRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeWorkDaysWithBlocks(
        entitiesFlow: Flow<List<WorkDayEntity>>
    ): Flow<List<WorkDay>> {
        return entitiesFlow.flatMapLatest { entities ->
            if (entities.isEmpty()) return@flatMapLatest flowOf(emptyList())
            val idList = entities.map { it.id }
            val blocksFlow: Flow<List<TimeBlockEntity>> = if (idList.size <= 500) {
                timeBlockDao.getTimeBlocksForDaysFlow(idList)
            } else {
                val chunkFlows = idList.chunked(500).map { chunk ->
                    timeBlockDao.getTimeBlocksForDaysFlow(chunk)
                }
                combine(chunkFlows) { arrays ->
                    arrays.flatMap { it }
                }
            }
            blocksFlow.map { blocks ->
                val blocksByDayId = blocks.groupBy { it.workDayId }
                entities.map { entity ->
                    entity.toDomain().copy(
                        timeBlocks = blocksByDayId[entity.id]?.map { it.toDomain() } ?: emptyList()
                    )
                }
            }
        }
    }

    override fun getWorkDaysForMonth(yearMonth: YearMonth): Flow<List<WorkDay>> {
        val start = yearMonth.atDay(1).toString()
        val end = yearMonth.atEndOfMonth().toString()
        return observeWorkDaysWithBlocks(workDayDao.getWorkDaysBetween(start, end))
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
        val savedId = if (entity.id == 0L) {
            workDayDao.insert(entity)
        } else {
            workDayDao.update(entity)
            entity.id
        }
        val settings = settingsRepository.getSettings().firstOrNull()
        if (settings != null && settings.calendarSyncEnabled) {
            val savedWorkDay = workDay.copy(id = savedId)
            if (savedWorkDay.dayType in VACATION_TYPES) {
                val run = findVacationRun(savedWorkDay)
                calendarSyncService.syncVacationGroup(run, settings)
            } else {
                val blocks = timeBlockDao.getTimeBlocksForDays(listOf(savedId))
                val workDayWithBlocks = savedWorkDay.copy(timeBlocks = blocks.map { it.toDomain() })
                calendarSyncService.syncWorkDay(workDayWithBlocks, settings)
            }
        }
        cascadeClosedYearsIfNeeded(workDay.date.year)
        return savedId
    }

    override suspend fun deleteWorkDay(workDay: WorkDay) {
        val settings = settingsRepository.getSettings().firstOrNull()
        if (settings != null && settings.calendarSyncEnabled && workDay.dayType in VACATION_TYPES) {
            val remainingIds = calendarSyncService.deleteGroupEventForWorkDay(workDay.id)
            workDayDao.delete(workDay.toEntity())
            if (remainingIds.isNotEmpty()) {
                val remainingDays = remainingIds.mapNotNull { workDayDao.getWorkDayById(it)?.toDomain() }
                val runs = VacationGroupUtil.groupConsecutiveRuns(remainingDays)
                runs.forEach { calendarSyncService.syncVacationGroup(it, settings) }
            }
        } else {
            val prefix = settings?.calendarEventPrefix ?: "FleX"
            calendarSyncService.deleteCalendarEvent(workDay, prefix)
            workDayDao.delete(workDay.toEntity())
        }
        cascadeClosedYearsIfNeeded(workDay.date.year)
    }

    private suspend fun findVacationRun(workDay: WorkDay): List<WorkDay> {
        val allOfType = workDayDao.getWorkDaysByType(workDay.dayType.name).map { it.toDomain() }
        val runs = VacationGroupUtil.groupConsecutiveRuns(allOfType)
        return runs.find { run -> run.any { it.id == workDay.id } } ?: listOf(workDay)
    }

    override suspend fun saveTimeBlock(timeBlock: TimeBlock): Long {
        val savedId = timeBlockDao.insert(timeBlock.toEntity())
        // Re-sync the parent WorkDay so block-location changes are reflected in the calendar
        val settings = settingsRepository.getSettings().firstOrNull()
        val workDayEntity = workDayDao.getWorkDayById(timeBlock.workDayId)
        if (settings != null && settings.calendarSyncEnabled && workDayEntity != null) {
            val allBlocks = timeBlockDao.getTimeBlocksForDays(listOf(timeBlock.workDayId))
            val workDay = workDayEntity.toDomain().copy(
                timeBlocks = allBlocks.map { it.toDomain() }
            )
            calendarSyncService.syncWorkDay(workDay, settings)
        }
        workDayEntity?.let { cascadeClosedYearsIfNeeded(LocalDate.parse(it.date).year) }
        return savedId
    }

    override suspend fun deleteTimeBlock(timeBlock: TimeBlock) {
        val workDayEntity = workDayDao.getWorkDayById(timeBlock.workDayId)
        timeBlockDao.delete(timeBlock.toEntity())
        val settings = settingsRepository.getSettings().firstOrNull()
        if (settings != null && settings.calendarSyncEnabled && workDayEntity != null) {
            val remainingBlocks = timeBlockDao.getTimeBlocksForDays(listOf(timeBlock.workDayId))
            val workDay = workDayEntity.toDomain().copy(
                timeBlocks = remainingBlocks.map { it.toDomain() }
            )
            calendarSyncService.syncWorkDay(workDay, settings)
        }
        workDayEntity?.let { cascadeClosedYearsIfNeeded(LocalDate.parse(it.date).year) }
    }

    override fun getTimeBlocksForDay(workDayId: Long): Flow<List<TimeBlock>> {
        return timeBlockDao.getTimeBlocksForDay(workDayId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getWorkDaysForYear(year: Int): Flow<List<WorkDay>> {
        val start = LocalDate.of(year, 1, 1).toString()
        val end = LocalDate.of(year, 12, 31).toString()
        return observeWorkDaysWithBlocks(workDayDao.getWorkDaysBetween(start, end))
    }

    override fun getWorkDaysInRange(start: LocalDate, end: LocalDate): Flow<List<WorkDay>> {
        return observeWorkDaysWithBlocks(workDayDao.getWorkDaysBetween(start.toString(), end.toString()))
    }

    override suspend fun getTimeBlockById(id: Long): TimeBlock? {
        return timeBlockDao.getTimeBlockById(id)?.toDomain()
    }

    override suspend fun confirmPlannedDays(yearMonth: YearMonth) {
        val start = yearMonth.atDay(1).toString()
        val end = yearMonth.atEndOfMonth().toString()
        workDayDao.confirmPlannedDays(start, end)
        cascadeClosedYearsIfNeeded(yearMonth.year)
    }

    override suspend fun confirmPlannedDaysUpTo(date: LocalDate): Int {
        val dateStr = date.toString()
        val earliestPlanned = workDayDao.getEarliestPlannedDateUpTo(dateStr)
        val count = workDayDao.confirmPlannedDaysUpTo(dateStr)
        if (count > 0 && earliestPlanned != null) {
            cascadeClosedYearsIfNeeded(LocalDate.parse(earliestPlanned).year)
        }
        return count
    }

    private suspend fun cascadeClosedYearsIfNeeded(affectedYear: Int) {
        val settings = settingsRepository.getSettings().firstOrNull() ?: return
        if (affectedYear >= settings.settingsYear) return

        val closings = runCatching { settingsRepository.getYearClosings() }.getOrNull()
            ?.firstOrNull()
            .orEmpty()
            .associateBy { it.year }
        if (closings.isEmpty()) return

        val startYear = closings.keys
            .filter { it >= affectedYear && it < settings.settingsYear }
            .minOrNull() ?: return

        val workTimeRules = settingsRepository.getWorkTimeRules().firstOrNull().orEmpty()

        var prevEndFlex = closings[startYear]?.startFlextimeMinutes ?: 0
        var prevEndOvertime = closings[startYear]?.startOvertimeMinutes ?: 0
        var prevRemaining = 0
        var prevManual = false

        for (y in startYear until settings.settingsYear) {
            val existing = closings[y]
            val startFlex = if (y == startYear) existing?.startFlextimeMinutes ?: prevEndFlex else prevEndFlex
            val startOvertime = if (y == startYear) existing?.startOvertimeMinutes ?: prevEndOvertime else prevEndOvertime
            val carryOver = if (y == startYear || prevManual) {
                existing?.carryOverVacationDays ?: prevRemaining
            } else {
                prevRemaining
            }
            val annual = existing?.annualVacationDays ?: settings.annualVacationDays
            val special = existing?.specialVacationDays ?: settings.specialVacationDays
            val specialPaidOut = existing?.specialVacationPaidOut ?: false

            val startStr = LocalDate.of(y, 1, 1).toString()
            val endStr = LocalDate.of(y, 12, 31).toString()
            val dayEntities = workDayDao.getWorkDaysBetweenSync(startStr, endStr)
            val blocksByDayId = if (dayEntities.isEmpty()) {
                emptyMap()
            } else {
                dayEntities.map { it.id }
                    .chunked(500)
                    .flatMap { chunk -> timeBlockDao.getTimeBlocksForDays(chunk) }
                    .groupBy { it.workDayId }
            }
            val actualDays = dayEntities
                .filter { !it.isPlanned }
                .map { entity ->
                    entity.toDomain().copy(
                        timeBlocks = blocksByDayId[entity.id]?.map { it.toDomain() } ?: emptyList()
                    )
                }

            val usedVacation = actualDays.count { it.dayType == DayType.VACATION }
            val yearSettings = settings.copy(
                settingsYear = y,
                annualVacationDays = annual,
                carryOverVacationDays = carryOver,
                specialVacationDays = special,
                specialVacationPaidOut = specialPaidOut,
                initialFlextimeMinutes = startFlex,
                initialOvertimeMinutes = startOvertime
            )
            val balance = calculateFlextime(actualDays, yearSettings, null, workTimeRules)
            val endFlex = balance.totalMinutes.toInt()
            val endOvertime = balance.overtimeMinutes.toInt()
            val remaining = maxOf(0, annual + carryOver - usedVacation)
            val manual = existing?.carryOverToNextYearManual ?: false

            if (existing != null) {
                val updated = existing.copy(
                    carryOverVacationDays = carryOver,
                    startFlextimeMinutes = startFlex,
                    startOvertimeMinutes = startOvertime,
                    endFlextimeMinutes = endFlex,
                    endOvertimeMinutes = endOvertime,
                    usedVacationDays = usedVacation
                )
                settingsRepository.saveYearClosing(updated)
            }

            prevEndFlex = endFlex
            prevEndOvertime = endOvertime
            prevRemaining = remaining
            prevManual = manual
        }

        val updatedCarryOver = if (prevManual) settings.carryOverVacationDays else prevRemaining
        settingsRepository.saveSettings(
            settings.copy(
                initialFlextimeMinutes = prevEndFlex,
                initialOvertimeMinutes = prevEndOvertime,
                carryOverVacationDays = updatedCarryOver
            )
        )
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
