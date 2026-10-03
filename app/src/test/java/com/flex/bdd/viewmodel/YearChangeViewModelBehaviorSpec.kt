package com.flex.bdd.viewmodel

import com.flex.domain.model.DayType
import com.flex.domain.model.FlextimeBalance
import com.flex.domain.model.Settings
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.ui.yearchange.YearChangeViewModel
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class YearChangeViewModelBehaviorSpec : BehaviorSpec({

    val testDispatcher = UnconfinedTestDispatcher()

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    Given("ein Nutzer am Ende des Arbeitsjahres mit Urlaubsanspruch und Gleitzeitsaldo") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>()
        val calculateFlextime = mockk<CalculateFlextimeUseCase>()

        val baseSettings = Settings(
            id = 1L,
            settingsYear = 2025,
            annualVacationDays = 30,
            carryOverVacationDays = 5,
            initialFlextimeMinutes = 120,
            initialOvertimeMinutes = 60
        )

        // 25 tatsächlich genommene Urlaubstage
        val vacationDays = (1..25).map { day ->
            WorkDay(
                id = day.toLong(),
                date = LocalDate.of(2025, 7, day),
                dayType = DayType.VACATION,
                location = WorkLocation.OFFICE,
                isPlanned = false
            )
        }

        // 2 geplante Urlaubstage, die nicht als genommen zählen dürfen
        val plannedVacationDays = (26..27).map { day ->
            WorkDay(
                id = (100 + day).toLong(),
                date = LocalDate.of(2025, 7, day),
                dayType = DayType.VACATION,
                location = WorkLocation.OFFICE,
                isPlanned = true
            )
        }

        every { getSettings() } returns flowOf(baseSettings)
        every { workDayRepository.getWorkDaysForYear(2025) } returns flowOf(vacationDays + plannedVacationDays)
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance(
            initialMinutes = 120,
            earnedMinutes = 180,
            totalMinutes = 300,
            targetMinutes = 0,
            overtimeMinutes = 180,
            earnedOvertimeMinutes = 120
        )

        val savedSettingsSlot = slot<Settings>()
        coEvery { settingsRepository.saveSettings(capture(savedSettingsSlot)) } returns Unit

        val viewModel = YearChangeViewModel(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            getSettings = getSettings,
            calculateFlextime = calculateFlextime
        )

        When("der initiale State geladen wird") {
            val state = viewModel.uiState.value

            Then("entspricht das Quelljahr dem Einstellungsjahr und das Zieljahr dem Folgejahr") {
                state.sourceYear shouldBe 2025
                state.targetYear shouldBe 2026
            }

            Then("werden die genommenen Urlaubstage ohne geplante Tage ermittelt") {
                state.usedVacationDays shouldBe 25
            }

            Then("wird der verbleibende Resturlaub korrekt berechnet") {
                // 30 (Jahresurlaub) + 5 (Vorjahresübertrag) - 25 (genommen) = 10 Tage
                state.remainingVacationDays shouldBe 10
            }

            Then("entspricht currentAnnualVacationDays den aktuellen Einstellungen") {
                state.currentAnnualVacationDays shouldBe 30
            }

            Then("werden Gleitzeit- und Überstundensaldo aus der Flextime-Berechnung bezogen") {
                state.flextimeMinutes shouldBe 300L
                state.overtimeMinutes shouldBe 180L
            }
        }

        When("der Jahreswechsel mit den berechneten Werten angewendet wird") {
            viewModel.applyYearChange(carryOverDays = 10, annualDays = 28)

            Then("wird der Resturlaub als carryOverVacationDays für das neue Zieljahr gespeichert") {
                val captured = savedSettingsSlot.captured
                captured.settingsYear shouldBe 2026
                captured.carryOverVacationDays shouldBe 10
                captured.annualVacationDays shouldBe 28
                captured.specialVacationDays shouldBe 0
            }

            Then("wird der Gleitzeitsaldo als initialFlextimeMinutes übernommen") {
                val captured = savedSettingsSlot.captured
                captured.initialFlextimeMinutes shouldBe 300
            }

            Then("wird der Überstundensaldo als initialOvertimeMinutes übernommen") {
                val captured = savedSettingsSlot.captured
                captured.initialOvertimeMinutes shouldBe 180
            }
        }
    }

    Given("ein Fall mit mehr genommenen Urlaubstagen als dem Gesamtkontingent") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>()
        val calculateFlextime = mockk<CalculateFlextimeUseCase>()

        val settings = Settings(
            settingsYear = 2025,
            annualVacationDays = 30,
            carryOverVacationDays = 0
        )

        // 32 genommene Urlaubstage
        val vacationDays = (1..32).map { day ->
            WorkDay(
                id = day.toLong(),
                date = LocalDate.of(2025, 1, 1).plusDays(day.toLong()),
                dayType = DayType.VACATION,
                location = WorkLocation.OFFICE,
                isPlanned = false
            )
        }

        every { getSettings() } returns flowOf(settings)
        every { workDayRepository.getWorkDaysForYear(2025) } returns flowOf(vacationDays)
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance()

        val viewModel = YearChangeViewModel(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            getSettings = getSettings,
            calculateFlextime = calculateFlextime
        )

        When("der State ausgewertet wird") {
            val state = viewModel.uiState.value

            Then("werden alle 32 Tage als genommen gewertet") {
                state.usedVacationDays shouldBe 32
            }

            Then("ist der verbleibende Resturlaub niemals negativ sondern 0") {
                // 30 - 32 = -2 -> maxOf(0, -2) = 0
                state.remainingVacationDays shouldBe 0
            }
        }
    }

    Given("ein Jahresabschluss mit Minusstunden auf dem Gleitzeitkonto") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>()
        val calculateFlextime = mockk<CalculateFlextimeUseCase>()

        val baseSettings = Settings(
            id = 1L,
            settingsYear = 2025,
            annualVacationDays = 30,
            carryOverVacationDays = 0,
            initialFlextimeMinutes = 0,
            initialOvertimeMinutes = 0
        )

        every { getSettings() } returns flowOf(baseSettings)
        every { workDayRepository.getWorkDaysForYear(2025) } returns flowOf(emptyList())
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance(
            initialMinutes = 0,
            earnedMinutes = -240,
            totalMinutes = -240,
            targetMinutes = 0,
            overtimeMinutes = 0,
            earnedOvertimeMinutes = 0
        )

        val savedSettingsSlot = slot<Settings>()
        coEvery { settingsRepository.saveSettings(capture(savedSettingsSlot)) } returns Unit

        val viewModel = YearChangeViewModel(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            getSettings = getSettings,
            calculateFlextime = calculateFlextime
        )

        When("der Jahreswechsel mit Minusstunden angewendet wird") {
            viewModel.applyYearChange(carryOverDays = 0, annualDays = 30)

            Then("wird der negative Gleitzeitsaldo als initialFlextimeMinutes für das neue Jahr gesetzt") {
                val captured = savedSettingsSlot.captured
                captured.initialFlextimeMinutes shouldBe -240
                captured.settingsYear shouldBe 2026
            }
        }
    }

    Given("die Steuerung des Jahreswechsel-Dialogs für ein vergangenes Jahr") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>()
        val calculateFlextime = mockk<CalculateFlextimeUseCase>()

        // Jahr 2020 liegt in der Vergangenheit -> automatischer Dialog
        val pastYearSettings = Settings(
            settingsYear = 2020
        )

        every { getSettings() } returns flowOf(pastYearSettings)
        every { workDayRepository.getWorkDaysForYear(2020) } returns flowOf(emptyList())
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance()

        val viewModel = YearChangeViewModel(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            getSettings = getSettings,
            calculateFlextime = calculateFlextime
        )

        When("das ViewModel geladen wird") {
            Then("wird der Dialog automatisch geöffnet") {
                viewModel.uiState.value.showDialog.shouldBeTrue()
            }
        }

        When("der Dialog geschlossen wird (dismiss)") {
            viewModel.dismiss()

            Then("wird showDialog auf false gesetzt") {
                viewModel.uiState.value.showDialog.shouldBeFalse()
            }
        }

        When("der Dialog manuell wieder geöffnet wird (openDialog)") {
            viewModel.openDialog()

            Then("wird showDialog wieder true") {
                viewModel.uiState.value.showDialog.shouldBeTrue()
            }
        }
    }

    Given("die Steuerung des Jahreswechsel-Dialogs für das aktuelle Jahr") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>()
        val calculateFlextime = mockk<CalculateFlextimeUseCase>()

        val currentYear = LocalDate.now().year
        val currentYearSettings = Settings(
            settingsYear = currentYear
        )

        every { getSettings() } returns flowOf(currentYearSettings)
        every { workDayRepository.getWorkDaysForYear(currentYear) } returns flowOf(emptyList())
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance()

        val viewModel = YearChangeViewModel(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            getSettings = getSettings,
            calculateFlextime = calculateFlextime
        )

        When("das Einstellungsjahr dem aktuellen Kalenderjahr entspricht") {
            Then("bleibt der Dialog standardmäßig geschlossen") {
                viewModel.uiState.value.showDialog.shouldBeFalse()
            }
        }

        When("der Nutzer den Dialog manuell öffnet") {
            viewModel.openDialog()

            Then("wird der Dialog angezeigt") {
                viewModel.uiState.value.showDialog.shouldBeTrue()
            }
        }

        When("der Dialog anschließend wieder geschlossen wird") {
            viewModel.dismiss()

            Then("ist der Dialog wieder ausgeblendet") {
                viewModel.uiState.value.showDialog.shouldBeFalse()
            }
        }
    }
})
