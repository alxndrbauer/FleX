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
import com.flex.support.TestDates
import com.flex.support.fixedClock
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
        settingsRepository: SettingsRepository = mockk(relaxed = true),
        settings: Settings = Settings(),
        yearWorkDays: Map<Int, List<WorkDay>> = emptyMap(),
        flextimeBalance: ((List<WorkDay>) -> FlextimeBalance)? = null
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

        every { getSettings() } returns flowOf(settings)
        every { settingsRepository.getQuotaRules() } returns flowOf(emptyList())
        every { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { settingsRepository.getQuotaRuleForMonth(any(), any()) } returns null
        every { settingsRepository.getWorkTimeRuleForDate(any(), any()) } returns null
        every { workDayRepository.getWorkDaysForYear(any()) } answers { flowOf(yearWorkDays[firstArg<Int>()] ?: emptyList()) }
        every { calculateDayWorkTime(any()) } returns DayWorkTimeResult(0, 0, 0, false)
        every { calculateDayWorkTime(any(), any()) } returns DayWorkTimeResult(0, 0, 0, false)
        every { calculateFlextime(any(), any(), any(), any()) } answers {
            flextimeBalance?.invoke(firstArg()) ?: FlextimeBalance()
        }
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
            checkTimeBlockOverlap = checkTimeBlockOverlap,
            clock = fixedClock()
        )
        return Pair(workDayRepository, vm)
    }

    Given("ein MonthViewModel für den aktuellen Monat") {
        val currentMonth = YearMonth.from(TestDates.FRIDAY)
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
        val currentMonth = YearMonth.from(TestDates.FRIDAY)
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
        val currentMonth = YearMonth.from(TestDates.FRIDAY)
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
        val targetDate = YearMonth.from(TestDates.FRIDAY).atDay(14)
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

    Given("das Speichern eines Überstundentags im Monatsdialog") {
        val targetDate = YearMonth.from(TestDates.FRIDAY).atDay(14)
        val existingBlock = TimeBlock(
            id = 99L,
            workDayId = 7L,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(17, 0)
        )

        fun setupDay(repo: WorkDayRepository, dayType: DayType) {
            coEvery { repo.getWorkDay(targetDate) } returns flowOf(
                WorkDay(
                    id = 7L,
                    date = targetDate,
                    location = WorkLocation.OFFICE,
                    dayType = dayType,
                    timeBlocks = listOf(existingBlock)
                )
            )
            coEvery { repo.saveWorkDay(any()) } returns 7L
        }

        When("ohne Überstundenguthaben ein Überstundentag gespeichert wird") {
            val repo = mockk<WorkDayRepository>(relaxed = true)
            val (_, viewModel) = setupMonthViewModel(workDayRepository = repo)
            setupDay(repo, DayType.WORK)
            viewModel.selectDay(targetDate)

            viewModel.saveDay(targetDate, DayType.OVERTIME_DAY, null, emptyList())

            Then("wird der Tag nicht gespeichert") {
                coVerify(exactly = 0) { repo.saveWorkDay(any()) }
            }

            Then("bleibt der Bearbeitungsmodus offen") {
                viewModel.uiState.value.editingDay.shouldNotBeNull()
            }
        }

        When("ein bestehender Überstundentag ohne Guthaben erneut gespeichert wird") {
            val repo = mockk<WorkDayRepository>(relaxed = true)
            val (_, viewModel) = setupMonthViewModel(workDayRepository = repo)
            setupDay(repo, DayType.OVERTIME_DAY)
            viewModel.selectDay(targetDate)

            viewModel.saveDay(targetDate, DayType.OVERTIME_DAY, "Notiz ergänzt", emptyList())

            Then("wird der Tag mit dem neuen Wert gespeichert") {
                val daySlot = slot<WorkDay>()
                coVerify { repo.saveWorkDay(capture(daySlot)) }
                daySlot.captured.dayType shouldBe DayType.OVERTIME_DAY
                daySlot.captured.note shouldBe "Notiz ergänzt"
            }
        }

        When("mit Überstundenguthaben vor dem Tag ein Überstundentag gespeichert wird") {
            val repo = mockk<WorkDayRepository>(relaxed = true)
            val (_, viewModel) = setupMonthViewModel(
                workDayRepository = repo,
                flextimeBalance = { days ->
                    if (days.size <= 1) FlextimeBalance(overtimeMinutes = 600) else FlextimeBalance()
                }
            )
            setupDay(repo, DayType.WORK)
            viewModel.selectDay(targetDate)

            Then("steht das Guthaben vor dem Tag im UI-State") {
                viewModel.uiState.value.overtimeMinutesBefore(targetDate) shouldBe 600
            }

            viewModel.saveDay(targetDate, DayType.OVERTIME_DAY, null, emptyList())

            Then("wird der Tag gespeichert") {
                val daySlot = slot<WorkDay>()
                coVerify { repo.saveWorkDay(capture(daySlot)) }
                daySlot.captured.dayType shouldBe DayType.OVERTIME_DAY
            }
        }

        When("das Guthaben vor dem Tag bereits aufgebraucht ist") {
            val repo = mockk<WorkDayRepository>(relaxed = true)
            val month = YearMonth.from(TestDates.FRIDAY)
            val daysBefore = (1..13).map {
                WorkDay(date = month.atDay(it), location = WorkLocation.HOME_OFFICE, dayType = DayType.WORK)
            }
            val (_, viewModel) = setupMonthViewModel(
                customDays = mapOf(month to daysBefore),
                workDayRepository = repo,
                flextimeBalance = { days ->
                    if (days.isEmpty()) FlextimeBalance(overtimeMinutes = 600)
                    else FlextimeBalance(overtimeMinutes = 600 - days.first().date.dayOfMonth * 100L)
                }
            )
            setupDay(repo, DayType.WORK)
            viewModel.selectDay(targetDate)

            Then("wird das verbrauchte Guthaben vor dem Tag gemeldet") {
                viewModel.uiState.value.overtimeMinutesBefore(targetDate) shouldBe -700
            }

            viewModel.saveDay(targetDate, DayType.OVERTIME_DAY, null, emptyList())

            Then("wird der Tag nicht gespeichert") {
                coVerify(exactly = 0) { repo.saveWorkDay(any()) }
            }
        }

        When("der Vortag das Guthaben vollständig aufbraucht") {
            val repo = mockk<WorkDayRepository>(relaxed = true)
            val month = YearMonth.from(TestDates.FRIDAY)
            val days = (1..14).map {
                WorkDay(date = month.atDay(it), location = WorkLocation.HOME_OFFICE, dayType = DayType.WORK)
            }
            val (_, viewModel) = setupMonthViewModel(
                customDays = mapOf(month to days),
                workDayRepository = repo,
                flextimeBalance = { workedDays ->
                    when {
                        workedDays.isEmpty() -> FlextimeBalance(overtimeMinutes = 600)
                        workedDays.first().date.dayOfMonth == 13 -> FlextimeBalance(overtimeMinutes = 0)
                        else -> FlextimeBalance(overtimeMinutes = 600)
                    }
                }
            )
            setupDay(repo, DayType.WORK)
            viewModel.selectDay(targetDate)

            Then("ist vor dem Tag kein Guthaben mehr verfügbar") {
                viewModel.uiState.value.overtimeMinutesBefore(targetDate) shouldBe 0
            }

            viewModel.saveDay(targetDate, DayType.OVERTIME_DAY, null, emptyList())

            Then("wird der Tag nicht gespeichert") {
                coVerify(exactly = 0) { repo.saveWorkDay(any()) }
            }
        }

        When("der Tag kein Tagessoll hat (Samstag)") {
            val repo = mockk<WorkDayRepository>(relaxed = true)
            val saturday = LocalDate.of(2026, 10, 10)
            val (_, viewModel) = setupMonthViewModel(
                workDayRepository = repo,
                flextimeBalance = { FlextimeBalance(overtimeMinutes = 600) }
            )
            coEvery { repo.getWorkDay(saturday) } returns flowOf(
                WorkDay(id = 9L, date = saturday, location = WorkLocation.HOME_OFFICE, dayType = DayType.WORK)
            )
            coEvery { repo.saveWorkDay(any()) } returns 9L
            viewModel.selectDay(saturday)

            Then("wird das Guthaben vor dem Tag gemeldet") {
                viewModel.uiState.value.overtimeMinutesBefore(saturday) shouldBe 600
            }

            viewModel.saveDay(saturday, DayType.OVERTIME_DAY, null, emptyList())

            Then("wird der Tag nicht gespeichert") {
                coVerify(exactly = 0) { repo.saveWorkDay(any()) }
            }
        }
    }

    Given("der Monatsanfang liegt im Januar des Folgejahres") {
        val january = YearMonth.of(2027, 1)
        val decemberDay = WorkDay(
            date = LocalDate.of(2026, 12, 15),
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.WORK
        )
        val (_, viewModel) = setupMonthViewModel(
            settings = Settings(settingsYear = 2026),
            yearWorkDays = mapOf(2026 to listOf(decemberDay)),
            flextimeBalance = { days ->
                if (days.any { it.date == decemberDay.date }) FlextimeBalance(overtimeMinutes = 600)
                else FlextimeBalance()
            }
        )
        viewModel.selectMonth(january)

        Then("zählt der Dezember des Vorjahres zum Monatsanfang") {
            viewModel.uiState.value.overtimeMinutesAtMonthStart shouldBe 600
        }

        Then("ist der Überstundentag im Januar verfügbar") {
            viewModel.uiState.value.overtimeMinutesBefore(january.atDay(4)) shouldBe 600
        }
    }

    Given("der Monatsanfang liegt im Februar des Folgejahres") {
        val february = YearMonth.of(2027, 2)
        val decemberDay = WorkDay(
            date = LocalDate.of(2026, 12, 15),
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.WORK
        )
        val (_, viewModel) = setupMonthViewModel(
            settings = Settings(settingsYear = 2026),
            yearWorkDays = mapOf(2026 to listOf(decemberDay)),
            flextimeBalance = { days ->
                if (days.any { it.date == decemberDay.date }) FlextimeBalance(overtimeMinutes = 600)
                else FlextimeBalance()
            }
        )
        viewModel.selectMonth(february)

        Then("zählt der Dezember des Vorjahres zum Monatsanfang") {
            viewModel.uiState.value.overtimeMinutesAtMonthStart shouldBe 600
        }

        Then("ist der Überstundentag im Februar verfügbar") {
            viewModel.uiState.value.overtimeMinutesBefore(february.atDay(4)) shouldBe 600
        }
    }

    Given("ein MonthViewModel beim Navigieren in ein abgeschlossenes Vorjahr mit YearClosing-Snapshot") {
        val realCalc = CalculateFlextimeUseCase(CalculateDayWorkTimeUseCase())
        val settings2026 = Settings(
            dailyWorkMinutes = 420,
            settingsYear = 2026,
            initialFlextimeMinutes = 360,
            specialVacationPaidOut = false
        )
        val closing2025 = com.flex.domain.model.YearClosing(
            year = 2025,
            annualVacationDays = 30,
            carryOverVacationDays = 4,
            specialVacationDays = 5,
            specialVacationPaidOut = true,
            startFlextimeMinutes = 60,
            startOvertimeMinutes = 0,
            endFlextimeMinutes = 360,
            endOvertimeMinutes = 0,
            usedVacationDays = 20
        )
        val day2025 = WorkDay(
            id = 1L,
            date = LocalDate.of(2025, 3, 3),
            dayType = DayType.BUSINESS_TRIP,
            location = WorkLocation.OFFICE,
            isPlanned = false,
            timeBlocks = listOf(
                TimeBlock(
                    workDayId = 1L,
                    startTime = java.time.LocalTime.of(8, 0),
                    endTime = java.time.LocalTime.of(20, 0),
                    isDuration = true
                )
            )
        )

        val getMonthWorkDays = mockk<GetMonthWorkDaysUseCase>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>(relaxed = true)
        val workDayRepo = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepo = mockk<SettingsRepository>(relaxed = true)
        val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
        val calculateQuota = mockk<CalculateQuotaUseCase>(relaxed = true)
        val dataChangeEventBus = mockk<DataChangeEventBus>(relaxed = true)
        val prepareExportData = mockk<PrepareExportDataUseCase>(relaxed = true)
        val exportService = mockk<ExportService>(relaxed = true)
        val checkBreakViolation = mockk<CheckBreakViolationUseCase>(relaxed = true)
        val buildPrognosisDays = mockk<BuildPrognosisDaysUseCase>(relaxed = true)

        every { getSettings() } returns flowOf(settings2026)
        every { settingsRepo.getYearClosings() } returns flowOf(listOf(closing2025))
        every { settingsRepo.getQuotaRules() } returns flowOf(emptyList())
        every { settingsRepo.getWorkTimeRules() } returns flowOf(emptyList())
        every { getMonthWorkDays(YearMonth.of(2025, 3)) } returns flowOf(listOf(day2025))
        every { getMonthWorkDays(match { it != YearMonth.of(2025, 3) }) } returns flowOf(emptyList())
        every { workDayRepo.getWorkDaysForYear(2025) } returns flowOf(listOf(day2025))
        every { workDayRepo.getWorkDaysForYear(match { it != 2025 }) } returns flowOf(emptyList())
        every { buildPrognosisDays(any(), any(), any(), any()) } answers { secondArg() }
        every { calculateQuota(any(), any(), any(), any(), any(), any()) } returns QuotaStatus()
        every { checkBreakViolation(any(), any()) } returns com.flex.domain.model.BreakCheckResult(emptyList(), skipped = false)

        val vm = MonthViewModel(
            getMonthWorkDays, getSettings, workDayRepo, settingsRepo,
            calculateDayWorkTime, calculateQuota, realCalc,
            dataChangeEventBus, prepareExportData, exportService,
            checkBreakViolation, buildPrognosisDays,
            clock = fixedClock()
        )

        When("der Monat März 2025 ausgewählt wird") {
            vm.selectMonth(YearMonth.of(2025, 3))

            Then("beträgt die Prognose-Gleitzeit 360 Min ohne Doppelzählung und isSpecialVacationPaidOut ist true") {
                vm.uiState.value.prognosisFlextime.totalMinutes shouldBe 360L
                vm.uiState.value.isSpecialVacationPaidOut shouldBe true
            }
        }
    }
})
