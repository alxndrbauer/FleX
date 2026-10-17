package com.flex.bdd.viewmodel

import com.flex.domain.model.DEFAULT_WORK_DAYS
import com.flex.domain.model.DayType
import com.flex.domain.model.FlextimeBalance
import com.flex.domain.model.QuotaStatus
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateAnalyticsUseCase
import com.flex.domain.usecase.CalculateDashboardMetricsUseCase
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.GetMonthWorkDaysUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.ui.dashboard.DashboardViewModel
import com.flex.domain.model.getRuleForDate
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelBehaviorSpec : BehaviorSpec({

    val testDispatcher = UnconfinedTestDispatcher()

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    val fourDays = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY
    )

    val defaultSettings = Settings(
        dailyWorkMinutes = 426,
        monthlyWorkMinutes = 9372,
        officeQuotaPercent = 40,
        officeQuotaMinDays = 8,
        initialFlextimeMinutes = 0
    )

    fun createDurationBlock(minutes: Long, location: WorkLocation = WorkLocation.OFFICE): TimeBlock {
        val start = LocalTime.of(8, 0)
        return TimeBlock(
            startTime = start,
            endTime = start.plusMinutes(minutes),
            location = location,
            isDuration = true
        )
    }

    // =========================================================================
    // Szenario 1: Dashboard-Wochenstatus bei 4-Tage-Woche (Mo-Do, 533 Min/Tag)
    // =========================================================================
    Given("ein Benutzer im 4-Tage-Woche-Modell (Mo-Do, 533 Min/Tag)") {
        val getMonthWorkDays = mockk<GetMonthWorkDaysUseCase>()
        val getSettings = mockk<GetSettingsUseCase>()
        val workDayRepository = mockk<WorkDayRepository>()
        val settingsRepository = mockk<SettingsRepository>()

        val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
        val calculateFlextime = CalculateFlextimeUseCase(calculateDayWorkTime)
        val calculateQuota = CalculateQuotaUseCase(calculateDayWorkTime)
        val calculateDashboardMetrics = CalculateDashboardMetricsUseCase(calculateDayWorkTime)
        val calculateAnalytics = CalculateAnalyticsUseCase(calculateDayWorkTime, calculateFlextime)

        val currentYearMonth = YearMonth.now()
        val fourDayRule = WorkTimeRule(
            id = 1L,
            validFrom = currentYearMonth,
            dailyWorkMinutes = 533,
            monthlyWorkMinutes = 18 * 533,
            workDays = fourDays
        )
        val rules = listOf(fourDayRule)

        every { getSettings() } returns flowOf(defaultSettings)
        every { settingsRepository.getWorkTimeRules() } returns flowOf(rules)
        every { settingsRepository.getQuotaRules() } returns flowOf(emptyList())
        every { settingsRepository.getQuotaRuleForMonth(any(), any()) } returns null
        every { settingsRepository.getWorkTimeRuleForDate(any(), any()) } answers {
            rules.getRuleForDate(firstArg())
        }
        every { getMonthWorkDays(any()) } returns flowOf(emptyList())
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())

        val viewModel = DashboardViewModel(
            getMonthWorkDays = getMonthWorkDays,
            getSettings = getSettings,
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            calculateQuota = calculateQuota,
            calculateFlextime = calculateFlextime,
            calculateDashboardMetrics = calculateDashboardMetrics,
            calculateAnalytics = calculateAnalytics
        )

        When("das Dashboard den aktuellen Wochenstatus (currentWeekStatus) berechnet") {
            testDispatcher.scheduler.advanceUntilIdle()
            val state = viewModel.uiState.value
            val weekStatus = state.currentWeekStatus

            Then("ist der Wochenstatus für das aktuelle Jahr vorhanden") {
                weekStatus.shouldNotBeNull()
            }

            Then("beträgt das Wochenziel exakt 4 * 533 Min = 2.132 Minuten (Freitag zählt nicht)") {
                weekStatus!!.targetMinutes shouldBe (4 * 533).toLong()
                weekStatus.targetMinutes shouldBe 2132L
            }
        }
    }

    // =========================================================================
    // Szenario 2: Dashboard mit unterjährigem Wechsel von Vollzeit (5 Tage) auf 4-Tage-Woche
    // =========================================================================
    Given("ein unterjähriger Wechsel zum 01.07.2026 von Vollzeit (480 Min) auf 4-Tage-Woche (533 Min)") {
        val getMonthWorkDays = mockk<GetMonthWorkDaysUseCase>()
        val getSettings = mockk<GetSettingsUseCase>()
        val workDayRepository = mockk<WorkDayRepository>()
        val settingsRepository = mockk<SettingsRepository>()

        val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
        val calculateFlextime = CalculateFlextimeUseCase(calculateDayWorkTime)
        val calculateQuota = CalculateQuotaUseCase(calculateDayWorkTime)
        val calculateDashboardMetrics = CalculateDashboardMetricsUseCase(calculateDayWorkTime)
        val calculateAnalytics = CalculateAnalyticsUseCase(calculateDayWorkTime, calculateFlextime)

        val juneMonth = YearMonth.of(2026, 6)
        val julyMonth = YearMonth.of(2026, 7)

        val ruleJune = WorkTimeRule(
            id = 1L,
            validFrom = YearMonth.of(2026, 1),
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 10560,
            workDays = DEFAULT_WORK_DAYS
        )
        val ruleJuly = WorkTimeRule(
            id = 2L,
            validFrom = julyMonth,
            dailyWorkMinutes = 533,
            monthlyWorkMinutes = 9594,
            workDays = fourDays
        )
        val rules = listOf(ruleJune, ruleJuly)

        // Juni: 2 Arbeitstage à 480 Min
        val juneWorkDays = listOf(
            WorkDay(
                date = LocalDate.of(2026, 6, 1),
                dayType = DayType.WORK,
                timeBlocks = listOf(createDurationBlock(480, WorkLocation.OFFICE))
            ),
            WorkDay(
                date = LocalDate.of(2026, 6, 2),
                dayType = DayType.WORK,
                timeBlocks = listOf(createDurationBlock(480, WorkLocation.OFFICE))
            )
        )

        // Juli: 1 Arbeitstag à 533 Min am Mittwoch + 1 freier Freitag mit 240 Min Überstunden
        val julyWorkDays = listOf(
            WorkDay(
                date = LocalDate.of(2026, 7, 1), // Mittwoch
                dayType = DayType.WORK,
                timeBlocks = listOf(createDurationBlock(533, WorkLocation.OFFICE))
            ),
            WorkDay(
                date = LocalDate.of(2026, 7, 3), // Freitag (arbeitsfrei)
                dayType = DayType.WORK,
                timeBlocks = listOf(createDurationBlock(240, WorkLocation.HOME_OFFICE))
            )
        )

        every { getSettings() } returns flowOf(defaultSettings)
        every { settingsRepository.getWorkTimeRules() } returns flowOf(rules)
        every { settingsRepository.getQuotaRules() } returns flowOf(emptyList())
        every { settingsRepository.getQuotaRuleForMonth(any(), any()) } returns null
        every { settingsRepository.getWorkTimeRuleForDate(any(), any()) } answers {
            rules.getRuleForDate(firstArg())
        }
        every { getMonthWorkDays(any()) } returns flowOf(emptyList())
        every { getMonthWorkDays(julyMonth) } returns flowOf(julyWorkDays)
        every { workDayRepository.getWorkDaysForYear(2026) } returns flowOf(juneWorkDays + julyWorkDays)

        When("das Dashboard auf Juli 2026 geschaltet wird") {

            val viewModel = DashboardViewModel(
                getMonthWorkDays = getMonthWorkDays,
                getSettings = getSettings,
                workDayRepository = workDayRepository,
                settingsRepository = settingsRepository,
                calculateQuota = calculateQuota,
                calculateFlextime = calculateFlextime,
                calculateDashboardMetrics = calculateDashboardMetrics,
                calculateAnalytics = calculateAnalytics
            )
            viewModel.selectMonth(julyMonth)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value

            Then("ist das Gleitzeitsaldo für Juli inklusive der 100% Gutschrift für den freien Freitag positiv (+240 Min)") {
                // Mi 01.07.: 533 - 533 = 0
                // Fr 03.07. (arbeitsfrei): 240 - 0 = +240
                state.flextimeBalance.earnedMinutes shouldBe 240L
            }

            Then("basiert das dynamische Monatssoll auf den 18 Mo-Do Arbeitstagen à 533 Min") {
                state.flextimeBalance.targetMinutes shouldBe 18L * 533L
                state.flextimeBalance.targetMinutes shouldBe 9594L
            }
        }
    }
})
