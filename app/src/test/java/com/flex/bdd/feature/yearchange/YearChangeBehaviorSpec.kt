package com.flex.bdd.feature.yearchange

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class YearChangeBehaviorSpec : BehaviorSpec({

    val testDispatcher = UnconfinedTestDispatcher()

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    Given("ein Nutzer am Ende des Arbeitsjahres 2025 mit Resturlaub") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>()
        val calculateFlextime = mockk<CalculateFlextimeUseCase>()

        val baseSettings = Settings(
            id = 1L,
            settingsYear = 2025,
            annualVacationDays = 30,
            carryOverVacationDays = 4, // Insgesamt 34 Urlaubstage
            initialFlextimeMinutes = 60,
            initialOvertimeMinutes = 30
        )

        // 26 genommene Urlaubstage
        val vacationDays = (1..26).map { day ->
            WorkDay(
                id = day.toLong(),
                date = LocalDate.of(2025, 6, day),
                dayType = DayType.VACATION,
                location = WorkLocation.OFFICE,
                isPlanned = false
            )
        }

        every { getSettings() } returns flowOf(baseSettings)
        every { workDayRepository.getWorkDaysForYear(2025) } returns flowOf(vacationDays)
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance(
            initialMinutes = 60,
            earnedMinutes = 300,
            totalMinutes = 360, // +6 Stunden
            targetMinutes = 0,
            overtimeMinutes = 150, // +2.5 Stunden
            earnedOvertimeMinutes = 120
        )

        val savedSettingsSlot = slot<Settings>()
        coEvery { settingsRepository.saveSettings(capture(savedSettingsSlot)) } returns Unit

        val viewModel = YearChangeViewModel(
            workDayRepository,
            settingsRepository,
            getSettings,
            calculateFlextime
        )

        When("die Jahreswechsel-Berechnung durchgeführt wird") {
            val state = viewModel.uiState.value

            Then("wird das Quell- und Zieljahr korrekt ermittelt") {
                state.sourceYear shouldBe 2025
                state.targetYear shouldBe 2026
            }

            Then("werden die genommenen und verbleibenden Urlaubstage korrekt ermittelt") {
                state.usedVacationDays shouldBe 26
                // 30 + 4 - 26 = 8 Resturlaubstage
                state.remainingVacationDays shouldBe 8
            }

            Then("werden Gleitzeit- und Überstundensaldo aus der Flextime-Berechnung bezogen") {
                state.flextimeMinutes shouldBe 360L
                state.overtimeMinutes shouldBe 150L
            }
        }

        When("der Jahreswechsel mit den berechneten Resturlaubstagen übernommen wird") {
            viewModel.applyYearChange(carryOverDays = 8, annualDays = 30)

            Then("wird der Resturlaub als carryOverVacationDays für das neue Jahr 2026 gespeichert") {
                val captured = savedSettingsSlot.captured
                captured.settingsYear shouldBe 2026
                captured.carryOverVacationDays shouldBe 8
                captured.annualVacationDays shouldBe 30
                captured.specialVacationDays shouldBe 0
            }

            Then("wird der Gleitzeitsaldo Ende des Jahres als initialFlextimeMinutes gesetzt") {
                val captured = savedSettingsSlot.captured
                captured.initialFlextimeMinutes shouldBe 360
            }

            Then("wird der Überstundensaldo für das neue Jahr als initialOvertimeMinutes gesetzt") {
                val captured = savedSettingsSlot.captured
                captured.initialOvertimeMinutes shouldBe 150
            }
        }
    }

    Given("ein negatives Gleitzeitkonto am Jahresende") {
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
            earnedMinutes = -180,
            totalMinutes = -180, // -3 Stunden Minusstunden
            targetMinutes = 0,
            overtimeMinutes = 0,
            earnedOvertimeMinutes = 0
        )

        val savedSettingsSlot = slot<Settings>()
        coEvery { settingsRepository.saveSettings(capture(savedSettingsSlot)) } returns Unit

        val viewModel = YearChangeViewModel(
            workDayRepository,
            settingsRepository,
            getSettings,
            calculateFlextime
        )

        When("der Jahreswechsel angewendet wird") {
            viewModel.applyYearChange(carryOverDays = 0, annualDays = 30)

            Then("wird der negative Saldo als initialFlextimeMinutes in das Folgejahr übertragen") {
                val captured = savedSettingsSlot.captured
                captured.initialFlextimeMinutes shouldBe -180
                captured.settingsYear shouldBe 2026
            }
        }
    }

    Given("die Steuerung des Jahreswechsel-Dialogs") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>()
        val calculateFlextime = mockk<CalculateFlextimeUseCase>()

        val oldYearSettings = Settings(
            settingsYear = 2020 // Deutlich in der Vergangenheit -> automatischer Dialog
        )

        every { getSettings() } returns flowOf(oldYearSettings)
        every { workDayRepository.getWorkDaysForYear(2020) } returns flowOf(emptyList())
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance(
            initialMinutes = 0,
            earnedMinutes = 0,
            totalMinutes = 0,
            targetMinutes = 0,
            overtimeMinutes = 0,
            earnedOvertimeMinutes = 0
        )

        val viewModel = YearChangeViewModel(
            workDayRepository,
            settingsRepository,
            getSettings,
            calculateFlextime
        )

        When("das Einstellungsjahr älter als das aktuelle Kalenderjahr ist") {
            Then("wird der Jahreswechsel-Dialog automatisch eingeblendet") {
                viewModel.uiState.value.showDialog.shouldBeTrue()
            }
        }

        When("der Nutzer den Dialog wegklickt (dismiss)") {
            viewModel.dismiss()

            Then("wird der Dialog geschlossen") {
                viewModel.uiState.value.showDialog.shouldBeFalse()
            }
        }

        When("der Dialog danach manuell geöffnet wird (openDialog)") {
            viewModel.openDialog()

            Then("wird der Dialog wieder sichtbar") {
                viewModel.uiState.value.showDialog.shouldBeTrue()
            }
        }
    }
})
