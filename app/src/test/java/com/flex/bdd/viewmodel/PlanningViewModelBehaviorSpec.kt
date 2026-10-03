package com.flex.bdd.viewmodel

import com.flex.domain.events.UndoEvent
import com.flex.domain.model.DayType
import com.flex.domain.model.FlextimeBalance
import com.flex.domain.model.PublicHolidays
import com.flex.domain.model.QuotaStatus
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.BuildPrognosisDaysUseCase
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.DayWorkTimeResult
import com.flex.domain.usecase.GetMonthWorkDaysUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.ui.planning.PlanType
import com.flex.ui.planning.PlanningViewModel
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class PlanningViewModelBehaviorSpec : BehaviorSpec({

    val testDispatcher = UnconfinedTestDispatcher()

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    fun findFirstWorkday(month: YearMonth): LocalDate {
        return (1..28)
            .map { month.atDay(it) }
            .first { it.dayOfWeek !in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) && !PublicHolidays.isHoliday(it) }
    }

    fun setupPlanningViewModel(
        workDayRepository: WorkDayRepository = mockk(relaxed = true),
        settingsRepository: SettingsRepository = mockk(relaxed = true),
        monthWorkDays: List<WorkDay> = emptyList()
    ): Pair<WorkDayRepository, PlanningViewModel> {
        val getMonthWorkDays = mockk<GetMonthWorkDaysUseCase>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>(relaxed = true)
        val calculateDayWorkTime = mockk<CalculateDayWorkTimeUseCase>(relaxed = true)
        val calculateQuota = mockk<CalculateQuotaUseCase>(relaxed = true)
        val calculateFlextime = mockk<CalculateFlextimeUseCase>(relaxed = true)
        val buildPrognosisDays = mockk<BuildPrognosisDaysUseCase>(relaxed = true)

        every { getSettings() } returns flowOf(Settings())
        every { settingsRepository.getQuotaRules() } returns flowOf(emptyList())
        every { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { settingsRepository.getQuotaRuleForMonth(any(), any()) } returns null
        every { settingsRepository.getWorkTimeRuleForDate(any(), any()) } returns null
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())
        every { calculateDayWorkTime(any()) } returns DayWorkTimeResult(0, 0, 0, false)
        every { calculateDayWorkTime(any(), any()) } returns DayWorkTimeResult(0, 0, 0, false)
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance(totalMinutes = 120)
        every { calculateQuota(any(), any(), any(), any(), any(), any()) } returns QuotaStatus()
        every { buildPrognosisDays(any(), any(), any(), any()) } answers { secondArg() }
        every { getMonthWorkDays(any()) } returns flowOf(monthWorkDays)

        val vm = PlanningViewModel(
            getMonthWorkDays = getMonthWorkDays,
            getSettings = getSettings,
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            calculateDayWorkTime = calculateDayWorkTime,
            calculateQuota = calculateQuota,
            calculateFlextime = calculateFlextime,
            buildPrognosisDays = buildPrognosisDays
        )
        return Pair(workDayRepository, vm)
    }

    Given("ein initialisiertes PlanningViewModel für den Folgemonat") {
        val nextMonth = YearMonth.now().plusMonths(1)
        val plannedWorkDay = WorkDay(
            id = 1L,
            date = nextMonth.atDay(10),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = true
        )

        val getMonthWorkDays = mockk<GetMonthWorkDaysUseCase>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>(relaxed = true)
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val calculateDayWorkTime = mockk<CalculateDayWorkTimeUseCase>(relaxed = true)
        val calculateQuota = mockk<CalculateQuotaUseCase>(relaxed = true)
        val calculateFlextime = mockk<CalculateFlextimeUseCase>(relaxed = true)
        val buildPrognosisDays = mockk<BuildPrognosisDaysUseCase>(relaxed = true)

        val quotaExpected = QuotaStatus(
            officeDays = 8,
            homeOfficeDays = 12,
            officePercent = 40.0,
            percentQuotaMet = true,
            daysQuotaMet = true
        )

        every { getSettings() } returns flowOf(Settings())
        every { settingsRepository.getQuotaRules() } returns flowOf(emptyList())
        every { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { settingsRepository.getQuotaRuleForMonth(any(), any()) } returns null
        every { settingsRepository.getWorkTimeRuleForDate(any(), any()) } returns null
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())
        every { calculateDayWorkTime(any()) } returns DayWorkTimeResult(480, 480, 0, false)
        every { calculateDayWorkTime(any(), any()) } returns DayWorkTimeResult(480, 480, 0, false)
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance(totalMinutes = 120)
        every { calculateQuota(any(), any(), any(), any(), any(), any()) } returns quotaExpected
        every { buildPrognosisDays(any(), any(), any(), any()) } answers { secondArg() }
        every { getMonthWorkDays(nextMonth) } returns flowOf(listOf(plannedWorkDay))
        every { getMonthWorkDays(match { it != nextMonth }) } returns flowOf(emptyList())

        val viewModel = PlanningViewModel(
            getMonthWorkDays, getSettings, workDayRepository, settingsRepository,
            calculateDayWorkTime, calculateQuota, calculateFlextime, buildPrognosisDays
        )

        When("der State geladen ist") {
            Then("zeigt yearMonth auf den nächsten Monat") {
                viewModel.uiState.value.yearMonth shouldBe nextMonth
            }

            Then("sind die geplanten Tage und die Quotenprognose geladen") {
                viewModel.uiState.value.workDays shouldHaveSize 1
                viewModel.uiState.value.workDays.first().isPlanned.shouldBeTrue()
                viewModel.uiState.value.quotaStatus.officePercent shouldBe 40.0
                viewModel.uiState.value.quotaStatus.percentQuotaMet.shouldBeTrue()
            }

            Then("ist die Gleitzeitprognose vorhanden") {
                viewModel.uiState.value.flextimeBalance.totalMinutes shouldBe 120
            }
        }
    }

    Given("das Planen eines Bürotags (OFFICE)") {
        val nextMonth = YearMonth.now().plusMonths(1)
        val dateOffice = findFirstWorkday(nextMonth)
        val (workDayRepository, viewModel) = setupPlanningViewModel()
        coEvery { workDayRepository.saveWorkDay(any()) } returns 101L

        When("planDay mit PlanType.OFFICE aufgerufen wird") {
            viewModel.setSelectedPlanType(PlanType.OFFICE)
            viewModel.planDay(dateOffice)

            Then("wird ein geplanter WorkDay mit OFFICE und ein TimeBlock gespeichert") {
                val wdSlot = slot<WorkDay>()
                coVerify { workDayRepository.saveWorkDay(capture(wdSlot)) }
                wdSlot.captured.date shouldBe dateOffice
                wdSlot.captured.location shouldBe WorkLocation.OFFICE
                wdSlot.captured.dayType shouldBe DayType.WORK
                wdSlot.captured.isPlanned.shouldBeTrue()

                val tbSlot = slot<TimeBlock>()
                coVerify { workDayRepository.saveTimeBlock(capture(tbSlot)) }
                tbSlot.captured.location shouldBe WorkLocation.OFFICE
                tbSlot.captured.isDuration.shouldBeTrue()
            }
        }
    }

    Given("das Planen eines Home-Office-Tags (HOME_OFFICE)") {
        val nextMonth = YearMonth.now().plusMonths(1)
        val dateHomeOffice = findFirstWorkday(nextMonth)
        val (workDayRepository, viewModel) = setupPlanningViewModel()
        coEvery { workDayRepository.saveWorkDay(any()) } returns 102L

        When("planDay mit PlanType.HOME_OFFICE aufgerufen wird") {
            viewModel.setSelectedPlanType(PlanType.HOME_OFFICE)
            viewModel.planDay(dateHomeOffice)

            Then("wird ein geplanter WorkDay mit HOME_OFFICE und ein TimeBlock gespeichert") {
                val wdSlot = slot<WorkDay>()
                coVerify { workDayRepository.saveWorkDay(capture(wdSlot)) }
                wdSlot.captured.date shouldBe dateHomeOffice
                wdSlot.captured.location shouldBe WorkLocation.HOME_OFFICE
                wdSlot.captured.dayType shouldBe DayType.WORK
                wdSlot.captured.isPlanned.shouldBeTrue()

                val tbSlot = slot<TimeBlock>()
                coVerify { workDayRepository.saveTimeBlock(capture(tbSlot)) }
                tbSlot.captured.location shouldBe WorkLocation.HOME_OFFICE
            }
        }
    }

    Given("das Planen eines Urlaubstags (VACATION)") {
        val nextMonth = YearMonth.now().plusMonths(1)
        val dateVacation = findFirstWorkday(nextMonth)
        val (workDayRepository, viewModel) = setupPlanningViewModel()
        coEvery { workDayRepository.saveWorkDay(any()) } returns 103L

        When("planDay mit PlanType.VACATION aufgerufen wird") {
            viewModel.setSelectedPlanType(PlanType.VACATION)
            viewModel.planDay(dateVacation)

            Then("wird ein geplanter WorkDay mit DayType VACATION ohne TimeBlock gespeichert") {
                val wdSlot = slot<WorkDay>()
                coVerify { workDayRepository.saveWorkDay(capture(wdSlot)) }
                wdSlot.captured.date shouldBe dateVacation
                wdSlot.captured.dayType shouldBe DayType.VACATION
                wdSlot.captured.isPlanned.shouldBeTrue()

                coVerify(exactly = 0) { workDayRepository.saveTimeBlock(any()) }
            }
        }
    }

    Given("das Entfernen eines geplanten Tages") {
        val nextMonth = YearMonth.now().plusMonths(1)
        val plannedDate = findFirstWorkday(nextMonth)
        val plannedWorkDay = WorkDay(
            id = 50L,
            date = plannedDate,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = true
        )

        val workDaysFlow = MutableStateFlow(listOf(plannedWorkDay))
        val getMonthWorkDays = mockk<GetMonthWorkDaysUseCase>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>(relaxed = true)
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val calculateDayWorkTime = mockk<CalculateDayWorkTimeUseCase>(relaxed = true)
        val calculateQuota = mockk<CalculateQuotaUseCase>(relaxed = true)
        val calculateFlextime = mockk<CalculateFlextimeUseCase>(relaxed = true)
        val buildPrognosisDays = mockk<BuildPrognosisDaysUseCase>(relaxed = true)

        every { getSettings() } returns flowOf(Settings())
        every { settingsRepository.getQuotaRules() } returns flowOf(emptyList())
        every { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { settingsRepository.getQuotaRuleForMonth(any(), any()) } returns null
        every { settingsRepository.getWorkTimeRuleForDate(any(), any()) } returns null
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())
        every { calculateDayWorkTime(any()) } returns DayWorkTimeResult(0, 0, 0, false)
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance()
        every { calculateQuota(any(), any(), any(), any(), any(), any()) } returns QuotaStatus()
        every { buildPrognosisDays(any(), any(), any(), any()) } answers { secondArg() }
        every { getMonthWorkDays(nextMonth) } returns workDaysFlow

        val viewModel = PlanningViewModel(
            getMonthWorkDays, getSettings, workDayRepository, settingsRepository,
            calculateDayWorkTime, calculateQuota, calculateFlextime, buildPrognosisDays
        )

        When("removePlan für diesen Tag aufgerufen wird") {
            var receivedUndoEvent: UndoEvent? = null
            val collectJob = launch(UnconfinedTestDispatcher()) {
                viewModel.undoEvent.collect { receivedUndoEvent = it }
            }

            viewModel.removePlan(plannedDate)

            Then("wird der geplante WorkDay aus dem Repository gelöscht") {
                coVerify { workDayRepository.deleteWorkDay(plannedWorkDay) }
            }

            Then("wird ein UndoEvent mit 'Plan entfernt' emittiert") {
                receivedUndoEvent.shouldNotBeNull()
                receivedUndoEvent.message shouldBe "Plan entfernt"
            }

            collectJob.cancel()
        }
    }

    Given("das Anwenden des Standardmonats") {
        val nextMonth = YearMonth.now().plusMonths(1)

        val getMonthWorkDays = mockk<GetMonthWorkDaysUseCase>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>(relaxed = true)
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val calculateDayWorkTime = mockk<CalculateDayWorkTimeUseCase>(relaxed = true)
        val calculateQuota = mockk<CalculateQuotaUseCase>(relaxed = true)
        val calculateFlextime = mockk<CalculateFlextimeUseCase>(relaxed = true)
        val buildPrognosisDays = mockk<BuildPrognosisDaysUseCase>(relaxed = true)

        every { getSettings() } returns flowOf(Settings())
        every { settingsRepository.getQuotaRules() } returns flowOf(emptyList())
        every { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { settingsRepository.getQuotaRuleForMonth(any(), any()) } returns null
        every { settingsRepository.getWorkTimeRuleForDate(any(), any()) } returns null
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())
        every { calculateDayWorkTime(any()) } returns DayWorkTimeResult(0, 0, 0, false)
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance()
        every { calculateQuota(any(), any(), any(), any(), any(), any()) } returns QuotaStatus()
        every { buildPrognosisDays(any(), any(), any(), any()) } answers { secondArg() }
        every { getMonthWorkDays(any()) } returns flowOf(emptyList())

        val savedWorkDays = mutableListOf<WorkDay>()
        coEvery { workDayRepository.saveWorkDay(capture(savedWorkDays)) } returns 1L

        val viewModel = PlanningViewModel(
            getMonthWorkDays, getSettings, workDayRepository, settingsRepository,
            calculateDayWorkTime, calculateQuota, calculateFlextime, buildPrognosisDays
        )

        When("applyStandardMonth mit PlanType OFFICE aufgerufen wird") {
            viewModel.applyStandardMonth(PlanType.OFFICE)

            Then("werden alle regulären Werktage des Monats als Büro geplant angelegt") {
                savedWorkDays.shouldHaveSize(
                    (1..nextMonth.lengthOfMonth())
                        .map { nextMonth.atDay(it) }
                        .count { it.dayOfWeek !in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) && !PublicHolidays.isHoliday(it) }
                )
                savedWorkDays.all { it.location == WorkLocation.OFFICE && it.isPlanned }.shouldBeTrue()
            }
        }
    }

    Given("die Monatsübersichten für Folgemonate") {
        val (workDayRepository, viewModel) = setupPlanningViewModel()

        When("der State initialisiert ist") {
            Then("enthält monthSummaries genau 12 Monatsübersichten ab aktuellem Monat") {
                val summaries = viewModel.uiState.value.monthSummaries
                summaries shouldHaveSize 12
                val currentMonth = YearMonth.now()
                summaries.first().yearMonth shouldBe currentMonth
                summaries.last().yearMonth shouldBe currentMonth.plusMonths(11)
            }
        }
    }
})
