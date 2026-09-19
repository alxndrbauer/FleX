package com.flex.domain.usecase

import com.flex.domain.events.DataChangeEvent
import com.flex.domain.events.DataChangeEventBus
import com.flex.domain.repository.WorkDayRepository
import com.flex.wearable.WearSyncHelper
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class AutoBookPlannedDaysUseCaseTest {

    private val workDayRepository: WorkDayRepository = mock()
    private val wearSyncHelper: WearSyncHelper = mock()
    private val dataChangeEventBus: DataChangeEventBus = mock()
    private lateinit var useCase: AutoBookPlannedDaysUseCase

    @BeforeEach
    fun setUp() {
        useCase = AutoBookPlannedDaysUseCase(workDayRepository, wearSyncHelper, dataChangeEventBus)
    }

    @Test
    fun `when planned days are confirmed, emits event, pushes to wear, and returns count`() = runTest {
        val today = LocalDate.now()
        whenever(workDayRepository.confirmPlannedDaysUpTo(today)).thenReturn(2)

        val result = useCase()

        assertThat(result).isEqualTo(2)
        verify(workDayRepository).confirmPlannedDaysUpTo(today)
        verify(dataChangeEventBus).emit(DataChangeEvent.WorkDayChanged)
        verify(wearSyncHelper).push()
    }

    @Test
    fun `when no planned days are confirmed, does not emit or push, returns 0`() = runTest {
        val today = LocalDate.now()
        whenever(workDayRepository.confirmPlannedDaysUpTo(today)).thenReturn(0)

        val result = useCase()

        assertThat(result).isEqualTo(0)
        verify(workDayRepository).confirmPlannedDaysUpTo(today)
        verify(dataChangeEventBus, never()).emit(any())
        verify(wearSyncHelper, never()).push()
    }

    @Test
    fun `custom upToDate is passed to repository`() = runTest {
        val customDate = LocalDate.of(2026, 9, 21)
        whenever(workDayRepository.confirmPlannedDaysUpTo(customDate)).thenReturn(1)

        val result = useCase(customDate)

        assertThat(result).isEqualTo(1)
        verify(workDayRepository).confirmPlannedDaysUpTo(customDate)
        verify(dataChangeEventBus).emit(DataChangeEvent.WorkDayChanged)
        verify(wearSyncHelper).push()
    }
}
