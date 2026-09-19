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

class PauseWorkUseCaseTest {

    private val workDayRepository: WorkDayRepository = mock()
    private val pausePreferences: PausePreferences = mock()
    private lateinit var useCase: PauseWorkUseCase

    private val today = LocalDate.now()

    @BeforeEach
    fun setUp() {
        useCase = PauseWorkUseCase(workDayRepository, pausePreferences)
    }

    @Test
    fun `returns false when no running block exists`() = runTest {
        whenever(workDayRepository.getWorkDay(today)).thenReturn(flowOf(null))

        val result = useCase()

        assertThat(result).isFalse()
        verify(workDayRepository, never()).saveTimeBlock(any())
        verify(pausePreferences, never()).pauseStartTime = any()
    }

    @Test
    fun `ends running block and saves pause timestamp when running block exists`() = runTest {
        val runningBlock = TimeBlock(
            id = 5L,
            workDayId = 1L,
            startTime = LocalTime.of(8, 30),
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
        val captor = argumentCaptor<TimeBlock>()
        verify(workDayRepository).saveTimeBlock(captor.capture())
        assertThat(captor.firstValue.id).isEqualTo(5L)
        assertThat(captor.firstValue.endTime).isNotNull()

        verify(pausePreferences).pauseStartTime = any()

        val inOrder = org.mockito.kotlin.inOrder(pausePreferences, workDayRepository)
        inOrder.verify(pausePreferences).pauseStartTime = any()
        inOrder.verify(workDayRepository).saveTimeBlock(any())
    }
}
