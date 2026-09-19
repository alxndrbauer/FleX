package com.flex.domain.usecase

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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalTime

class SwitchLocationUseCaseTest {

    private val workDayRepository: WorkDayRepository = mock()
    private lateinit var useCase: SwitchLocationUseCase

    private val today = LocalDate.now()

    @BeforeEach
    fun setUp() {
        useCase = SwitchLocationUseCase(workDayRepository)
    }

    @Test
    fun `returns false when no work day or running block exists`() = runTest {
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(null))

        val result = useCase()

        assertThat(result).isFalse()
        verify(workDayRepository, never()).saveTimeBlock(any())
    }

    @Test
    fun `switches from OFFICE to HOME_OFFICE when running block exists`() = runTest {
        val runningBlock = TimeBlock(
            id = 10L,
            workDayId = 1L,
            startTime = LocalTime.of(8, 0),
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

        assertThat(result).isTrue()
        val timeBlockCaptor = argumentCaptor<TimeBlock>()
        verify(workDayRepository).saveTimeBlock(timeBlockCaptor.capture())

        val updatedBlock = timeBlockCaptor.firstValue
        assertThat(updatedBlock.id).isEqualTo(10L)
        assertThat(updatedBlock.startTime).isEqualTo(LocalTime.of(8, 0))
        assertThat(updatedBlock.endTime).isNull()
        assertThat(updatedBlock.location).isEqualTo(WorkLocation.HOME_OFFICE)

        val workDayCaptor = argumentCaptor<WorkDay>()
        verify(workDayRepository).saveWorkDay(workDayCaptor.capture())
        assertThat(workDayCaptor.firstValue.location).isEqualTo(WorkLocation.HOME_OFFICE)
    }

    @Test
    fun `switches to explicit target location`() = runTest {
        val runningBlock = TimeBlock(
            id = 10L,
            workDayId = 1L,
            startTime = LocalTime.of(8, 0),
            endTime = null,
            location = WorkLocation.HOME_OFFICE
        )
        val workDay = WorkDay(
            id = 1L,
            date = today,
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(runningBlock)
        )
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        val result = useCase(WorkLocation.OFFICE)

        assertThat(result).isTrue()
        val timeBlockCaptor = argumentCaptor<TimeBlock>()
        verify(workDayRepository).saveTimeBlock(timeBlockCaptor.capture())
        assertThat(timeBlockCaptor.firstValue.id).isEqualTo(10L)
        assertThat(timeBlockCaptor.firstValue.endTime).isNull()
        assertThat(timeBlockCaptor.firstValue.location).isEqualTo(WorkLocation.OFFICE)
    }

    @Test
    fun `returns false when already at target location`() = runTest {
        val runningBlock = TimeBlock(
            id = 10L,
            workDayId = 1L,
            startTime = LocalTime.of(8, 0),
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

        val result = useCase(WorkLocation.OFFICE)

        assertThat(result).isFalse()
        verify(workDayRepository, never()).saveTimeBlock(any())
    }
}
