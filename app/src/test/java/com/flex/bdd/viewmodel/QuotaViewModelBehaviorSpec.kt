package com.flex.bdd.viewmodel

import com.flex.domain.model.DayType
import com.flex.domain.model.FlextimeBalance
import com.flex.domain.model.QuotaRule
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
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
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
class QuotaViewModelBehaviorSpec : BehaviorSpec({

    val testDispatcher = UnconfinedTestDispatcher()

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    fun setupQuotaViewModel(
        settings: Settings = Settings(),
        monthDays: List<WorkDay> = emptyList(),
        yearDays: List<WorkDay> = emptyList(),
        quotaRules: List<QuotaRule> = emptyList(),
        quotaResult: QuotaStatus = QuotaStatus(),
        flextimeResult: FlextimeBalance = FlextimeBalance()
    ): QuotaViewModel {
        val getMonthWorkDays = mockk<GetMonthWorkDaysUseCase>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>(relaxed = true)
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val calculateQuota = mockk<CalculateQuotaUseCase>(relaxed = true)
        val calculateFlextime = mockk<CalculateFlextimeUseCase>(relaxed = true)

        every { getSettings() } returns flowOf(settings)
        every { getMonthWorkDays(any()) } returns flowOf(monthDays)
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(yearDays)
        every { settingsRepository.getQuotaRules() } returns flowOf(quotaRules)
        every { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { settingsRepository.getQuotaRuleForMonth(any(), any()) } answers {
            val ym = firstArg<YearMonth>()
            quotaRules.find { it.validFrom == ym }
        }
        every { calculateQuota(any(), any(), any(), any(), any(), any()) } returns quotaResult
        every { calculateFlextime(any(), any(), any()) } returns flextimeResult
        every { calculateFlextime(any(), any(), any(), any()) } returns flextimeResult

        return QuotaViewModel(
            getMonthWorkDays = getMonthWorkDays,
            getSettings = getSettings,
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            calculateQuota = calculateQuota,
            calculateFlextime = calculateFlextime
        )
    }

    Given("ein Nutzer mit konfigurierten Quotenregeln für den aktuellen Monat") {
        val currentMonth = YearMonth.now()
        val customRule = QuotaRule(
            id = 1L,
            validFrom = currentMonth,
            officeQuotaPercent = 50,
            officeQuotaMinDays = 10
        )
        val expectedQuota = QuotaStatus(
            officeMinutes = 2400,
            homeOfficeMinutes = 2400,
            officeDays = 5,
            homeOfficeDays = 5,
            officePercent = 50.0,
            percentQuotaMet = true,
            daysQuotaMet = false
        )
        val expectedFlextime = FlextimeBalance(
            totalMinutes = 180,
            earnedMinutes = 4980,
            targetMinutes = 4800
        )

        val viewModel = setupQuotaViewModel(
            quotaRules = listOf(customRule),
            quotaResult = expectedQuota,
            flextimeResult = expectedFlextime
        )

        When("der Quota-State aggregiert wird") {
            Then("entsprechen effectiveQuotaPercent und effectiveQuotaMinDays der Monatsregel") {
                viewModel.uiState.value.effectiveQuotaPercent shouldBe 50
                viewModel.uiState.value.effectiveQuotaMinDays shouldBe 10
            }

            Then("ist der QuotaStatus mit den berechneten Quoten befüllt") {
                val stateQuota = viewModel.uiState.value.quotaStatus
                stateQuota.officePercent shouldBe 50.0
                stateQuota.percentQuotaMet.shouldBeTrue()
                stateQuota.officeDays shouldBe 5
            }

            Then("enthält flextimeBalance den kumulierten Jahressaldo") {
                viewModel.uiState.value.flextimeBalance.totalMinutes shouldBe 180
            }
        }
    }

    Given("ein Arbeitnehmer mit Urlaubsanspruch und genommenen bzw. geplanten Tagen") {
        val currentYear = LocalDate.now().year
        val settings = Settings(
            annualVacationDays = 30,
            carryOverVacationDays = 2,
            specialVacationDays = 5
        )

        val usedVacationDays = (1..5).map { day ->
            WorkDay(
                id = day.toLong(),
                date = LocalDate.of(currentYear, 2, day),
                location = WorkLocation.HOME_OFFICE,
                dayType = DayType.VACATION,
                isPlanned = false
            )
        }
        val plannedVacationDays = (10..12).map { day ->
            WorkDay(
                id = (100 + day).toLong(),
                date = LocalDate.of(currentYear, 7, day),
                location = WorkLocation.HOME_OFFICE,
                dayType = DayType.VACATION,
                isPlanned = true
            )
        }
        val usedSpecialDay = WorkDay(
            id = 200L,
            date = LocalDate.of(currentYear, 3, 1),
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.SPECIAL_VACATION,
            isPlanned = false
        )
        val plannedSpecialDay = WorkDay(
            id = 201L,
            date = LocalDate.of(currentYear, 8, 1),
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.SPECIAL_VACATION,
            isPlanned = true
        )

        val yearDays = usedVacationDays + plannedVacationDays + listOf(usedSpecialDay, plannedSpecialDay)

        val viewModel = setupQuotaViewModel(
            settings = settings,
            yearDays = yearDays
        )

        When("die Urlaubsstatistik geladen ist") {
            Then("sind Jahresurlaub und Übertrag korrekt übernommen") {
                val vacationInfo = viewModel.uiState.value.vacationInfo
                vacationInfo.annualDays shouldBe 30
                vacationInfo.carryOverDays shouldBe 2
            }

            Then("werden genommene und geplante Urlaubstage unterschieden") {
                val vacationInfo = viewModel.uiState.value.vacationInfo
                vacationInfo.usedVacationDays shouldBe 5
                vacationInfo.plannedVacationDays shouldBe 3
            }

            Then("beträgt der Resturlaub genau 27 Tage (30 + 2 - 5)") {
                viewModel.uiState.value.vacationInfo.remainingVacationDays shouldBe 27
            }

            Then("ist die Sonderurlaubs-Statistik korrekt berechnet") {
                val vacationInfo = viewModel.uiState.value.vacationInfo
                vacationInfo.specialDays shouldBe 5
                vacationInfo.usedSpecialDays shouldBe 1
                vacationInfo.plannedSpecialDays shouldBe 1
                vacationInfo.remainingSpecialDays shouldBe 4
            }
        }
    }

    Given("ein Arbeitnehmer mit erfassten Krankentagen im laufenden Jahr") {
        val currentYear = LocalDate.now().year
        val actualSickDays = (1..4).map { day ->
            WorkDay(
                id = day.toLong(),
                date = LocalDate.of(currentYear, 1, 10 + day),
                location = WorkLocation.HOME_OFFICE,
                dayType = DayType.SICK_DAY,
                isPlanned = false
            )
        }
        val plannedSickDay = WorkDay(
            id = 99L,
            date = LocalDate.of(currentYear, 11, 1),
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.SICK_DAY,
            isPlanned = true
        )

        val viewModel = setupQuotaViewModel(
            yearDays = actualSickDays + listOf(plannedSickDay)
        )

        When("die Krankentage ausgewertet werden") {
            Then("werden nur tatsächliche, nicht geplante Krankheitstage gezählt") {
                viewModel.uiState.value.sickDays shouldBe 4
            }
        }
    }
})
