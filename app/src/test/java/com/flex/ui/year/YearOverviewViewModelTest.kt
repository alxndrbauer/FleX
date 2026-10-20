package com.flex.ui.year

import com.flex.BaseUnitTest
import com.flex.MainDispatcherExtension
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.DayWorkTimeResult
import com.flex.domain.usecase.GetSettingsUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@ExtendWith(MainDispatcherExtension::class)
@OptIn(ExperimentalCoroutinesApi::class)
class YearOverviewViewModelTest : BaseUnitTest() {

    @Mock
    private lateinit var workDayRepository: WorkDayRepository

    @Mock
    private lateinit var getSettings: GetSettingsUseCase

    @Mock
    private lateinit var calculateDayWorkTime: CalculateDayWorkTimeUseCase

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    private val testYear = 2026
    private val testSettings = Settings(dailyWorkMinutes = 480)

    @BeforeEach
    override fun setUp() {
        super.setUp()
        whenever(getSettings()).thenReturn(flowOf(testSettings))
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(emptyList()))
        whenever(workDayRepository.getWorkDaysForYear(any())).thenReturn(flowOf(emptyList()))
        val dummyResult = DayWorkTimeResult(grossMinutes = 480L, netMinutes = 480L, breakMinutes = 30L, exceedsMaxHours = false)
        whenever(calculateDayWorkTime.invoke(any())).thenReturn(dummyResult)
        whenever(calculateDayWorkTime.invoke(any(), any())).thenReturn(dummyResult)
    }

    @Test
    fun `initial state loads 12 month summaries for current year`() = runTest {
        val viewModel = YearOverviewViewModel(
            workDayRepository = workDayRepository,
            getSettings = getSettings,
            calculateDayWorkTime = calculateDayWorkTime,
            settingsRepository = settingsRepository
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.monthSummaries).hasSize(12)
        assertThat(state.monthSummaries[0].yearMonth).isEqualTo(YearMonth.of(state.year, 1))
        assertThat(state.monthSummaries[11].yearMonth).isEqualTo(YearMonth.of(state.year, 12))
    }

    @Test
    fun `year navigation updates year state and reloads data`() = runTest {
        val initialYear = LocalDate.now().year
        val viewModel = YearOverviewViewModel(
            workDayRepository = workDayRepository,
            getSettings = getSettings,
            calculateDayWorkTime = calculateDayWorkTime,
            settingsRepository = settingsRepository
        )
        advanceUntilIdle()

        viewModel.nextYear()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.year).isEqualTo(initialYear + 1)
        assertThat(viewModel.uiState.value.monthSummaries[0].yearMonth.year).isEqualTo(initialYear + 1)

        viewModel.previousYear()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.year).isEqualTo(initialYear)

        viewModel.selectYear(2030)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.year).isEqualTo(2030)
        assertThat(viewModel.uiState.value.monthSummaries[0].yearMonth.year).isEqualTo(2030)
    }

    @Test
    fun `month summaries correctly categorize work days and absences`() = runTest {
        val days = listOf(
            WorkDay(
                id = 1,
                date = LocalDate.of(testYear, 5, 4),
                location = WorkLocation.OFFICE,
                dayType = DayType.WORK,
                timeBlocks = listOf(
                    TimeBlock(
                        id = 1,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(16, 30),
                        location = WorkLocation.OFFICE
                    )
                )
            ),
            WorkDay(
                id = 2,
                date = LocalDate.of(testYear, 5, 5),
                location = WorkLocation.HOME_OFFICE,
                dayType = DayType.WORK,
                timeBlocks = listOf(
                    TimeBlock(
                        id = 2,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(16, 30),
                        location = WorkLocation.HOME_OFFICE
                    )
                )
            ),
            WorkDay(
                id = 3,
                date = LocalDate.of(testYear, 5, 6),
                location = WorkLocation.HOME_OFFICE,
                dayType = DayType.VACATION
            )
        )

        whenever(workDayRepository.getWorkDaysForYear(testYear)).thenReturn(flowOf(days))

        val viewModel = YearOverviewViewModel(
            workDayRepository = workDayRepository,
            getSettings = getSettings,
            calculateDayWorkTime = calculateDayWorkTime,
            settingsRepository = settingsRepository
        )
        viewModel.selectYear(testYear)
        advanceUntilIdle()

        val maySummary = viewModel.uiState.value.monthSummaries.find { it.yearMonth.monthValue == 5 }
        assertThat(maySummary).isNotNull()
        assertThat(maySummary!!.workDays).isEqualTo(2)
        assertThat(maySummary.officeDays).isEqualTo(1)
        assertThat(maySummary.homeOfficeDays).isEqualTo(1)
        assertThat(maySummary.vacationDays).isEqualTo(1)
        assertThat(maySummary.totalWorkMinutes).isEqualTo(960L)
    }
}
