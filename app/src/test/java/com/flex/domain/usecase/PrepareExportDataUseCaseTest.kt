package com.flex.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class PrepareExportDataUseCaseTest {

    private val workDayRepository: WorkDayRepository = mock()
    private val settingsRepository: SettingsRepository = mock()
    private val calculateDayWorkTime = CalculateDayWorkTimeUseCase()

    private lateinit var useCase: PrepareExportDataUseCase

    @BeforeEach
    fun setUp() {
        useCase = PrepareExportDataUseCase(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            calculateDayWorkTime = calculateDayWorkTime
        )
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(Settings()))
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(emptyList()))
        whenever(settingsRepository.getWorkTimeRuleForDate(any(), any())).thenReturn(null)
    }

    @Test
    fun `split day with different locations sets hasMultipleLocations true, location null, and populates blocks`() = runTest {
        val date = LocalDate.of(2026, 7, 15)
        val timeBlocks = listOf(
            TimeBlock(
                id = 1,
                workDayId = 1,
                startTime = LocalTime.of(8, 30),
                endTime = LocalTime.of(12, 0),
                location = WorkLocation.HOME_OFFICE
            ),
            TimeBlock(
                id = 2,
                workDayId = 1,
                startTime = LocalTime.of(13, 0),
                endTime = LocalTime.of(17, 0),
                location = WorkLocation.OFFICE
            )
        )
        val workDay = WorkDay(
            id = 1,
            date = date,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = timeBlocks
        )

        whenever(workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 7)))
            .thenReturn(flowOf(listOf(workDay)))

        val exportData = useCase(YearMonth.of(2026, 7))
        val row = exportData.rows.first { it.date == date }

        assertThat(row.hasMultipleLocations).isTrue()
        assertThat(row.location).isNull()
        assertThat(row.blocks).hasSize(2)

        val block1 = row.blocks[0]
        assertThat(block1.startTime).isEqualTo(LocalTime.of(8, 30))
        assertThat(block1.endTime).isEqualTo(LocalTime.of(12, 0))
        assertThat(block1.location).isEqualTo(WorkLocation.HOME_OFFICE)
        assertThat(block1.durationMinutes).isEqualTo(210L)

        val block2 = row.blocks[1]
        assertThat(block2.startTime).isEqualTo(LocalTime.of(13, 0))
        assertThat(block2.endTime).isEqualTo(LocalTime.of(17, 0))
        assertThat(block2.location).isEqualTo(WorkLocation.OFFICE)
        assertThat(block2.durationMinutes).isEqualTo(240L)
    }

    @Test
    fun `day with multiple blocks at same location sets hasMultipleLocations false and empty blocks`() = runTest {
        val date = LocalDate.of(2026, 7, 1)
        val timeBlocks = listOf(
            TimeBlock(
                id = 1,
                workDayId = 1,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(12, 0),
                location = WorkLocation.OFFICE
            ),
            TimeBlock(
                id = 2,
                workDayId = 1,
                startTime = LocalTime.of(12, 30),
                endTime = LocalTime.of(16, 30),
                location = WorkLocation.OFFICE
            )
        )
        val workDay = WorkDay(
            id = 1,
            date = date,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = timeBlocks
        )

        whenever(workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 7)))
            .thenReturn(flowOf(listOf(workDay)))

        val exportData = useCase(YearMonth.of(2026, 7))
        val row = exportData.rows.first { it.date == date }

        assertThat(row.hasMultipleLocations).isFalse()
        assertThat(row.location).isEqualTo(WorkLocation.OFFICE)
        assertThat(row.blocks).isEmpty()
    }
}
