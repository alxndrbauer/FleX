package com.flex.bdd.feature.quota

import com.flex.domain.model.DayType
import com.flex.domain.model.FlextimeBalance
import com.flex.domain.model.QuotaStatus
import com.flex.domain.model.Settings
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.GetMonthWorkDaysUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.ui.quota.QuotaViewModel
import com.flex.ui.quota.VacationInfo
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
class SpecialVacationPayoutBehaviorSpec : BehaviorSpec({

    val testDispatcher = UnconfinedTestDispatcher()

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    fun createQuotaFixture(
        settings: Settings = Settings(specialVacationDays = 5, specialVacationPaidOut = false),
        yearDays: List<WorkDay> = emptyList()
    ): Triple<QuotaViewModel, WorkDayRepository, SettingsRepository> {
        val getMonthWorkDays = mockk<GetMonthWorkDaysUseCase>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>(relaxed = true)
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val calculateQuota = mockk<CalculateQuotaUseCase>(relaxed = true)
        val calculateFlextime = mockk<CalculateFlextimeUseCase>(relaxed = true)

        every { getSettings() } returns flowOf(settings)
        every { getMonthWorkDays(any()) } returns flowOf(emptyList())
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(yearDays)
        coEvery { settingsRepository.getQuotaRules() } returns flowOf(emptyList())
        coEvery { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { calculateQuota(any(), any(), any(), any(), any(), any()) } returns QuotaStatus()
        every { calculateFlextime(any(), any(), any()) } returns FlextimeBalance()

        val viewModel = QuotaViewModel(
            getMonthWorkDays,
            getSettings,
            workDayRepository,
            settingsRepository,
            calculateQuota,
            calculateFlextime
        )
        return Triple(viewModel, workDayRepository, settingsRepository)
    }

    Given("die Berechnung des Sonderurlaubskontingents in VacationInfo") {

        When("5 Tage Anspruch bestehen, keine Tage genommen wurden und keine Auszahlung vorliegt") {
            val info = VacationInfo(
                specialDays = 5,
                usedSpecialDays = 0,
                isSpecialPaidOut = false
            )

            Then("sind 5 Tage übrig und eine Auszahlung ist zulässig") {
                info.remainingSpecialDays shouldBe 5
                info.canPayoutSpecialDays.shouldBeTrue()
            }
        }

        When("5 Tage Anspruch bestehen und die Auszahlung aktiviert wurde") {
            val info = VacationInfo(
                specialDays = 5,
                usedSpecialDays = 0,
                isSpecialPaidOut = true
            )

            Then("ist das verbleibende Kontingent 0 Tage und eine erneute Auszahlung ist nicht möglich") {
                info.remainingSpecialDays shouldBe 0
                info.canPayoutSpecialDays.shouldBeFalse()
            }
        }

        When("bereits 2 Sonderurlaubstage tatsächlich genommen wurden") {
            val info = VacationInfo(
                specialDays = 5,
                usedSpecialDays = 2,
                isSpecialPaidOut = false
            )

            Then("sind 3 Tage übrig, aber eine Auszahlung ist nicht zulässig (All-or-Nothing)") {
                info.remainingSpecialDays shouldBe 3
                info.canPayoutSpecialDays.shouldBeFalse()
            }
        }
    }

    Given("ein Benutzer lässt sein gesamtes Sonderurlaubskontingent (5 Tage) über QuotaViewModel auszahlen") {

        When("noch keine Sonderurlaubstage genommen oder geplant wurden") {
            val (viewModel, _, settingsRepository) = createQuotaFixture()

            viewModel.payoutSpecialVacation()

            Then("wird specialVacationPaidOut = true in den Settings gespeichert") {
                val settingsSlot = slot<Settings>()
                coVerify { settingsRepository.saveSettings(capture(settingsSlot)) }
                settingsSlot.captured.specialVacationPaidOut.shouldBeTrue()
            }
        }

        When("noch keine Tage genommen wurden, aber 2 Sonderurlaubstage für den Sommer geplant sind") {
            val plannedDay1 = WorkDay(
                id = 10L,
                date = LocalDate.of(2026, 7, 1),
                dayType = DayType.SPECIAL_VACATION,
                location = WorkLocation.HOME_OFFICE,
                isPlanned = true
            )
            val plannedDay2 = WorkDay(
                id = 11L,
                date = LocalDate.of(2026, 7, 2),
                dayType = DayType.SPECIAL_VACATION,
                location = WorkLocation.HOME_OFFICE,
                isPlanned = true
            )

            val (viewModel, workDayRepository, settingsRepository) = createQuotaFixture(
                yearDays = listOf(plannedDay1, plannedDay2)
            )

            viewModel.payoutSpecialVacation()

            Then("werden die geplanten Sonderurlaubstage gelöscht und die Auszahlung gespeichert") {
                coVerify { workDayRepository.deleteWorkDay(plannedDay1) }
                coVerify { workDayRepository.deleteWorkDay(plannedDay2) }

                val settingsSlot = slot<Settings>()
                coVerify { settingsRepository.saveSettings(capture(settingsSlot)) }
                settingsSlot.captured.specialVacationPaidOut.shouldBeTrue()
            }
        }

        When("bereits 1 Sonderurlaubstag genommen wurde") {
            val takenDay = WorkDay(
                id = 1L,
                date = LocalDate.of(2026, 3, 15),
                dayType = DayType.SPECIAL_VACATION,
                location = WorkLocation.HOME_OFFICE,
                isPlanned = false
            )

            val (viewModel, _, settingsRepository) = createQuotaFixture(
                yearDays = listOf(takenDay)
            )

            viewModel.payoutSpecialVacation()

            Then("wird die Auszahlung nicht ausgeführt (Settings werden nicht verändert)") {
                coVerify(exactly = 0) { settingsRepository.saveSettings(any()) }
            }
        }
    }

    Given("eine bestehende Auszahlung von Sonderurlaub") {

        When("der Benutzer die Auszahlung widerruft (revertSpecialVacationPayout)") {
            val (viewModel, _, settingsRepository) = createQuotaFixture(
                settings = Settings(specialVacationDays = 5, specialVacationPaidOut = true)
            )

            viewModel.revertSpecialVacationPayout()

            Then("wird specialVacationPaidOut = false in den Settings gespeichert") {
                val settingsSlot = slot<Settings>()
                coVerify { settingsRepository.saveSettings(capture(settingsSlot)) }
                settingsSlot.captured.specialVacationPaidOut.shouldBeFalse()
            }
        }
    }

    Given("ein Jahreswechsel (YearChangeViewModel) nach einem Jahr mit ausgezahltem Sonderurlaub") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>(relaxed = true)
        val calculateFlextime = mockk<CalculateFlextimeUseCase>(relaxed = true)

        val oldSettings = Settings(
            settingsYear = 2026,
            specialVacationDays = 5,
            specialVacationPaidOut = true
        )
        every { getSettings() } returns flowOf(oldSettings)
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())
        every { calculateFlextime(any(), any(), any()) } returns FlextimeBalance()

        val yearChangeViewModel = YearChangeViewModel(
            workDayRepository,
            settingsRepository,
            getSettings,
            calculateFlextime
        )

        When("der Jahreswechsel für das neue Jahr durchgeführt wird") {
            yearChangeViewModel.applyYearChange(carryOverDays = 5, annualDays = 30)

            Then("ist specialVacationPaidOut im neuen Jahr wieder false") {
                val settingsSlot = slot<Settings>()
                coVerify { settingsRepository.saveSettings(capture(settingsSlot)) }
                settingsSlot.captured.specialVacationPaidOut.shouldBeFalse()
            }
        }
    }
})
