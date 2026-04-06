package com.flex.data.repository

import com.google.common.truth.Truth.assertThat
import com.flex.BaseUnitTest
import com.flex.calendar.CalendarSyncService
import com.flex.data.local.dao.TimeBlockDao
import com.flex.data.local.dao.WorkDayDao
import com.flex.data.local.entity.TimeBlockEntity
import com.flex.data.local.entity.WorkDayEntity
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * Unit tests for WorkDayRepositoryImpl.
 * Tests repository operations with mocked DAOs (no real database).
 */
class WorkDayRepositoryImplTest : BaseUnitTest() {

    @Mock
    private lateinit var workDayDao: WorkDayDao

    @Mock
    private lateinit var timeBlockDao: TimeBlockDao

    @Mock
    private lateinit var calendarSyncService: CalendarSyncService

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    private lateinit var repository: WorkDayRepositoryImpl

    @BeforeEach
    override fun setUp() {
        super.setUp()
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(Settings()))
        repository = WorkDayRepositoryImpl(workDayDao, timeBlockDao, calendarSyncService, settingsRepository)
    }

    // ========== getWorkDay Tests ==========

    @Test
    fun `getWorkDay returns correct WorkDay with TimeBlocks`() = runTest {
        // Given: A WorkDayEntity and its TimeBlocks
        val date = LocalDate.of(2025, 2, 15)
        val workDayEntity = WorkDayEntity(
            id = 1L,
            date = date.toString(),
            location = WorkLocation.OFFICE.name,
            dayType = DayType.WORK.name,
            isPlanned = false,
            note = "Test note"
        )
        val timeBlockEntities = listOf(
            TimeBlockEntity(
                id = 1L,
                workDayId = 1L,
                startTime = "09:00",
                endTime = "12:00",
                isDuration = false
            ),
            TimeBlockEntity(
                id = 2L,
                workDayId = 1L,
                startTime = "13:00",
                endTime = "17:00",
                isDuration = false
            )
        )

        whenever(workDayDao.getWorkDayByDate(date.toString())).thenReturn(flowOf(workDayEntity))
        whenever(timeBlockDao.getTimeBlocksForDay(1L)).thenReturn(flowOf(timeBlockEntities))

        // When: Getting work day
        val result = repository.getWorkDay(date).first()

        // Then: Should return correctly mapped WorkDay with TimeBlocks
        assertThat(result).isNotNull()
        assertThat(result?.id).isEqualTo(1L)
        assertThat(result?.date).isEqualTo(date)
        assertThat(result?.location).isEqualTo(WorkLocation.OFFICE)
        assertThat(result?.dayType).isEqualTo(DayType.WORK)
        assertThat(result?.isPlanned).isFalse()
        assertThat(result?.note).isEqualTo("Test note")
        assertThat(result?.timeBlocks).hasSize(2)
        assertThat(result?.timeBlocks?.get(0)?.startTime).isEqualTo(LocalTime.of(9, 0))
        assertThat(result?.timeBlocks?.get(0)?.endTime).isEqualTo(LocalTime.of(12, 0))
        assertThat(result?.timeBlocks?.get(1)?.startTime).isEqualTo(LocalTime.of(13, 0))
        assertThat(result?.timeBlocks?.get(1)?.endTime).isEqualTo(LocalTime.of(17, 0))
    }

    @Test
    fun `getWorkDay returns null when no WorkDay exists for date`() = runTest {
        // Given: DAO returns null (no work day for this date)
        val date = LocalDate.of(2025, 2, 15)
        whenever(workDayDao.getWorkDayByDate(date.toString())).thenReturn(flowOf(null))

        // When: Getting work day
        val result = repository.getWorkDay(date).first()

        // Then: Should return null
        assertThat(result).isNull()
    }

    @Test
    fun `getWorkDay returns WorkDay with empty timeBlocks when no blocks exist`() = runTest {
        // Given: WorkDayEntity without TimeBlocks
        val date = LocalDate.of(2025, 2, 15)
        val workDayEntity = WorkDayEntity(
            id = 1L,
            date = date.toString(),
            location = WorkLocation.HOME_OFFICE.name,
            dayType = DayType.WORK.name
        )

        whenever(workDayDao.getWorkDayByDate(date.toString())).thenReturn(flowOf(workDayEntity))
        whenever(timeBlockDao.getTimeBlocksForDay(1L)).thenReturn(flowOf(emptyList()))

        // When: Getting work day
        val result = repository.getWorkDay(date).first()

        // Then: Should return WorkDay with empty timeBlocks
        assertThat(result).isNotNull()
        assertThat(result?.timeBlocks).isEmpty()
    }

    // ========== saveWorkDay Tests ==========

    @Test
    fun `saveWorkDay saves and returns correct ID`() = runTest {
        // Given: A WorkDay to save
        val workDay = WorkDay(
            id = 0L,
            date = LocalDate.of(2025, 2, 15),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = true,
            note = "Important meeting"
        )
        val expectedId = 5L

        whenever(workDayDao.insert(
            WorkDayEntity(
                id = 0L,
                date = "2025-02-15",
                location = "OFFICE",
                dayType = "WORK",
                isPlanned = true,
                note = "Important meeting"
            )
        )).thenReturn(expectedId)

        // When: Saving work day
        val resultId = repository.saveWorkDay(workDay)

        // Then: Should return the ID from DAO
        assertThat(resultId).isEqualTo(expectedId)
        verify(workDayDao).insert(
            WorkDayEntity(
                id = 0L,
                date = "2025-02-15",
                location = "OFFICE",
                dayType = "WORK",
                isPlanned = true,
                note = "Important meeting"
            )
        )
    }

    @Test
    fun `saveWorkDay correctly maps dayType and location`() = runTest {
        // Given: WorkDay with HOME_OFFICE and VACATION
        val workDay = WorkDay(
            date = LocalDate.of(2025, 2, 15),
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.VACATION
        )

        whenever(workDayDao.insert(
            WorkDayEntity(
                id = 0L,
                date = "2025-02-15",
                location = "HOME_OFFICE",
                dayType = "VACATION",
                isPlanned = false,
                note = null
            )
        )).thenReturn(1L)

        // When: Saving work day
        repository.saveWorkDay(workDay)

        // Then: Verify correct enum mapping
        verify(workDayDao).insert(
            WorkDayEntity(
                id = 0L,
                date = "2025-02-15",
                location = "HOME_OFFICE",
                dayType = "VACATION",
                isPlanned = false,
                note = null
            )
        )
    }

    // ========== deleteWorkDay Tests ==========

    @Test
    fun `deleteWorkDay removes WorkDay from persistence`() = runTest {
        // Given: A WorkDay to delete
        val workDay = WorkDay(
            id = 3L,
            date = LocalDate.of(2025, 2, 15),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK
        )

        // When: Deleting work day
        repository.deleteWorkDay(workDay)

        // Then: DAO delete should be called
        verify(workDayDao).delete(
            WorkDayEntity(
                id = 3L,
                date = "2025-02-15",
                location = "OFFICE",
                dayType = "WORK",
                isPlanned = false,
                note = null
            )
        )
    }

    // ========== TimeBlock Tests ==========

    @Test
    fun `saveTimeBlock saves and returns correct ID`() = runTest {
        // Given: A TimeBlock to save
        val timeBlock = TimeBlock(
            id = 0L,
            workDayId = 5L,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(17, 0),
            isDuration = false
        )
        val expectedId = 10L

        whenever(timeBlockDao.insert(
            TimeBlockEntity(
                id = 0L,
                workDayId = 5L,
                startTime = "09:00",
                endTime = "17:00",
                isDuration = false
            )
        )).thenReturn(expectedId)

        // When: Saving time block
        val resultId = repository.saveTimeBlock(timeBlock)

        // Then: Should return the ID from DAO
        assertThat(resultId).isEqualTo(expectedId)
    }

    @Test
    fun `saveTimeBlock correctly handles null endTime for running blocks`() = runTest {
        // Given: TimeBlock with null endTime (currently running)
        val timeBlock = TimeBlock(
            id = 0L,
            workDayId = 5L,
            startTime = LocalTime.of(9, 0),
            endTime = null,  // Running block
            isDuration = false
        )

        whenever(timeBlockDao.insert(
            TimeBlockEntity(
                id = 0L,
                workDayId = 5L,
                startTime = "09:00",
                endTime = null,
                isDuration = false
            )
        )).thenReturn(1L)

        // When: Saving time block
        repository.saveTimeBlock(timeBlock)

        // Then: Verify null endTime is preserved
        verify(timeBlockDao).insert(
            TimeBlockEntity(
                id = 0L,
                workDayId = 5L,
                startTime = "09:00",
                endTime = null,
                isDuration = false
            )
        )
    }

    @Test
    fun `saveTimeBlock correctly handles isDuration flag`() = runTest {
        // Given: TimeBlock with isDuration = true
        val timeBlock = TimeBlock(
            id = 0L,
            workDayId = 5L,
            startTime = LocalTime.of(7, 30),
            endTime = null,
            isDuration = true
        )

        whenever(timeBlockDao.insert(
            TimeBlockEntity(
                id = 0L,
                workDayId = 5L,
                startTime = "07:30",
                endTime = null,
                isDuration = true
            )
        )).thenReturn(1L)

        // When: Saving time block
        repository.saveTimeBlock(timeBlock)

        // Then: Verify isDuration flag is preserved
        verify(timeBlockDao).insert(
            TimeBlockEntity(
                id = 0L,
                workDayId = 5L,
                startTime = "07:30",
                endTime = null,
                isDuration = true
            )
        )
    }

    @Test
    fun `deleteTimeBlock removes TimeBlock from persistence`() = runTest {
        // Given: A TimeBlock to delete
        val timeBlock = TimeBlock(
            id = 7L,
            workDayId = 3L,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(12, 0),
            isDuration = false
        )

        // When: Deleting time block
        repository.deleteTimeBlock(timeBlock)

        // Then: DAO delete should be called
        verify(timeBlockDao).delete(
            TimeBlockEntity(
                id = 7L,
                workDayId = 3L,
                startTime = "09:00",
                endTime = "12:00",
                isDuration = false
            )
        )
    }

    @Test
    fun `getTimeBlocksForDay returns mapped TimeBlocks for given workDayId`() = runTest {
        // Given: TimeBlocks for a specific work day
        val timeBlockEntities = listOf(
            TimeBlockEntity(
                id = 1L,
                workDayId = 5L,
                startTime = "08:00",
                endTime = "12:00",
                isDuration = false
            ),
            TimeBlockEntity(
                id = 2L,
                workDayId = 5L,
                startTime = "13:00",
                endTime = null,  // Running
                isDuration = false
            )
        )

        whenever(timeBlockDao.getTimeBlocksForDay(5L)).thenReturn(flowOf(timeBlockEntities))

        // When: Getting time blocks for day
        val result = repository.getTimeBlocksForDay(5L).first()

        // Then: Should return correctly mapped TimeBlocks
        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo(1L)
        assertThat(result[0].workDayId).isEqualTo(5L)
        assertThat(result[0].startTime).isEqualTo(LocalTime.of(8, 0))
        assertThat(result[0].endTime).isEqualTo(LocalTime.of(12, 0))
        assertThat(result[1].endTime).isNull() // Running block
    }

    @Test
    fun `TimeBlock has correct workDayId association`() = runTest {
        // Given: TimeBlock with specific workDayId
        val timeBlock = TimeBlock(
            id = 0L,
            workDayId = 42L,
            startTime = LocalTime.of(10, 0),
            endTime = LocalTime.of(11, 0),
            isDuration = false
        )

        whenever(timeBlockDao.insert(
            TimeBlockEntity(
                id = 0L,
                workDayId = 42L,
                startTime = "10:00",
                endTime = "11:00",
                isDuration = false
            )
        )).thenReturn(1L)

        // When: Saving time block
        repository.saveTimeBlock(timeBlock)

        // Then: Verify workDayId is correctly preserved
        verify(timeBlockDao).insert(
            TimeBlockEntity(
                id = 0L,
                workDayId = 42L,
                startTime = "10:00",
                endTime = "11:00",
                isDuration = false
            )
        )
    }

    // ========== getWorkDaysForMonth Tests ==========

    @Test
    fun `getWorkDaysForMonth filters by date range correctly`() = runTest {
        // Given: WorkDays in February 2025
        val yearMonth = YearMonth.of(2025, 2)
        val workDayEntities = listOf(
            WorkDayEntity(
                id = 1L,
                date = "2025-02-01",
                location = "OFFICE",
                dayType = "WORK"
            ),
            WorkDayEntity(
                id = 2L,
                date = "2025-02-15",
                location = "HOME_OFFICE",
                dayType = "WORK"
            )
        )

        whenever(workDayDao.getWorkDaysBetween("2025-02-01", "2025-02-28"))
            .thenReturn(flowOf(workDayEntities))
        whenever(timeBlockDao.getAllTimeBlocksFlow())
            .thenReturn(flowOf(emptyList()))

        // When: Getting work days for February 2025
        val result = repository.getWorkDaysForMonth(yearMonth).first()

        // Then: Should query correct date range
        verify(workDayDao).getWorkDaysBetween("2025-02-01", "2025-02-28")
        assertThat(result).hasSize(2)
    }

    @Test
    fun `getWorkDaysForMonth returns empty list when no work days exist`() = runTest {
        // Given: No work days in the month
        val yearMonth = YearMonth.of(2025, 3)
        whenever(workDayDao.getWorkDaysBetween("2025-03-01", "2025-03-31"))
            .thenReturn(flowOf(emptyList()))

        // When: Getting work days
        val result = repository.getWorkDaysForMonth(yearMonth).first()

        // Then: Should return empty list
        assertThat(result).isEmpty()
    }

    @Test
    fun `getWorkDaysForMonth associates TimeBlocks correctly with WorkDays`() = runTest {
        // Given: WorkDays with mixed TimeBlocks
        val yearMonth = YearMonth.of(2025, 2)
        val workDayEntities = listOf(
            WorkDayEntity(id = 1L, date = "2025-02-01", location = "OFFICE", dayType = "WORK"),
            WorkDayEntity(id = 2L, date = "2025-02-02", location = "OFFICE", dayType = "WORK")
        )
        val timeBlockEntities = listOf(
            TimeBlockEntity(id = 1L, workDayId = 1L, startTime = "09:00", endTime = "12:00", isDuration = false),
            TimeBlockEntity(id = 2L, workDayId = 1L, startTime = "13:00", endTime = "17:00", isDuration = false),
            TimeBlockEntity(id = 3L, workDayId = 2L, startTime = "08:00", endTime = "16:00", isDuration = false)
        )

        whenever(workDayDao.getWorkDaysBetween("2025-02-01", "2025-02-28"))
            .thenReturn(flowOf(workDayEntities))
        whenever(timeBlockDao.getAllTimeBlocksFlow())
            .thenReturn(flowOf(timeBlockEntities))

        // When: Getting work days
        val result = repository.getWorkDaysForMonth(yearMonth).first()

        // Then: TimeBlocks should be correctly associated
        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo(1L)
        assertThat(result[0].timeBlocks).hasSize(2) // WorkDay 1 has 2 blocks
        assertThat(result[1].id).isEqualTo(2L)
        assertThat(result[1].timeBlocks).hasSize(1) // WorkDay 2 has 1 block
    }

    // ========== confirmPlannedDays Tests ==========

    @Test
    fun `confirmPlannedDays calls DAO with correct date range`() = runTest {
        // Given: YearMonth to confirm
        val yearMonth = YearMonth.of(2025, 2)

        // When: Confirming planned days
        repository.confirmPlannedDays(yearMonth)

        // Then: DAO should be called with correct range
        verify(workDayDao).confirmPlannedDays("2025-02-01", "2025-02-28")
    }

    // ========== CalendarSync Block-Loading Tests ==========

    @Test
    fun `saveWorkDay passes loaded blocks to calendarSync when blocks exist`() = runTest {
        // Given
        val date = LocalDate.of(2025, 3, 1)
        val workDay = WorkDay(id = 0L, date = date, location = WorkLocation.OFFICE, dayType = DayType.WORK)
        val savedId = 10L
        val blockEntity = TimeBlockEntity(
            id = 1L,
            workDayId = savedId,
            startTime = "09:00",
            endTime = "17:00",
            isDuration = false,
            location = "HOME_OFFICE"
        )
        val settings = Settings(calendarSyncEnabled = true, calendarId = 42L)

        whenever(workDayDao.insert(any())).thenReturn(savedId)
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(settings))
        whenever(timeBlockDao.getTimeBlocksForDays(listOf(savedId))).thenReturn(listOf(blockEntity))

        // When
        repository.saveWorkDay(workDay)

        // Then: calendarSync receives WorkDay WITH loaded blocks
        val captor = argumentCaptor<WorkDay>()
        verify(calendarSyncService).syncWorkDay(captor.capture(), eq(settings))
        assertThat(captor.firstValue.timeBlocks).hasSize(1)
        assertThat(captor.firstValue.timeBlocks[0].location).isEqualTo(WorkLocation.HOME_OFFICE)
    }

    @Test
    fun `saveWorkDay passes empty blocks to calendarSync when no blocks exist`() = runTest {
        // Given
        val date = LocalDate.of(2025, 3, 1)
        val workDay = WorkDay(id = 0L, date = date, location = WorkLocation.OFFICE, dayType = DayType.WORK)
        val savedId = 10L
        val settings = Settings(calendarSyncEnabled = true, calendarId = 42L)

        whenever(workDayDao.insert(any())).thenReturn(savedId)
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(settings))
        whenever(timeBlockDao.getTimeBlocksForDays(listOf(savedId))).thenReturn(emptyList())

        // When
        repository.saveWorkDay(workDay)

        // Then: calendarSync receives WorkDay with empty blocks
        val captor = argumentCaptor<WorkDay>()
        verify(calendarSyncService).syncWorkDay(captor.capture(), eq(settings))
        assertThat(captor.firstValue.timeBlocks).isEmpty()
    }

    // ========== Vacation Group Sync Tests ==========

    @Test
    fun `saveWorkDay for VACATION calls syncVacationGroup with full consecutive run`() = runTest {
        // Given: 3 consecutive vacation days already in DB, adding the 3rd
        val settings = Settings(calendarSyncEnabled = true, calendarId = 42L, calendarSyncTypes = "VACATION")
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(settings))

        val newDay = WorkDay(id = 0L, date = LocalDate.of(2026, 4, 9), location = WorkLocation.OFFICE, dayType = DayType.VACATION)
        val savedId = 3L

        whenever(workDayDao.insert(any())).thenReturn(savedId)
        whenever(workDayDao.getWorkDaysByType("VACATION")).thenReturn(listOf(
            WorkDayEntity(1L, "2026-04-07", "OFFICE", "VACATION"),
            WorkDayEntity(2L, "2026-04-08", "OFFICE", "VACATION"),
            WorkDayEntity(savedId, "2026-04-09", "OFFICE", "VACATION"),
        ))

        // When
        repository.saveWorkDay(newDay)

        // Then: syncVacationGroup is called with all 3 consecutive days
        val captor = argumentCaptor<List<WorkDay>>()
        verify(calendarSyncService).syncVacationGroup(captor.capture(), eq(settings))
        assertThat(captor.firstValue).hasSize(3)
        assertThat(captor.firstValue.map { it.date.dayOfMonth }).containsExactly(7, 8, 9).inOrder()

        // And: syncWorkDay is NOT called
        verify(calendarSyncService, never()).syncWorkDay(any(), any())
    }

    @Test
    fun `saveWorkDay for SPECIAL_VACATION calls syncVacationGroup`() = runTest {
        val settings = Settings(calendarSyncEnabled = true, calendarId = 42L, calendarSyncTypes = "SPECIAL_VACATION")
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(settings))

        val newDay = WorkDay(id = 0L, date = LocalDate.of(2026, 4, 7), location = WorkLocation.OFFICE, dayType = DayType.SPECIAL_VACATION)
        whenever(workDayDao.insert(any())).thenReturn(1L)
        whenever(workDayDao.getWorkDaysByType("SPECIAL_VACATION")).thenReturn(listOf(
            WorkDayEntity(1L, "2026-04-07", "OFFICE", "SPECIAL_VACATION"),
        ))

        repository.saveWorkDay(newDay)

        verify(calendarSyncService).syncVacationGroup(any(), eq(settings))
        verify(calendarSyncService, never()).syncWorkDay(any(), any())
    }

    @Test
    fun `saveWorkDay for VACATION only includes days of same type in run (not SPECIAL_VACATION)`() = runTest {
        val settings = Settings(calendarSyncEnabled = true, calendarId = 42L, calendarSyncTypes = "VACATION,SPECIAL_VACATION")
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(settings))

        val newDay = WorkDay(id = 0L, date = LocalDate.of(2026, 4, 8), location = WorkLocation.OFFICE, dayType = DayType.VACATION)
        whenever(workDayDao.insert(any())).thenReturn(2L)
        // Only VACATION days queried (not SPECIAL_VACATION)
        whenever(workDayDao.getWorkDaysByType("VACATION")).thenReturn(listOf(
            WorkDayEntity(1L, "2026-04-07", "OFFICE", "VACATION"),
            WorkDayEntity(2L, "2026-04-08", "OFFICE", "VACATION"),
        ))

        repository.saveWorkDay(newDay)

        val captor = argumentCaptor<List<WorkDay>>()
        verify(calendarSyncService).syncVacationGroup(captor.capture(), eq(settings))
        assertThat(captor.firstValue.all { it.dayType == DayType.VACATION }).isTrue()
    }

    @Test
    fun `saveWorkDay for WORK still calls syncWorkDay not syncVacationGroup`() = runTest {
        val settings = Settings(calendarSyncEnabled = true, calendarId = 42L)
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(settings))

        val workDay = WorkDay(id = 0L, date = LocalDate.of(2026, 4, 7), location = WorkLocation.OFFICE, dayType = DayType.WORK)
        whenever(workDayDao.insert(any())).thenReturn(1L)
        whenever(timeBlockDao.getTimeBlocksForDays(any())).thenReturn(emptyList())

        repository.saveWorkDay(workDay)

        verify(calendarSyncService).syncWorkDay(any(), any())
        verify(calendarSyncService, never()).syncVacationGroup(any(), any())
    }

    @Test
    fun `deleteWorkDay for VACATION calls deleteGroupEventForWorkDay`() = runTest {
        val settings = Settings(calendarSyncEnabled = true, calendarId = 42L)
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(settings))

        val workDay = WorkDay(id = 5L, date = LocalDate.of(2026, 4, 9), location = WorkLocation.OFFICE, dayType = DayType.VACATION)
        whenever(calendarSyncService.deleteGroupEventForWorkDay(5L)).thenReturn(emptyList())

        repository.deleteWorkDay(workDay)

        verify(calendarSyncService).deleteGroupEventForWorkDay(5L)
        verify(workDayDao).delete(any())
        // No individual deleteCalendarEvent call
        verify(calendarSyncService, never()).deleteCalendarEvent(any(), any())
    }

    @Test
    fun `deleteWorkDay for VACATION re-syncs remaining group members`() = runTest {
        val settings = Settings(calendarSyncEnabled = true, calendarId = 42L, calendarSyncTypes = "VACATION")
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(settings))

        // Deleting middle day of a 3-day group → remaining: Apr 7 and Apr 9 (now separate groups)
        val workDay = WorkDay(id = 2L, date = LocalDate.of(2026, 4, 8), location = WorkLocation.OFFICE, dayType = DayType.VACATION)
        whenever(calendarSyncService.deleteGroupEventForWorkDay(2L)).thenReturn(listOf(1L, 3L))
        whenever(workDayDao.getWorkDayById(1L)).thenReturn(WorkDayEntity(1L, "2026-04-07", "OFFICE", "VACATION"))
        whenever(workDayDao.getWorkDayById(3L)).thenReturn(WorkDayEntity(3L, "2026-04-09", "OFFICE", "VACATION"))

        repository.deleteWorkDay(workDay)

        // Apr 7 and Apr 9 have a gap of 2 days → they ARE still bridged (gap ≤ 3)
        // So they form one group and syncVacationGroup is called once
        val captor = argumentCaptor<List<WorkDay>>()
        verify(calendarSyncService).syncVacationGroup(captor.capture(), eq(settings))
        assertThat(captor.firstValue).hasSize(2)
    }

    @Test
    fun `deleteWorkDay for VACATION with large gap re-syncs as two separate groups`() = runTest {
        val settings = Settings(calendarSyncEnabled = true, calendarId = 42L, calendarSyncTypes = "VACATION")
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(settings))

        // Deleting a day that bridged two distant periods: Apr 7 and Apr 14 remain
        val workDay = WorkDay(id = 2L, date = LocalDate.of(2026, 4, 10), location = WorkLocation.OFFICE, dayType = DayType.VACATION)
        whenever(calendarSyncService.deleteGroupEventForWorkDay(2L)).thenReturn(listOf(1L, 3L))
        // Apr 7 and Apr 14 have a gap of 7 days → two separate groups
        whenever(workDayDao.getWorkDayById(1L)).thenReturn(WorkDayEntity(1L, "2026-04-07", "OFFICE", "VACATION"))
        whenever(workDayDao.getWorkDayById(3L)).thenReturn(WorkDayEntity(3L, "2026-04-14", "OFFICE", "VACATION"))

        repository.deleteWorkDay(workDay)

        val captor = argumentCaptor<List<WorkDay>>()
        verify(calendarSyncService, org.mockito.kotlin.times(2)).syncVacationGroup(captor.capture(), eq(settings))
        assertThat(captor.allValues).hasSize(2)
        assertThat(captor.allValues[0]).hasSize(1)
        assertThat(captor.allValues[1]).hasSize(1)
    }

    @Test
    fun `deleteWorkDay for WORK still uses deleteCalendarEvent`() = runTest {
        val workDay = WorkDay(id = 3L, date = LocalDate.of(2026, 4, 7), location = WorkLocation.OFFICE, dayType = DayType.WORK)

        repository.deleteWorkDay(workDay)

        verify(calendarSyncService).deleteCalendarEvent(any(), any())
        verify(calendarSyncService, never()).deleteGroupEventForWorkDay(any())
    }
}
