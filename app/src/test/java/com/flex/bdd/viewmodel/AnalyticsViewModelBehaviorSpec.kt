package com.flex.bdd.viewmodel

import com.flex.domain.model.AnalyticsData
import com.flex.domain.model.LocationDistribution
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeRange
import com.flex.domain.model.TimeSeriesPoint
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateAnalyticsUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.ui.analytics.AnalyticsViewModel
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelBehaviorSpec : BehaviorSpec({

    val testDispatcher = UnconfinedTestDispatcher()

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    val defaultAnalytics = AnalyticsData(
        flextimeSeries = listOf(TimeSeriesPoint(LocalDate.of(2025, 5, 1), 60)),
        overtimeSeries = emptyList(),
        weeklyHours = emptyList(),
        monthlyHours = emptyList(),
        locationDistribution = LocationDistribution(officeMinutes = 1200, homeOfficeMinutes = 800)
    )

    fun setupAnalyticsViewModel(
        workDayRepository: WorkDayRepository = mockk(relaxed = true),
        analyticsData: AnalyticsData = defaultAnalytics
    ): Pair<WorkDayRepository, AnalyticsViewModel> {
        val getSettingsUseCase = mockk<GetSettingsUseCase>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val calculateAnalyticsUseCase = mockk<CalculateAnalyticsUseCase>(relaxed = true)

        every { getSettingsUseCase() } returns flowOf(Settings())
        every { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { workDayRepository.getWorkDaysForMonth(any()) } returns flowOf(emptyList())
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())
        every { workDayRepository.getWorkDaysInRange(any(), any()) } returns flowOf(emptyList())
        every { calculateAnalyticsUseCase(any(), any(), any(), any()) } returns analyticsData

        val vm = AnalyticsViewModel(
            workDayRepository = workDayRepository,
            getSettingsUseCase = getSettingsUseCase,
            settingsRepository = settingsRepository,
            calculateAnalyticsUseCase = calculateAnalyticsUseCase
        )
        return Pair(workDayRepository, vm)
    }

    Given("ein AnalyticsViewModel beim Start") {
        val (_, viewModel) = setupAnalyticsViewModel()

        When("der initiale Zustand geladen wird") {
            Then("steht timeRange auf dem aktuellen Monat") {
                viewModel.uiState.value.timeRange shouldBe TimeRange.Month(YearMonth.now())
            }

            Then("ist analyticsData mit den Diagrammdaten befüllt") {
                val data = viewModel.uiState.value.analyticsData
                data.shouldNotBeNull()
                data.flextimeSeries shouldHaveSize 1
                data.locationDistribution.officeMinutes shouldBe 1200
            }

            Then("ist isLoading false") {
                viewModel.uiState.value.isLoading.shouldBeFalse()
            }
        }
    }

    Given("das Umschalten auf einen Jahres-Zeitbereich") {
        val (workDayRepository, viewModel) = setupAnalyticsViewModel()

        When("setTimeRange(TimeRange.Year(2025)) aufgerufen wird") {
            viewModel.setTimeRange(TimeRange.Year(2025))

            Then("wechselt timeRange auf das Jahr 2025") {
                viewModel.uiState.value.timeRange shouldBe TimeRange.Year(2025)
            }

            Then("wird getWorkDaysForYear aufgerufen") {
                coVerify { workDayRepository.getWorkDaysForYear(2025) }
            }
        }
    }

    Given("das Umschalten auf einen benutzerdefinierten Datumsbereich") {
        val (workDayRepository, viewModel) = setupAnalyticsViewModel()
        val startDate = LocalDate.of(2025, 1, 1)
        val endDate = LocalDate.of(2025, 3, 31)

        When("setTimeRange mit TimeRange.Custom aufgerufen wird") {
            viewModel.setTimeRange(TimeRange.Custom(startDate, endDate))

            Then("wechselt timeRange auf den angegebenen Zeitraum") {
                viewModel.uiState.value.timeRange shouldBe TimeRange.Custom(startDate, endDate)
            }

            Then("wird getWorkDaysInRange aufgerufen") {
                coVerify { workDayRepository.getWorkDaysInRange(startDate, endDate) }
            }
        }
    }

    Given("das Blättern im Monatsmodus") {
        val (_, viewModel) = setupAnalyticsViewModel()
        val may2025 = YearMonth.of(2025, 5)
        viewModel.setTimeRange(TimeRange.Month(may2025))

        When("toggleTimeRange(increment = true) aufgerufen wird") {
            viewModel.toggleTimeRange(increment = true)

            Then("wechselt der Monat auf Juni 2025") {
                viewModel.uiState.value.timeRange shouldBe TimeRange.Month(YearMonth.of(2025, 6))
            }
        }

        When("toggleTimeRange(increment = false) aufgerufen wird") {
            viewModel.toggleTimeRange(increment = false)

            Then("wechselt der Monat zurück auf Mai 2025") {
                viewModel.uiState.value.timeRange shouldBe TimeRange.Month(YearMonth.of(2025, 5))
            }
        }
    }

    Given("das Blättern im Jahresmodus") {
        val (_, viewModel) = setupAnalyticsViewModel()
        viewModel.setTimeRange(TimeRange.Year(2025))

        When("toggleTimeRange(increment = true) aufgerufen wird") {
            viewModel.toggleTimeRange(increment = true)

            Then("wechselt das Jahr auf 2026") {
                viewModel.uiState.value.timeRange shouldBe TimeRange.Year(2026)
            }
        }

        When("toggleTimeRange(increment = false) aufgerufen wird") {
            viewModel.toggleTimeRange(increment = false)

            Then("wechselt das Jahr zurück auf 2025") {
                viewModel.uiState.value.timeRange shouldBe TimeRange.Year(2025)
            }
        }
    }
})
