package com.flex.bdd.feature.widget

import com.flex.domain.model.DayType
import com.flex.domain.model.FlextimeBalance
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.DayWorkTimeResult
import com.flex.widget.FlexWidgetDataProvider
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.LocalTime

class FlexWidgetBehaviorSpec : BehaviorSpec({

    val defaultSettings = Settings(
        dailyWorkMinutes = 480, // 8h
        monthlyWorkMinutes = 9600,
        initialFlextimeMinutes = 60,
        initialOvertimeMinutes = 0
    )

    Given("ein Benutzer ist aktuell ausgestempelt") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val calculateDayWorkTime = mockk<CalculateDayWorkTimeUseCase>()
        val calculateFlextime = mockk<CalculateFlextimeUseCase>()

        every { settingsRepository.getSettings() } returns flowOf(defaultSettings)
        every { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { settingsRepository.getWorkTimeRuleForDate(any(), any()) } returns null
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())

        val dataProvider = FlexWidgetDataProvider(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            calculateDayWorkTime = calculateDayWorkTime,
            calculateFlextime = calculateFlextime
        )

        val today = LocalDate.now()
        val workDay = WorkDay(
            id = 1L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(
                TimeBlock(
                    id = 10L,
                    workDayId = 1L,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(12, 0),
                    location = WorkLocation.OFFICE
                )
            )
        )

        every { workDayRepository.getWorkDay(today) } returns flowOf(workDay)
        every { calculateDayWorkTime(any(), any()) } returns DayWorkTimeResult(
            grossMinutes = 240,
            netMinutes = 240,
            breakMinutes = 0,
            exceedsMaxHours = false
        )
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance(
            initialMinutes = 60,
            earnedMinutes = 15,
            totalMinutes = 75,
            targetMinutes = 480
        )

        When("der FlexWidgetDataProvider den Widget-State berechnet") {
            val state = dataProvider.getWidgetState()

            Then("ist isClockRunning false") {
                state.isClockRunning shouldBe false
            }
            Then("entspricht netMinutesToday der abgeschlossenen Arbeitszeit (4h = 240m)") {
                state.netMinutesToday shouldBe 240L
            }
            Then("ist targetMinutesToday das Tagessoll (480m)") {
                state.targetMinutesToday shouldBe 480L
            }
            Then("wird der aktuelle Gleitzeitsaldo geliefert (75m)") {
                state.flextimeBalanceMinutes shouldBe 75L
            }
            Then("entspricht der Standort dem des Arbeitstags") {
                state.currentLocation shouldBe WorkLocation.OFFICE
            }
        }
    }

    Given("ein Benutzer ist aktuell im Home-Office eingestempelt mit laufendem Block") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val calculateDayWorkTime = mockk<CalculateDayWorkTimeUseCase>()
        val calculateFlextime = mockk<CalculateFlextimeUseCase>()

        every { settingsRepository.getSettings() } returns flowOf(defaultSettings)
        every { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { settingsRepository.getWorkTimeRuleForDate(any(), any()) } returns null
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())

        val dataProvider = FlexWidgetDataProvider(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            calculateDayWorkTime = calculateDayWorkTime,
            calculateFlextime = calculateFlextime
        )

        val today = LocalDate.now()
        val runningBlock = TimeBlock(
            id = 20L,
            workDayId = 2L,
            startTime = LocalTime.now().minusHours(2),
            endTime = null,
            location = WorkLocation.HOME_OFFICE
        )
        val workDay = WorkDay(
            id = 2L,
            date = today,
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(runningBlock)
        )

        every { workDayRepository.getWorkDay(today) } returns flowOf(workDay)
        every { calculateDayWorkTime(any(), any()) } returns DayWorkTimeResult(
            grossMinutes = 120,
            netMinutes = 120,
            breakMinutes = 0,
            exceedsMaxHours = false
        )
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance(
            initialMinutes = 0,
            earnedMinutes = -360,
            totalMinutes = -30,
            targetMinutes = 480
        )

        When("der FlexWidgetDataProvider den Widget-State berechnet") {
            val state = dataProvider.getWidgetState()

            Then("ist isClockRunning true") {
                state.isClockRunning shouldBe true
            }
            Then("ist der aktuelle Standort HOME_OFFICE") {
                state.currentLocation shouldBe WorkLocation.HOME_OFFICE
            }
            Then("beinhaltet die Arbeitszeit den laufenden Block (120m)") {
                state.netMinutesToday shouldBe 120L
            }
            Then("wird ein negativer Gleitzeitsaldo korrekt übergeben (-30m)") {
                state.flextimeBalanceMinutes shouldBe -30L
            }
        }
    }

    Given("noch kein Arbeitstag für heute existiert") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val calculateDayWorkTime = mockk<CalculateDayWorkTimeUseCase>()
        val calculateFlextime = mockk<CalculateFlextimeUseCase>()

        every { settingsRepository.getSettings() } returns flowOf(defaultSettings)
        every { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { settingsRepository.getWorkTimeRuleForDate(any(), any()) } returns null
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())

        val dataProvider = FlexWidgetDataProvider(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            calculateDayWorkTime = calculateDayWorkTime,
            calculateFlextime = calculateFlextime
        )

        val today = LocalDate.now()
        every { workDayRepository.getWorkDay(today) } returns flowOf(null)
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance(
            initialMinutes = 0,
            earnedMinutes = 0,
            totalMinutes = 0,
            targetMinutes = 480
        )

        When("der FlexWidgetDataProvider den Widget-State abruft") {
            val state = dataProvider.getWidgetState()

            Then("ist isClockRunning false") {
                state.isClockRunning shouldBe false
            }
            Then("ist die Arbeitszeit heute 0") {
                state.netMinutesToday shouldBe 0L
            }
            Then("ist das Tagessoll 480 Minuten") {
                state.targetMinutesToday shouldBe 480L
            }
            Then("ist der Default-Standort OFFICE") {
                state.currentLocation shouldBe WorkLocation.OFFICE
            }
        }
    }

    Given("ein Benutzer befindet sich in Pause") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val calculateDayWorkTime = mockk<CalculateDayWorkTimeUseCase>()
        val calculateFlextime = mockk<CalculateFlextimeUseCase>()
        val pausePreferences = mockk<com.flex.data.local.PausePreferences>()

        every { settingsRepository.getSettings() } returns flowOf(defaultSettings)
        every { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { settingsRepository.getWorkTimeRuleForDate(any(), any()) } returns null
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())
        every { pausePreferences.isPaused } returns true

        val dataProvider = FlexWidgetDataProvider(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            calculateDayWorkTime = calculateDayWorkTime,
            calculateFlextime = calculateFlextime,
            pausePreferences = pausePreferences
        )

        val today = LocalDate.now()
        val workDay = WorkDay(
            id = 1L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(
                TimeBlock(
                    id = 10L,
                    workDayId = 1L,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(12, 0),
                    location = WorkLocation.OFFICE
                )
            )
        )
        every { workDayRepository.getWorkDay(today) } returns flowOf(workDay)
        every { calculateDayWorkTime(any(), any()) } returns DayWorkTimeResult(
            grossMinutes = 240,
            netMinutes = 240,
            breakMinutes = 0,
            exceedsMaxHours = false
        )
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance(
            totalMinutes = 0,
            targetMinutes = 480
        )

        When("der FlexWidgetDataProvider den Widget-State abruft") {
            val state = dataProvider.getWidgetState()

            Then("ist isClockRunning false") {
                state.isClockRunning shouldBe false
            }
            Then("ist isPaused true") {
                state.isPaused shouldBe true
            }
        }
    }
})
