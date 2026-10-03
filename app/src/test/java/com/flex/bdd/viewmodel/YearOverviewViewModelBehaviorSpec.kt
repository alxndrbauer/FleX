package com.flex.bdd.viewmodel

import com.flex.domain.model.DayType
import com.flex.domain.model.PublicHolidays
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.ui.year.YearOverviewViewModel
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.Year

@OptIn(ExperimentalCoroutinesApi::class)
class YearOverviewViewModelBehaviorSpec : BehaviorSpec({

    val testDispatcher = UnconfinedTestDispatcher()

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    Given("ein initialisierter YearOverviewViewModel für das aktuelle Jahr") {
        val workDayRepository = mockk<WorkDayRepository>()
        val getSettings = mockk<GetSettingsUseCase>()
        val calculateDayWorkTime = CalculateDayWorkTimeUseCase()

        val currentYear = LocalDate.now().year
        val expectedDaysCount = Year.of(currentYear).length()

        val settings = Settings(
            id = 1L,
            dailyWorkMinutes = 450,
            annualVacationDays = 30
        )

        every { getSettings() } returns flowOf(settings)
        every { workDayRepository.getWorkDaysForYear(currentYear) } returns flowOf(emptyList())

        val viewModel = YearOverviewViewModel(
            workDayRepository = workDayRepository,
            getSettings = getSettings,
            calculateDayWorkTime = calculateDayWorkTime
        )

        When("der initiale Zustand geladen wird") {
            val state = viewModel.uiState.value

            Then("ist das ausgewählte Jahr das aktuelle Jahr") {
                state.year shouldBe currentYear
            }

            Then("werden alle 365 bzw. 366 Tage für das aktuelle Jahr als Heatmap-Einträge geladen") {
                state.heatmapEntries.shouldHaveSize(expectedDaysCount)
                state.heatmapEntries.shouldContainKey(LocalDate.of(currentYear, 1, 1))
                state.heatmapEntries.shouldContainKey(LocalDate.of(currentYear, 12, 31))
            }

            Then("entspricht die tägliche Sollarbeitszeit dem Wert aus den Einstellungen") {
                state.dailyWorkMinutes shouldBe 450
            }
        }
    }

    Given("verschiedene Arbeitstage und Feiertage im aktuellen Jahr") {
        val workDayRepository = mockk<WorkDayRepository>()
        val getSettings = mockk<GetSettingsUseCase>()
        val calculateDayWorkTime = CalculateDayWorkTimeUseCase()

        val currentYear = LocalDate.now().year
        val settings = Settings(dailyWorkMinutes = 426)

        val officeWorkDate = LocalDate.of(currentYear, 3, 10)
        val businessTripDate = LocalDate.of(currentYear, 5, 20)
        val plannedWorkDate = LocalDate.of(currentYear, 7, 15)
        val holidayDate = LocalDate.of(currentYear, 1, 1) // Neujahr

        val workDays = listOf(
            WorkDay(
                id = 1L,
                date = officeWorkDate,
                dayType = DayType.WORK,
                location = WorkLocation.OFFICE,
                timeBlocks = listOf(
                    TimeBlock(
                        id = 1L,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(16, 30), // 8.5h brutto -> 8h netto (30 min Pause) = 480 min
                        location = WorkLocation.OFFICE
                    )
                ),
                isPlanned = false
            ),
            WorkDay(
                id = 2L,
                date = businessTripDate,
                dayType = DayType.BUSINESS_TRIP,
                location = WorkLocation.OFFICE,
                timeBlocks = listOf(
                    TimeBlock(
                        id = 2L,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(17, 0), // 9h brutto -> 8.5h netto (30 min Pause) = 510 min
                        location = WorkLocation.OFFICE
                    )
                ),
                isPlanned = false
            ),
            WorkDay(
                id = 3L,
                date = plannedWorkDate,
                dayType = DayType.WORK,
                location = WorkLocation.HOME_OFFICE,
                timeBlocks = listOf(
                    TimeBlock(
                        id = 3L,
                        startTime = LocalTime.of(9, 0),
                        endTime = LocalTime.of(17, 0),
                        location = WorkLocation.HOME_OFFICE
                    )
                ),
                isPlanned = true // Geplante Tage dürfen nicht in die Heatmap einfliessen
            )
        )

        every { getSettings() } returns flowOf(settings)
        every { workDayRepository.getWorkDaysForYear(currentYear) } returns flowOf(workDays)

        val viewModel = YearOverviewViewModel(
            workDayRepository = workDayRepository,
            getSettings = getSettings,
            calculateDayWorkTime = calculateDayWorkTime
        )

        When("die DayHeatmapEntry-Einträge erzeugt werden") {
            val entries = viewModel.uiState.value.heatmapEntries

            Then("weist der Bürotag die korrekten Netto-Minuten und den Arbeitsstatus auf") {
                val officeEntry = entries[officeWorkDate]
                officeEntry.shouldNotBeNull()
                officeEntry.dayType shouldBe DayType.WORK
                officeEntry.location shouldBe WorkLocation.OFFICE
                officeEntry.netMinutes shouldBe 480L
                officeEntry.isPublicHoliday.shouldBeFalse()
                officeEntry.isPlanned.shouldBeFalse()
            }

            Then("wird der Dienstreisetag mit korrekter Dienstreise-Arbeitszeit erfasst") {
                val tripEntry = entries[businessTripDate]
                tripEntry.shouldNotBeNull()
                tripEntry.dayType shouldBe DayType.BUSINESS_TRIP
                tripEntry.netMinutes shouldBe 510L
                tripEntry.isPublicHoliday.shouldBeFalse()
            }

            Then("ist der Feiertag korrekt als Feiertag mit Name markiert") {
                val holidayEntry = entries[holidayDate]
                holidayEntry.shouldNotBeNull()
                holidayEntry.isPublicHoliday.shouldBeTrue()
                holidayEntry.holidayName shouldBe "Neujahr"
            }

            Then("wird ein geplanter Arbeitstag nicht als Ist-Arbeitstag in die Heatmap übernommen") {
                val plannedEntry = entries[plannedWorkDate]
                plannedEntry.shouldNotBeNull()
                plannedEntry.dayType.shouldBeNull()
                plannedEntry.netMinutes shouldBe 0L
            }

            Then("hat ein Tag ohne Buchung 0 Netto-Minuten und keinen Tagestyp") {
                val emptyDate = LocalDate.of(currentYear, 2, 15)
                val emptyEntry = entries[emptyDate]
                emptyEntry.shouldNotBeNull()
                emptyEntry.dayType.shouldBeNull()
                emptyEntry.netMinutes shouldBe 0L
            }
        }
    }

    Given("eine Navigation zwischen den Jahren") {
        val workDayRepository = mockk<WorkDayRepository>()
        val getSettings = mockk<GetSettingsUseCase>()
        val calculateDayWorkTime = CalculateDayWorkTimeUseCase()

        val currentYear = LocalDate.now().year
        val prevYear = currentYear - 1
        val nextYearVal = currentYear + 1

        val settings = Settings(dailyWorkMinutes = 426)
        every { getSettings() } returns flowOf(settings)
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())

        val viewModel = YearOverviewViewModel(
            workDayRepository = workDayRepository,
            getSettings = getSettings,
            calculateDayWorkTime = calculateDayWorkTime
        )

        When("previousYear() aufgerufen wird") {
            viewModel.previousYear()

            Then("wechselt der Zustand auf das Vorjahr") {
                viewModel.uiState.value.year shouldBe prevYear
            }

            Then("werden alle Tage des Vorjahres in der Heatmap geladen") {
                val expectedPrevYearDays = Year.of(prevYear).length()
                viewModel.uiState.value.heatmapEntries.shouldHaveSize(expectedPrevYearDays)
                viewModel.uiState.value.heatmapEntries.shouldContainKey(LocalDate.of(prevYear, 1, 1))
                viewModel.uiState.value.heatmapEntries.shouldContainKey(LocalDate.of(prevYear, 12, 31))
            }
        }

        When("anschliessend zweimal nextYear() aufgerufen wird") {
            viewModel.nextYear()
            viewModel.nextYear()

            Then("wechselt der Zustand auf das Folgejahr") {
                viewModel.uiState.value.year shouldBe nextYearVal
            }

            Then("werden alle Tage des Folgejahres in der Heatmap geladen") {
                val expectedNextYearDays = Year.of(nextYearVal).length()
                viewModel.uiState.value.heatmapEntries.shouldHaveSize(expectedNextYearDays)
                viewModel.uiState.value.heatmapEntries.shouldContainKey(LocalDate.of(nextYearVal, 1, 1))
                viewModel.uiState.value.heatmapEntries.shouldContainKey(LocalDate.of(nextYearVal, 12, 31))
            }
        }
    }

    Given("ein Arbeitsjahr mit verschiedenen Arbeitstagen für Jahressummen") {
        val workDayRepository = mockk<WorkDayRepository>()
        val getSettings = mockk<GetSettingsUseCase>()
        val calculateDayWorkTime = CalculateDayWorkTimeUseCase()

        val year = LocalDate.now().year
        val settings = Settings(dailyWorkMinutes = 426)

        // 5 reine Bürotage (ohne Zeitblöcke, location = OFFICE)
        val officeDays = (1..5).map { d ->
            WorkDay(
                id = d.toLong(),
                date = LocalDate.of(year, 1, d),
                dayType = DayType.WORK,
                location = WorkLocation.OFFICE,
                timeBlocks = emptyList(),
                isPlanned = false
            )
        }

        // 3 reine Home-Office Tage (ohne Zeitblöcke, location = HOME_OFFICE)
        val homeOfficeDays = (6..8).map { d ->
            WorkDay(
                id = d.toLong(),
                date = LocalDate.of(year, 1, d),
                dayType = DayType.WORK,
                location = WorkLocation.HOME_OFFICE,
                timeBlocks = emptyList(),
                isPlanned = false
            )
        }

        // 2 Mischtage: 4h Büro, 2h HO -> Büro überwiegt (4h >= 2h) -> zählt als Bürotag
        // Jeder Tag hat 6h Brutto = 360 min -> 360 - 30 (Pause) = 330 min netto
        val mixedOfficeDays = (9..10).map { d ->
            WorkDay(
                id = d.toLong(),
                date = LocalDate.of(year, 1, d),
                dayType = DayType.WORK,
                location = WorkLocation.OFFICE,
                timeBlocks = listOf(
                    TimeBlock(
                        id = (d * 10).toLong(),
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(12, 0),
                        location = WorkLocation.OFFICE
                    ),
                    TimeBlock(
                        id = (d * 10 + 1).toLong(),
                        startTime = LocalTime.of(13, 0),
                        endTime = LocalTime.of(15, 0),
                        location = WorkLocation.HOME_OFFICE
                    )
                ),
                isPlanned = false
            )
        }

        // 1 Mischtag: 2h Büro, 5h HO -> HO überwiegt (5h > 2h) -> zählt als HO-Tag
        // 7h Brutto = 420 min -> 420 - 30 = 390 min netto
        val mixedHoDay = WorkDay(
            id = 11L,
            date = LocalDate.of(year, 1, 11),
            dayType = DayType.WORK,
            location = WorkLocation.HOME_OFFICE,
            timeBlocks = listOf(
                TimeBlock(
                    id = 111L,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(10, 0),
                    location = WorkLocation.OFFICE
                ),
                TimeBlock(
                    id = 112L,
                    startTime = LocalTime.of(10, 30),
                    endTime = LocalTime.of(15, 30),
                    location = WorkLocation.HOME_OFFICE
                )
            ),
            isPlanned = false
        )

        // 20 Urlaubstage
        val vacationDays = (1..20).map { d ->
            WorkDay(
                id = (100 + d).toLong(),
                date = LocalDate.of(year, 2, d),
                dayType = DayType.VACATION,
                location = WorkLocation.HOME_OFFICE,
                isPlanned = false
            )
        }

        // 2 Sonderurlaubstage
        val specialVacationDays = (1..2).map { d ->
            WorkDay(
                id = (200 + d).toLong(),
                date = LocalDate.of(year, 3, d),
                dayType = DayType.SPECIAL_VACATION,
                location = WorkLocation.HOME_OFFICE,
                isPlanned = false
            )
        }

        // 4 Krankheitstage
        val sickDays = (1..4).map { d ->
            WorkDay(
                id = (300 + d).toLong(),
                date = LocalDate.of(year, 4, d),
                dayType = DayType.SICK_DAY,
                location = WorkLocation.HOME_OFFICE,
                isPlanned = false
            )
        }

        // 1 Gleitzeittag
        val flexDay = WorkDay(
            id = 401L,
            date = LocalDate.of(year, 5, 2),
            dayType = DayType.FLEX_DAY,
            location = WorkLocation.HOME_OFFICE,
            isPlanned = false
        )

        // 1 Samstag-Bonus-Tag mit 4 Stunden Arbeit = 240 min netto
        val saturdayBonusDay = WorkDay(
            id = 501L,
            date = LocalDate.of(year, 5, 10),
            dayType = DayType.SATURDAY_BONUS,
            location = WorkLocation.OFFICE,
            timeBlocks = listOf(
                TimeBlock(
                    id = 5011L,
                    startTime = LocalTime.of(9, 0),
                    endTime = LocalTime.of(13, 0),
                    location = WorkLocation.OFFICE
                )
            ),
            isPlanned = false
        )

        // 2 Dienstreisetage mit je 8h = 480 min netto
        val businessTripDays = (1..2).map { d ->
            WorkDay(
                id = (600 + d).toLong(),
                date = LocalDate.of(year, 6, d),
                dayType = DayType.BUSINESS_TRIP,
                location = WorkLocation.OFFICE,
                timeBlocks = listOf(
                    TimeBlock(
                        id = (6000 + d).toLong(),
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(16, 30),
                        location = WorkLocation.OFFICE
                    )
                ),
                isPlanned = false
            )
        }

        // 1 geplanter Urlaubstag (muss ignoriert werden)
        val plannedVacation = WorkDay(
            id = 999L,
            date = LocalDate.of(year, 7, 1),
            dayType = DayType.VACATION,
            location = WorkLocation.HOME_OFFICE,
            isPlanned = true
        )

        val allWorkDays = officeDays + homeOfficeDays + mixedOfficeDays + listOf(mixedHoDay) +
            vacationDays + specialVacationDays + sickDays + listOf(flexDay, saturdayBonusDay) +
            businessTripDays + listOf(plannedVacation)

        every { getSettings() } returns flowOf(settings)
        every { workDayRepository.getWorkDaysForYear(year) } returns flowOf(allWorkDays)

        val viewModel = YearOverviewViewModel(
            workDayRepository = workDayRepository,
            getSettings = getSettings,
            calculateDayWorkTime = calculateDayWorkTime
        )

        When("die Jahressummen ausgewertet werden") {
            val summary = viewModel.uiState.value.summary

            Then("ist das Summenjahr korrekt") {
                summary.year shouldBe year
            }

            Then("entspricht die Anzahl der reinen Arbeitstage den WORK-Tagen") {
                // 5 office + 3 ho + 2 mixedOffice + 1 mixedHo = 11 Arbeitstage
                summary.totalWorkDays shouldBe 11
            }

            Then("werden die Büro- und Home-Office-Tage korrekt gezählt") {
                // Bürotage: 5 reine + 2 Mischtage = 7
                // Home-Office: 3 reine + 1 Mischtag = 4
                summary.officeWorkDays shouldBe 7
                summary.homeOfficeWorkDays shouldBe 4
            }

            Then("werden Urlaubstage korrekt gezählt (ohne geplante Tage)") {
                summary.vacationDays shouldBe 20
            }

            Then("werden Sonderurlaubstage korrekt gezählt") {
                summary.specialVacationDays shouldBe 2
            }

            Then("werden Krankheitstage korrekt gezählt") {
                summary.sickDays shouldBe 4
            }

            Then("werden Gleitzeittage korrekt gezählt") {
                summary.flexDays shouldBe 1
            }

            Then("werden Samstag-Bonus-Tage korrekt gezählt") {
                summary.saturdayBonusDays shouldBe 1
            }

            Then("werden Dienstreisetage korrekt gezählt") {
                summary.businessTripDays shouldBe 2
            }

            Then("entspricht die Gesamtarbeitszeit der Summe aller Netto-Minuten aus WORK, Samstag und Dienstreise") {
                // WORK:
                // 5 officeDays (0 min da keine timeBlocks) = 0
                // 3 hoDays (0 min) = 0
                // 2 mixedOfficeDays: je 360 min (60 min Pause zwischen Blöcken) = 720 min
                // 1 mixedHoDay: 420 min (30 min Pause zwischen Blöcken) = 420 min
                // SATURDAY_BONUS:
                // 1 Saturday: 240 min
                // BUSINESS_TRIP:
                // 2 Trip: je 480 min = 960 min
                // Summe = 720 + 420 + 240 + 960 = 2340 min
                summary.totalWorkMinutes shouldBe 2340L
            }

            Then("entspricht die Anzahl der Feiertage den gesetzlichen Feiertagen des Jahres") {
                val holidays = PublicHolidays.getHolidaysForYear(year)
                summary.publicHolidayCount shouldBe holidays.size
            }
        }
    }
})
