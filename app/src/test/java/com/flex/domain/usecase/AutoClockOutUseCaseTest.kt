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

class AutoClockOutUseCaseTest {

    private val workDayRepository: WorkDayRepository = mock()
    private val pausePreferences: PausePreferences = mock()
    private lateinit var useCase: AutoClockOutUseCase

    private val today = LocalDate.now()

    @BeforeEach
    fun setUp() {
        useCase = AutoClockOutUseCase(workDayRepository, pausePreferences)
    }

    @Test
    fun `clocks out running block and returns true`() = runTest {
        whenever(pausePreferences.isPaused).thenReturn(false)
        val runningBlock = TimeBlock(
            id = 1L,
            workDayId = 10L,
            startTime = LocalTime.of(8, 0),
            endTime = null,
            location = WorkLocation.OFFICE
        )
        val workDay = WorkDay(
            id = 10L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(runningBlock)
        )
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        val result = useCase()

        assertThat(result).isTrue()
        val captor = argumentCaptor<TimeBlock>()
        verify(workDayRepository).saveTimeBlock(captor.capture())
        assertThat(captor.firstValue.id).isEqualTo(1L)
        assertThat(captor.firstValue.endTime).isNotNull()
    }

    @Test
    fun `clears pause and returns true when paused without running block`() = runTest {
        whenever(pausePreferences.isPaused).thenReturn(true)
        val completedBlock = TimeBlock(
            id = 1L,
            workDayId = 10L,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(12, 0),
            location = WorkLocation.OFFICE
        )
        val workDay = WorkDay(
            id = 10L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(completedBlock)
        )
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        val result = useCase()

        assertThat(result).isTrue()
        verify(pausePreferences).clearPause()
        verify(workDayRepository, never()).saveTimeBlock(any())
    }

    @Test
    fun `returns false when no running block and not paused`() = runTest {
        whenever(pausePreferences.isPaused).thenReturn(false)
        val completedBlock = TimeBlock(
            id = 1L,
            workDayId = 10L,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(12, 0),
            location = WorkLocation.OFFICE
        )
        val workDay = WorkDay(
            id = 10L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(completedBlock)
        )
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(workDay))

        val result = useCase()

        assertThat(result).isFalse()
        verify(pausePreferences, never()).clearPause()
        verify(workDayRepository, never()).saveTimeBlock(any())
    }

    @Test
    fun `returns false when workDay is null and not paused`() = runTest {
        whenever(pausePreferences.isPaused).thenReturn(false)
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(null))

        val result = useCase()

        assertThat(result).isFalse()
        verify(pausePreferences, never()).clearPause()
        verify(workDayRepository, never()).saveTimeBlock(any())
    }
}
