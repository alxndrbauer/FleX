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
}
