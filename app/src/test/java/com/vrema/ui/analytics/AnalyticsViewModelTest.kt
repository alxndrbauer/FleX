package com.vrema.ui.analytics

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.vrema.BaseUnitTest
import com.vrema.MainDispatcherRule
import com.vrema.domain.model.AnalyticsData
import com.vrema.domain.model.DayType
import com.vrema.domain.model.LocationDistribution
import com.vrema.domain.model.Settings
import com.vrema.domain.model.TimeRange
import com.vrema.domain.model.TimeSeriesPoint
import com.vrema.domain.model.WeeklyWorkHours
import com.vrema.domain.model.WorkDay
import com.vrema.domain.model.WorkLocation
import com.vrema.domain.repository.WorkDayRepository
import com.vrema.domain.usecase.CalculateAnalyticsUseCase
import com.vrema.domain.usecase.GetSettingsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Rule
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.YearMonth

/**
 * Unit tests for AnalyticsViewModel.
 * Tests time range switching, flow combining, loading states, and data fetching.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest : BaseUnitTest() {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Mock
    private lateinit var workDayRepository: WorkDayRepository

    @Mock
    private lateinit var getSettingsUseCase: GetSettingsUseCase

    @Mock
    private lateinit var calculateAnalyticsUseCase: CalculateAnalyticsUseCase

    private lateinit var viewModel: AnalyticsViewModel

    private val defaultSettings = Settings()
    private val defaultAnalyticsData = AnalyticsData(
        flextimeSeries = emptyList(),
        overtimeSeries = emptyList(),
        weeklyHours = emptyList(),
        monthlyHours = emptyList(),
        locationDistribution = LocationDistribution(0, 0)
    )

    override fun setUp() {
        super.setUp()
        // Default mock behavior
        whenever(getSettingsUseCase()).thenReturn(flowOf(defaultSettings))
        whenever(workDayRepository.getWorkDaysForMonth(any())).thenReturn(flowOf(emptyList()))
        whenever(workDayRepository.getWorkDaysForYear(any())).thenReturn(flowOf(emptyList()))
        whenever(workDayRepository.getWorkDaysInRange(any(), any())).thenReturn(flowOf(emptyList()))
        whenever(calculateAnalyticsUseCase(any(), any(), any())).thenReturn(defaultAnalyticsData)
    }

    // ========== Initial State Tests ==========

    @Test
    fun `initial state uses current month as timeRange`() = runTest {
        // When: ViewModel is created
        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        advanceUntilIdle()

        // Then: State should have current YearMonth
        assertThat(viewModel.uiState.value.timeRange)
            .isEqualTo(TimeRange.Month(YearMonth.now()))
    }

    @Test
    fun `initial state loads analyticsData`() = runTest {
        // When: ViewModel is created
        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        advanceUntilIdle()

        // Then: State should have analytics data
        assertThat(viewModel.uiState.value.analyticsData).isNotNull()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `initial state loads settings`() = runTest {
        // Given: GetSettings returns specific settings
        val expectedSettings = Settings(
            dailyWorkMinutes = 480,
            initialFlextimeMinutes = 120
        )
        whenever(getSettingsUseCase()).thenReturn(flowOf(expectedSettings))

        // When: ViewModel is created
        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        advanceUntilIdle()

        // Then: State should contain settings
        assertThat(viewModel.uiState.value.settings).isEqualTo(expectedSettings)
    }

    // ========== Time Range Switching Tests ==========

    @Test
    fun `setTimeRange to different month triggers repository fetch`() = runTest {
        // Given: ViewModel initialized with current month
        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        advanceUntilIdle()

        val febMonth = YearMonth.of(2025, 2)
        val marchMonth = YearMonth.of(2025, 3)

        // When: Setting time range to March
        viewModel.setTimeRange(TimeRange.Month(marchMonth))
        advanceUntilIdle()

        // Then: Repository should be called for March
        verify(workDayRepository).getWorkDaysForMonth(eq(marchMonth))
        assertThat(viewModel.uiState.value.timeRange).isEqualTo(TimeRange.Month(marchMonth))
    }

    @Test
    fun `setTimeRange from Month to Year changes repository call`() = runTest {
        // Given: ViewModel initialized with Month
        val initialMonth = YearMonth.of(2025, 2)
        whenever(workDayRepository.getWorkDaysForMonth(any())).thenReturn(flowOf(emptyList()))

        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        advanceUntilIdle()

        // When: Setting time range to Year
        val year2025 = 2025
        viewModel.setTimeRange(TimeRange.Year(year2025))
        advanceUntilIdle()

        // Then: Repository should be called for year instead of month
        verify(workDayRepository).getWorkDaysForYear(eq(year2025))
        assertThat(viewModel.uiState.value.timeRange).isEqualTo(TimeRange.Year(year2025))
    }

    @Test
    fun `setTimeRange to Custom range calls getWorkDaysInRange`() = runTest {
        // Given: ViewModel initialized
        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        advanceUntilIdle()

        // When: Setting custom time range
        val startDate = LocalDate.of(2025, 1, 1)
        val endDate = LocalDate.of(2025, 3, 31)
        viewModel.setTimeRange(TimeRange.Custom(startDate, endDate))
        advanceUntilIdle()

        // Then: Repository should be called with date range
        verify(workDayRepository).getWorkDaysInRange(eq(startDate), eq(endDate))
        assertThat(viewModel.uiState.value.timeRange)
            .isEqualTo(TimeRange.Custom(startDate, endDate))
    }

    // ========== Flow Combining Tests ==========

    @Test
    fun `combine flow emits both timeRange and settings in state`() = runTest {
        // Given: Settings and work days
        val expectedSettings = Settings(dailyWorkMinutes = 480)
        whenever(getSettingsUseCase()).thenReturn(flowOf(expectedSettings))

        val febMonth = YearMonth.of(2025, 2)
        val workDays = listOf(
            WorkDay(
                id = 1,
                date = LocalDate.of(2025, 2, 10),
                location = WorkLocation.OFFICE,
                dayType = DayType.WORK
            )
        )
        whenever(workDayRepository.getWorkDaysForMonth(febMonth)).thenReturn(flowOf(workDays))

        // When: ViewModel is created with specific month
        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        // Manually set time range to Feb
        viewModel.setTimeRange(TimeRange.Month(febMonth))
        advanceUntilIdle()

        // Then: State should contain both timeRange and settings
        assertThat(viewModel.uiState.value.timeRange).isEqualTo(TimeRange.Month(febMonth))
        assertThat(viewModel.uiState.value.settings).isEqualTo(expectedSettings)
        assertThat(viewModel.uiState.value.analyticsData).isNotNull()
    }

    @Test
    fun `combine flow triggers analytics calculation with correct parameters`() = runTest {
        // Given: Work days and settings
        val febMonth = YearMonth.of(2025, 2)
        val workDays = listOf(
            WorkDay(
                id = 1,
                date = LocalDate.of(2025, 2, 10),
                location = WorkLocation.OFFICE,
                dayType = DayType.WORK,
                isPlanned = false
            ),
            WorkDay(
                id = 2,
                date = LocalDate.of(2025, 2, 11),
                location = WorkLocation.HOME_OFFICE,
                dayType = DayType.WORK,
                isPlanned = true // This should be filtered out
            )
        )
        whenever(workDayRepository.getWorkDaysForMonth(febMonth)).thenReturn(flowOf(workDays))

        val expectedAnalytics = AnalyticsData(
            flextimeSeries = listOf(TimeSeriesPoint(febMonth.atDay(1), 120)),
            overtimeSeries = emptyList(),
            weeklyHours = emptyList(),
            monthlyHours = emptyList(),
            locationDistribution = LocationDistribution(480, 240)
        )
        whenever(calculateAnalyticsUseCase(any(), any(), any())).thenReturn(expectedAnalytics)

        // When: ViewModel is created and time range set
        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        viewModel.setTimeRange(TimeRange.Month(febMonth))
        advanceUntilIdle()

        // Then: Calculate analytics should be called with only non-planned work days
        verify(calculateAnalyticsUseCase).invoke(
            workDays = eq(listOf(workDays[0])), // Only non-planned
            settings = eq(defaultSettings),
            timeRange = eq(TimeRange.Month(febMonth))
        )
        assertThat(viewModel.uiState.value.analyticsData).isEqualTo(expectedAnalytics)
    }

    // ========== Different Time Ranges Return Different Data Tests ==========

    @Test
    fun `different time ranges trigger different repository calls`() = runTest {
        // Given: ViewModel initialized
        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        advanceUntilIdle()

        val febMonth = YearMonth.of(2025, 2)
        val year2025 = 2025

        // When: Setting to Month range
        viewModel.setTimeRange(TimeRange.Month(febMonth))
        advanceUntilIdle()

        // Then: Month repository method called
        verify(workDayRepository).getWorkDaysForMonth(eq(febMonth))

        // When: Setting to Year range
        viewModel.setTimeRange(TimeRange.Year(year2025))
        advanceUntilIdle()

        // Then: Year repository method called
        verify(workDayRepository).getWorkDaysForYear(eq(year2025))
    }

    @Test
    fun `month range and year range produce different analytics data`() = runTest {
        // Given: Different data for month vs year
        val febMonth = YearMonth.of(2025, 2)
        val febWorkDays = listOf(
            WorkDay(id = 1, date = LocalDate.of(2025, 2, 10), location = WorkLocation.OFFICE)
        )
        val yearWorkDays = listOf(
            WorkDay(id = 1, date = LocalDate.of(2025, 2, 10), location = WorkLocation.OFFICE),
            WorkDay(id = 2, date = LocalDate.of(2025, 3, 15), location = WorkLocation.HOME_OFFICE)
        )

        whenever(workDayRepository.getWorkDaysForMonth(febMonth)).thenReturn(flowOf(febWorkDays))
        whenever(workDayRepository.getWorkDaysForYear(2025)).thenReturn(flowOf(yearWorkDays))

        val monthAnalytics = AnalyticsData(
            flextimeSeries = listOf(TimeSeriesPoint(febMonth.atDay(1), 60)),
            overtimeSeries = emptyList(),
            weeklyHours = emptyList(),
            monthlyHours = emptyList(),
            locationDistribution = LocationDistribution(480, 0)
        )
        val yearAnalytics = AnalyticsData(
            flextimeSeries = listOf(TimeSeriesPoint(febMonth.atDay(1), 120)),
            overtimeSeries = emptyList(),
            weeklyHours = emptyList(),
            monthlyHours = emptyList(),
            locationDistribution = LocationDistribution(480, 240)
        )

        whenever(calculateAnalyticsUseCase(eq(febWorkDays), any(), any())).thenReturn(monthAnalytics)
        whenever(calculateAnalyticsUseCase(eq(yearWorkDays), any(), any())).thenReturn(yearAnalytics)

        // When: ViewModel initialized and set to month
        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        viewModel.setTimeRange(TimeRange.Month(febMonth))
        advanceUntilIdle()

        val analyticsData1 = viewModel.uiState.value.analyticsData

        // When: Switch to year
        viewModel.setTimeRange(TimeRange.Year(2025))
        advanceUntilIdle()

        val analyticsData2 = viewModel.uiState.value.analyticsData

        // Then: Analytics data should be different
        assertThat(analyticsData1).isEqualTo(monthAnalytics)
        assertThat(analyticsData2).isEqualTo(yearAnalytics)
        assertThat(analyticsData1).isNotEqualTo(analyticsData2)
    }

    // ========== Loading State Tests ==========

    @Test
    fun `loading state updates during data fetch`() = runTest {
        // Given: Repository with delayed response
        val febMonth = YearMonth.of(2025, 2)
        whenever(workDayRepository.getWorkDaysForMonth(febMonth)).thenReturn(
            flow {
                delay(100)
                emit(emptyList())
            }
        )

        // When: ViewModel is created
        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        viewModel.setTimeRange(TimeRange.Month(febMonth))

        // Note: Due to the combine/flatMapLatest flow structure, isLoading is set to true
        // when the time range changes. After data loads, it becomes false.
        advanceUntilIdle()

        // Then: Loading should eventually become false
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    // ========== Empty Data State Tests ==========

    @Test
    fun `empty work days list produces empty analytics data`() = runTest {
        // Given: Repository returns empty list
        val febMonth = YearMonth.of(2025, 2)
        whenever(workDayRepository.getWorkDaysForMonth(febMonth)).thenReturn(flowOf(emptyList()))

        val emptyAnalytics = AnalyticsData(
            flextimeSeries = emptyList(),
            overtimeSeries = emptyList(),
            weeklyHours = emptyList(),
            monthlyHours = emptyList(),
            locationDistribution = LocationDistribution(0, 0)
        )
        whenever(calculateAnalyticsUseCase(eq(emptyList()), any(), any())).thenReturn(emptyAnalytics)

        // When: ViewModel is created
        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        viewModel.setTimeRange(TimeRange.Month(febMonth))
        advanceUntilIdle()

        // Then: Analytics data should be empty
        assertThat(viewModel.uiState.value.analyticsData).isNotNull()
        assertThat(viewModel.uiState.value.analyticsData?.isEmpty).isTrue()
    }

    @Test
    fun `empty data state has non-null analytics data`() = runTest {
        // Given: Empty work days
        whenever(workDayRepository.getWorkDaysForMonth(any())).thenReturn(flowOf(emptyList()))

        // When: ViewModel is created
        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        advanceUntilIdle()

        // Then: analyticsData should not be null even with no data
        assertThat(viewModel.uiState.value.analyticsData).isNotNull()
    }

    // ========== Toggle Month Tests ==========

    @Test
    fun `toggleTimeRange with increment advances month by one`() = runTest {
        // Given: ViewModel initialized with February
        val febMonth = YearMonth.of(2025, 2)
        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        viewModel.setTimeRange(TimeRange.Month(febMonth))
        advanceUntilIdle()

        // When: Toggle with increment=true
        viewModel.toggleTimeRange(increment = true)
        advanceUntilIdle()

        // Then: Time range should be March
        assertThat(viewModel.uiState.value.timeRange).isEqualTo(TimeRange.Month(YearMonth.of(2025, 3)))
    }

    @Test
    fun `toggleTimeRange with decrement goes back one month`() = runTest {
        // Given: ViewModel initialized with February
        val febMonth = YearMonth.of(2025, 2)
        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        viewModel.setTimeRange(TimeRange.Month(febMonth))
        advanceUntilIdle()

        // When: Toggle with increment=false
        viewModel.toggleTimeRange(increment = false)
        advanceUntilIdle()

        // Then: Time range should be January
        assertThat(viewModel.uiState.value.timeRange).isEqualTo(TimeRange.Month(YearMonth.of(2025, 1)))
    }

    @Test
    fun `toggleTimeRange multiple increments advances correctly`() = runTest {
        // Given: ViewModel initialized with February
        val febMonth = YearMonth.of(2025, 2)
        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        viewModel.setTimeRange(TimeRange.Month(febMonth))
        advanceUntilIdle()

        // When: Toggle increment twice
        viewModel.toggleTimeRange(increment = true)
        advanceUntilIdle()
        viewModel.toggleTimeRange(increment = true)
        advanceUntilIdle()

        // Then: Time range should be April
        assertThat(viewModel.uiState.value.timeRange).isEqualTo(TimeRange.Month(YearMonth.of(2025, 4)))
    }

    @Test
    fun `toggleTimeRange increment then decrement returns to original month`() = runTest {
        // Given: ViewModel initialized with February
        val febMonth = YearMonth.of(2025, 2)
        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        viewModel.setTimeRange(TimeRange.Month(febMonth))
        advanceUntilIdle()

        // When: Toggle increment then decrement
        viewModel.toggleTimeRange(increment = true)
        advanceUntilIdle()
        viewModel.toggleTimeRange(increment = false)
        advanceUntilIdle()

        // Then: Time range should be back to February
        assertThat(viewModel.uiState.value.timeRange).isEqualTo(TimeRange.Month(febMonth))
    }

    // ========== Toggle Year Tests ==========

    @Test
    fun `toggleTimeRange on Year increments year by one`() = runTest {
        // Given: ViewModel initialized with Year 2025
        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        viewModel.setTimeRange(TimeRange.Year(2025))
        advanceUntilIdle()

        // When: Toggle with increment=true
        viewModel.toggleTimeRange(increment = true)
        advanceUntilIdle()

        // Then: Year should be 2026
        assertThat(viewModel.uiState.value.timeRange).isEqualTo(TimeRange.Year(2026))
    }

    @Test
    fun `toggleTimeRange on Year decrements year by one`() = runTest {
        // Given: ViewModel initialized with Year 2025
        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        viewModel.setTimeRange(TimeRange.Year(2025))
        advanceUntilIdle()

        // When: Toggle with increment=false
        viewModel.toggleTimeRange(increment = false)
        advanceUntilIdle()

        // Then: Year should be 2024
        assertThat(viewModel.uiState.value.timeRange).isEqualTo(TimeRange.Year(2024))
    }

    @Test
    fun `toggleTimeRange on Year increment then decrement returns to original`() = runTest {
        // Given: ViewModel initialized with Year 2025
        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        viewModel.setTimeRange(TimeRange.Year(2025))
        advanceUntilIdle()

        // When: Toggle increment then decrement
        viewModel.toggleTimeRange(increment = true)
        advanceUntilIdle()
        viewModel.toggleTimeRange(increment = false)
        advanceUntilIdle()

        // Then: Year should be back to 2025
        assertThat(viewModel.uiState.value.timeRange).isEqualTo(TimeRange.Year(2025))
    }

    // ========== Toggle Custom Range (No-op) Tests ==========

    @Test
    fun `toggleTimeRange on Custom range is no-op`() = runTest {
        // Given: ViewModel initialized with Custom range
        val startDate = LocalDate.of(2025, 1, 1)
        val endDate = LocalDate.of(2025, 3, 31)
        val customRange = TimeRange.Custom(startDate, endDate)

        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        viewModel.setTimeRange(customRange)
        advanceUntilIdle()

        // When: Toggle with increment
        viewModel.toggleTimeRange(increment = true)
        advanceUntilIdle()

        // Then: Custom range should remain unchanged
        assertThat(viewModel.uiState.value.timeRange).isEqualTo(customRange)

        // When: Toggle with decrement
        viewModel.toggleTimeRange(increment = false)
        advanceUntilIdle()

        // Then: Custom range should still remain unchanged
        assertThat(viewModel.uiState.value.timeRange).isEqualTo(customRange)
    }

    // ========== Edge Cases ==========

    @Test
    fun `planned work days are filtered out before analytics calculation`() = runTest {
        // Given: Mix of planned and actual work days
        val febMonth = YearMonth.of(2025, 2)
        val actualWorkDay = WorkDay(
            id = 1,
            date = LocalDate.of(2025, 2, 10),
            location = WorkLocation.OFFICE,
            isPlanned = false
        )
        val plannedWorkDay = WorkDay(
            id = 2,
            date = LocalDate.of(2025, 2, 11),
            location = WorkLocation.HOME_OFFICE,
            isPlanned = true
        )
        whenever(workDayRepository.getWorkDaysForMonth(febMonth))
            .thenReturn(flowOf(listOf(actualWorkDay, plannedWorkDay)))

        // When: ViewModel loads data
        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        viewModel.setTimeRange(TimeRange.Month(febMonth))
        advanceUntilIdle()

        // Then: Only actual work day should be passed to analytics calculation
        verify(calculateAnalyticsUseCase).invoke(
            workDays = eq(listOf(actualWorkDay)),
            settings = any(),
            timeRange = any()
        )
    }

    @Test
    fun `year month wraps correctly across year boundary`() = runTest {
        // Given: ViewModel initialized with December 2025
        val decMonth = YearMonth.of(2025, 12)
        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        viewModel.setTimeRange(TimeRange.Month(decMonth))
        advanceUntilIdle()

        // When: Toggle increment
        viewModel.toggleTimeRange(increment = true)
        advanceUntilIdle()

        // Then: Should wrap to January 2026
        assertThat(viewModel.uiState.value.timeRange).isEqualTo(TimeRange.Month(YearMonth.of(2026, 1)))
    }

    @Test
    fun `year month wraps backwards correctly across year boundary`() = runTest {
        // Given: ViewModel initialized with January 2025
        val janMonth = YearMonth.of(2025, 1)
        viewModel = AnalyticsViewModel(
            workDayRepository,
            getSettingsUseCase,
            calculateAnalyticsUseCase
        )
        viewModel.setTimeRange(TimeRange.Month(janMonth))
        advanceUntilIdle()

        // When: Toggle decrement
        viewModel.toggleTimeRange(increment = false)
        advanceUntilIdle()

        // Then: Should wrap to December 2024
        assertThat(viewModel.uiState.value.timeRange).isEqualTo(TimeRange.Month(YearMonth.of(2024, 12)))
    }
}
