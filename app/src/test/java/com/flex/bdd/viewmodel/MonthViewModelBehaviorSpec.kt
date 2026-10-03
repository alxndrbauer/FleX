package com.flex.bdd.viewmodel

import com.flex.data.export.ExportService
import com.flex.domain.events.DataChangeEventBus
import com.flex.domain.events.UndoEvent
import com.flex.domain.model.DayType
import com.flex.domain.model.FlextimeBalance
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
import com.flex.domain.usecase.CheckBreakViolationUseCase
import com.flex.domain.usecase.CheckTimeBlockOverlapUseCase
import com.flex.domain.usecase.DayWorkTimeResult
import com.flex.domain.usecase.GetMonthWorkDaysUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.domain.usecase.PrepareExportDataUseCase
import com.flex.ui.month.MonthViewModel
import com.flex.ui.month.TimeBlockInput
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class MonthViewModelBehaviorSpec : BehaviorSpec({

    val testDispatcher = UnconfinedTestDispatcher()

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    fun setupMonthViewModel(
        customDays: Map<YearMonth, List<WorkDay>> = emptyMap(),
        workDayRepository: WorkDayRepository = mockk(relaxed = true),
        settingsRepository: SettingsRepository = mockk(relaxed = true)
    ): Pair<WorkDayRepository, MonthViewModel> {
        val getMonthWorkDays = mockk<GetMonthWorkDaysUseCase>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>(relaxed = true)
        val calculateDayWorkTime = mockk<CalculateDayWorkTimeUseCase>(relaxed = true)
        val calculateQuota = mockk<CalculateQuotaUseCase>(relaxed = true)
        val calculateFlextime = mockk<CalculateFlextimeUseCase>(relaxed = true)
        val dataChangeEventBus = mockk<DataChangeEventBus>(relaxed = true)
        val prepareExportData = mockk<PrepareExportDataUseCase>(relaxed = true)
        val exportService = mockk<ExportService>(relaxed = true)
        val checkBreakViolation = mockk<CheckBreakViolationUseCase>(relaxed = true)
        val buildPrognosisDays = mockk<BuildPrognosisDaysUseCase>(relaxed = true)
        val checkTimeBlockOverlap = CheckTimeBlockOverlapUseCase()

        every { getSettings() } returns flowOf(Settings())
        every { settingsRepository.getQuotaRules() } returns flowOf(emptyList())
        every { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { settingsRepository.getQuotaRuleForMonth(any(), any()) } returns null
        every { settingsRepository.getWorkTimeRuleForDate(any(), any()) } returns null
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())
        every { calculateDayWorkTime(any()) } returns DayWorkTimeResult(0, 0, 0, false)
        every { calculateDayWorkTime(any(), any()) } returns DayWorkTimeResult(0, 0, 0, false)
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance()
        every { calculateQuota(any(), any(), any(), any(), any(), any()) } returns QuotaStatus()
        every { dataChangeEventBus.events } returns MutableSharedFlow()
        every { buildPrognosisDays(any(), any(), any(), any()) } answers { secondArg() }

        every { getMonthWorkDays(any()) } answers {
            val ym = firstArg<YearMonth>()
            flowOf(customDays[ym] ?: emptyList())
        }

        val vm = MonthViewModel(
            getMonthWorkDays = getMonthWorkDays,
            getSettings = getSettings,
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            calculateDayWorkTime = calculateDayWorkTime,
            calculateQuota = calculateQuota,
            calculateFlextime = calculateFlextime,
            dataChangeEventBus = dataChangeEventBus,
            prepareExportData = prepareExportData,
            exportService = exportService,
            checkBreakViolation = checkBreakViolation,
            buildPrognosisDays = buildPrognosisDays,
            checkTimeBlockOverlap = checkTimeBlockOverlap
        )
        return Pair(workDayRepository, vm)
    }

    Given("ein MonthViewModel für den aktuellen Monat") {
        val currentMonth = YearMonth.now()
        val day1 = WorkDay(
            id = 1L,
            date = currentMonth.atDay(3),
            location = WorkLocation.OFFICE,
            timeBlocks = listOf(
                TimeBlock(id = 10L, workDayId = 1L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 0))
            )
        )
        val monthDays = listOf(day1)
        val (_, viewModel) = setupMonthViewModel(customDays = mapOf(currentMonth to monthDays))

        When("der initiale State abgefragt wird") {
            Then("ist yearMonth auf den aktuellen Monat gesetzt") {
                viewModel.uiState.value.yearMonth shouldBe currentMonth
            }

            Then("sind die Tage des Monats geladen") {
                viewModel.uiState.value.workDays shouldContainExactly monthDays
            }
        }
    }

    Given("die Monatsnavigation") {
        val currentMonth = YearMonth.now()
        val prevMonth = currentMonth.minusMonths(1)
        val nextMonth = currentMonth.plusMonths(1)

        val prevDay = WorkDay(id = 2L, date = prevMonth.atDay(10), location = WorkLocation.HOME_OFFICE)
        val currentDay = WorkDay(id = 1L, date = currentMonth.atDay(15), location = WorkLocation.OFFICE)
        val nextDay = WorkDay(id = 3L, date = nextMonth.atDay(5), location = WorkLocation.OFFICE)

        val (_, viewModel) = setupMonthViewModel(
            customDays = mapOf(
                currentMonth to listOf(currentDay),
                prevMonth to listOf(prevDay),
                nextMonth to listOf(nextDay)
            )
        )

        When("previousMonth aufgerufen wird") {
            viewModel.previousMonth()

            Then("aktualisiert sich yearMonth auf den vorherigen Monat und lädt dessen Tage") {
                viewModel.uiState.value.yearMonth shouldBe prevMonth
                viewModel.uiState.value.workDays shouldContainExactly listOf(prevDay)
            }
        }

        When("anschließend zweimal nextMonth aufgerufen wird") {
            viewModel.nextMonth()
            viewModel.nextMonth()

            Then("aktualisiert sich yearMonth auf den Folgemonat und lädt dessen Tage") {
                viewModel.uiState.value.yearMonth shouldBe nextMonth
                viewModel.uiState.value.workDays shouldContainExactly listOf(nextDay)
            }
        }
    }

    Given("das Auswählen eines Tages") {
        val currentMonth = YearMonth.now()
        // Ein Werktag im Monat (Tag 15 ist meist kein Feiertag)
        val date = currentMonth.atDay(15)
        val existingBlock = TimeBlock(id = 101L, workDayId = 42L, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(17, 0))
        val existingDay = WorkDay(id = 42L, date = date, location = WorkLocation.OFFICE, timeBlocks = listOf(existingBlock))

        val repo = mockk<WorkDayRepository>(relaxed = true)
        coEvery { repo.getWorkDay(date) } returns flowOf(existingDay)

        val (_, viewModel) = setupMonthViewModel(workDayRepository = repo)

        When("selectDay mit einem bestehenden Tag aufgerufen wird") {
            viewModel.selectDay(date)

            Then("wird editingDay auf den gefundenen WorkDay gesetzt") {
                viewModel.uiState.value.editingDay.shouldNotBeNull()
                viewModel.uiState.value.editingDay?.id shouldBe 42L
                viewModel.uiState.value.editingDay?.date shouldBe date
            }

            Then("enthält editingTimeBlocks die zugehörigen Blöcke") {
                viewModel.uiState.value.editingTimeBlocks shouldContainExactly listOf(existingBlock)
            }
        }

        When("selectDay mit einem noch nicht gespeicherten Tag aufgerufen wird") {
            val emptyDate = currentMonth.atDay(16)
            coEvery { repo.getWorkDay(emptyDate) } returns flowOf(null)

            viewModel.selectDay(emptyDate)

            Then("wird editingDay mit einem neuen leeren WorkDay initialisiert") {
                viewModel.uiState.value.editingDay.shouldNotBeNull()
                viewModel.uiState.value.editingDay?.id shouldBe 0L
                viewModel.uiState.value.editingDay?.date shouldBe emptyDate
                viewModel.uiState.value.editingTimeBlocks.shouldHaveSize(0)
            }
        }
    }

    Given("das Bestätigen geplanter Tage") {
        val repo = mockk<WorkDayRepository>(relaxed = true)
        val (_, viewModel) = setupMonthViewModel(workDayRepository = repo)
        val targetMonth = viewModel.uiState.value.yearMonth

        When("confirmPlannedDays aufgerufen wird") {
            viewModel.confirmPlannedDays()

            Then("wird workDayRepository.confirmPlannedDays für den Monat aufgerufen") {
                coVerify { repo.confirmPlannedDays(targetMonth) }
            }
        }
    }

    Given("das Speichern und Löschen eines Tages") {
        val repo = mockk<WorkDayRepository>(relaxed = true)
        val (_, viewModel) = setupMonthViewModel(workDayRepository = repo)
        val targetDate = YearMonth.now().atDay(14)
        val existingBlock = TimeBlock(id = 99L, workDayId = 7L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0))
        val existingDay = WorkDay(id = 7L, date = targetDate, location = WorkLocation.OFFICE, timeBlocks = listOf(existingBlock))

        coEvery { repo.getWorkDay(targetDate) } returns flowOf(existingDay)
        coEvery { repo.saveWorkDay(any()) } returns 7L

        viewModel.selectDay(targetDate)

        When("saveDay mit neuen Zeitblöcken aufgerufen wird") {
            val newBlockInput = TimeBlockInput(
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(17, 30),
                location = WorkLocation.HOME_OFFICE,
                isDuration = false
            )
            viewModel.saveDay(
                date = targetDate,
                dayType = DayType.WORK,
                note = "Remote gearbeitet",
                timeBlocks = listOf(newBlockInput)
            )

            Then("wird der alte Block gelöscht") {
                coVerify { repo.deleteTimeBlock(existingBlock) }
            }

            Then("wird der WorkDay gespeichert") {
                val daySlot = slot<WorkDay>()
                coVerify { repo.saveWorkDay(capture(daySlot)) }
                daySlot.captured.date shouldBe targetDate
                daySlot.captured.note shouldBe "Remote gearbeitet"
                daySlot.captured.location shouldBe WorkLocation.HOME_OFFICE
            }

            Then("wird der neue TimeBlock gespeichert") {
                val blockSlot = slot<TimeBlock>()
                coVerify { repo.saveTimeBlock(capture(blockSlot)) }
                blockSlot.captured.startTime shouldBe LocalTime.of(9, 0)
                blockSlot.captured.endTime shouldBe LocalTime.of(17, 30)
                blockSlot.captured.location shouldBe WorkLocation.HOME_OFFICE
            }

            Then("wird der Bearbeitungsmodus beendet") {
                viewModel.uiState.value.editingDay.shouldBeNull()
                viewModel.uiState.value.editingTimeBlocks.shouldHaveSize(0)
            }
        }

        When("deleteDay aufgerufen wird") {
            var receivedUndoEvent: UndoEvent? = null
            val collectJob = launch(UnconfinedTestDispatcher()) {
                viewModel.undoEvent.collect { receivedUndoEvent = it }
            }

            viewModel.deleteDay(existingDay)

            Then("werden alle Blöcke und der WorkDay aus dem Repository gelöscht") {
                coVerify { repo.deleteTimeBlock(existingBlock) }
                coVerify { repo.deleteWorkDay(existingDay) }
            }

            Then("wird ein UndoEvent emittiert") {
                receivedUndoEvent.shouldNotBeNull()
                receivedUndoEvent.message shouldBe "Eintrag gelöscht"
            }

            collectJob.cancel()
        }
    }
})
