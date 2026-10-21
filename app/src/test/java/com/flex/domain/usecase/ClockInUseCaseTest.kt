package com.flex.domain.usecase

import com.flex.data.local.PausePreferences
import com.flex.domain.model.DayType
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.WorkDayRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalTime

class ClockInUseCaseTest {

    private val workDayRepository: WorkDayRepository = mock()
    private val pausePreferences: PausePreferences = mock()
    private lateinit var useCase: ClockInUseCase

    private val today = LocalDate.now()

    @BeforeEach
    fun setUp() {
        useCase = ClockInUseCase(workDayRepository, pausePreferences)
    }

    @Test
    fun `returns false when already clocked in`() = runTest {
        val runningBlock = TimeBlock(
            id = 1L,
            workDayId = 1L,
            startTime = LocalTime.of(9, 0),
            endTime = null,
            location = WorkLocation.OFFICE
        )
        val workDay = WorkDay(
            id = 1L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(runningBlock)
        )
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        val result = useCase()

        assertThat(result).isFalse()
        verify(workDayRepository, never()).saveTimeBlock(any())
    }

    @Test
    fun `creates new work day and time block when no work day exists`() = runTest {
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(null))
        whenever(workDayRepository.saveWorkDay(any())).thenReturn(42L)

        val result = useCase(WorkLocation.HOME_OFFICE)

        assertThat(result).isTrue()
        val workDayCaptor = argumentCaptor<WorkDay>()
        verify(workDayRepository).saveWorkDay(workDayCaptor.capture())
        assertThat(workDayCaptor.firstValue.location).isEqualTo(WorkLocation.HOME_OFFICE)

        val timeBlockCaptor = argumentCaptor<TimeBlock>()
        verify(workDayRepository).saveTimeBlock(timeBlockCaptor.capture())
        assertThat(timeBlockCaptor.firstValue.workDayId).isEqualTo(42L)
        assertThat(timeBlockCaptor.firstValue.location).isEqualTo(WorkLocation.HOME_OFFICE)
        assertThat(timeBlockCaptor.firstValue.endTime).isNull()
    }

    @Test
    fun `adds new time block to existing work day`() = runTest {
        val completedBlock = TimeBlock(
            id = 1L,
            workDayId = 5L,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(12, 0),
            location = WorkLocation.OFFICE
        )
        val existingDay = WorkDay(
            id = 5L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(completedBlock)
        )
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(existingDay))

        val result = useCase(WorkLocation.OFFICE)

        assertThat(result).isTrue()
        val timeBlockCaptor = argumentCaptor<TimeBlock>()
        verify(workDayRepository).saveTimeBlock(timeBlockCaptor.capture())
        assertThat(timeBlockCaptor.firstValue.workDayId).isEqualTo(5L)
        assertThat(timeBlockCaptor.firstValue.location).isEqualTo(WorkLocation.OFFICE)
        assertThat(timeBlockCaptor.firstValue.endTime).isNull()
    }

    @Test
    fun `clears pause and inherits last block location when no override given`() = runTest {
        val completedBlock = TimeBlock(
            id = 1L,
            workDayId = 5L,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(12, 0),
            location = WorkLocation.HOME_OFFICE
        )
        val existingDay = WorkDay(
            id = 5L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(completedBlock)
        )
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(existingDay))

        val result = useCase()

        assertThat(result).isTrue()
        verify(pausePreferences).clearPause()
        val timeBlockCaptor = argumentCaptor<TimeBlock>()
        verify(workDayRepository).saveTimeBlock(timeBlockCaptor.capture())
        assertThat(timeBlockCaptor.firstValue.location).isEqualTo(WorkLocation.HOME_OFFICE)
    }

    @Test
    fun `clock in on planned vacation day retains dayType and un-plans day`() = runTest {
        val existingDay = WorkDay(
            id = 7L,
            date = today,
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.VACATION,
            isPlanned = true,
            timeBlocks = emptyList()
        )
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(existingDay))

        val result = useCase()

        assertThat(result).isTrue()
        val workDayCaptor = argumentCaptor<WorkDay>()
        verify(workDayRepository).saveWorkDay(workDayCaptor.capture())
        assertThat(workDayCaptor.firstValue.id).isEqualTo(7L)
        assertThat(workDayCaptor.firstValue.dayType).isEqualTo(DayType.VACATION)
        assertThat(workDayCaptor.firstValue.isPlanned).isFalse()
    }

    @Test
    fun `clock in deletes planned duration blocks before adding a running block`() = runTest {
        val plannedBlock = TimeBlock(
            id = 70L,
            workDayId = 9L,
            startTime = LocalTime.of(8, 30),
            endTime = LocalTime.of(17, 30),
            isDuration = true,
            location = WorkLocation.HOME_OFFICE
        )
        val plannedDay = WorkDay(
            id = 9L,
            date = today,
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.VACATION,
            isPlanned = true,
            timeBlocks = listOf(plannedBlock)
        )
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(plannedDay))

        val result = useCase()

        assertThat(result).isTrue()
        verify(workDayRepository).deleteTimeBlock(plannedBlock)

        val savedDay = argumentCaptor<WorkDay>()
        verify(workDayRepository).saveWorkDay(savedDay.capture())
        assertThat(savedDay.firstValue.dayType).isEqualTo(DayType.VACATION)
        assertThat(savedDay.firstValue.isPlanned).isFalse()

        val runningBlock = argumentCaptor<TimeBlock>()
        val repositoryOrder = inOrder(workDayRepository)
        repositoryOrder.verify(workDayRepository).deleteTimeBlock(plannedBlock)
        repositoryOrder.verify(workDayRepository).saveTimeBlock(runningBlock.capture())
        assertThat(runningBlock.firstValue.isDuration).isFalse()
        assertThat(runningBlock.firstValue.endTime).isNull()
        assertThat(runningBlock.firstValue.workDayId).isEqualTo(plannedDay.id)
        assertThat(runningBlock.firstValue.location).isEqualTo(WorkLocation.HOME_OFFICE)
    }

    @Test
    fun `clock in on business trip day enforces OFFICE location and retains BUSINESS_TRIP`() = runTest {
        val existingDay = WorkDay(
            id = 8L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.BUSINESS_TRIP,
            isPlanned = false,
            timeBlocks = emptyList()
        )
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(existingDay))

        val result = useCase(WorkLocation.HOME_OFFICE) // Attempting HOME_OFFICE should be overridden to OFFICE

        assertThat(result).isTrue()
        val timeBlockCaptor = argumentCaptor<TimeBlock>()
        verify(workDayRepository).saveTimeBlock(timeBlockCaptor.capture())
        assertThat(timeBlockCaptor.firstValue.location).isEqualTo(WorkLocation.OFFICE)
    }
}
