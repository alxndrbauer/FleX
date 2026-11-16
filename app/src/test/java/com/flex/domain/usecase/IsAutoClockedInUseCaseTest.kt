package com.flex.domain.usecase

import com.flex.data.local.GeofencePreferences
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.WorkDayRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalTime

class IsAutoClockedInUseCaseTest {

    private val workDayRepository: WorkDayRepository = mock()
    private val geofencePreferences: GeofencePreferences = mock()
    private lateinit var useCase: IsAutoClockedInUseCase

    @BeforeEach
    fun setUp() {
        useCase = IsAutoClockedInUseCase(workDayRepository, geofencePreferences)
    }

    @Test
    fun `returns true while the last auto block is running`() = runTest {
        whenever(geofencePreferences.lastAutoTimeBlockId).thenReturn(5L)
        whenever(workDayRepository.getTimeBlockById(5L)).thenReturn(
            TimeBlock(
                id = 5L,
                workDayId = 1L,
                startTime = LocalTime.of(8, 0),
                endTime = null,
                location = WorkLocation.OFFICE
            )
        )

        assertThat(useCase()).isTrue()
    }

    @Test
    fun `returns false when the last auto block was stopped`() = runTest {
        whenever(geofencePreferences.lastAutoTimeBlockId).thenReturn(5L)
        whenever(workDayRepository.getTimeBlockById(5L)).thenReturn(
            TimeBlock(
                id = 5L,
                workDayId = 1L,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(12, 0),
                location = WorkLocation.OFFICE
            )
        )

        assertThat(useCase()).isFalse()
    }

    @Test
    fun `returns false when no auto block was recorded`() = runTest {
        whenever(geofencePreferences.lastAutoTimeBlockId).thenReturn(-1L)

        assertThat(useCase()).isFalse()
    }

    @Test
    fun `returns false when the recorded block no longer exists`() = runTest {
        whenever(geofencePreferences.lastAutoTimeBlockId).thenReturn(5L)
        whenever(workDayRepository.getTimeBlockById(5L)).thenReturn(null)

        assertThat(useCase()).isFalse()
    }
}
