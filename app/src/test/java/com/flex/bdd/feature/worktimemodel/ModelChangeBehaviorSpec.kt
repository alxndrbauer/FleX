package com.flex.bdd.feature.worktimemodel

import com.flex.domain.model.DEFAULT_WORK_DAYS
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.model.getRuleForDate
import com.flex.domain.model.getRuleForMonth
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.BuildPrognosisDaysUseCase
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.PrepareExportDataUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * BDD-Spezifikation für unterjährige Arbeitszeitmodell-Wechsel.
 *
 * Behandelt:
 * 1. Unterjähriger Modellwechsel mit WorkTimeRule(validFrom = YearMonth.of(2026, 7), ...)
 * 2. Monate vor Juli verwenden altes Modell (Vollzeit 5 Tage)
 * 3. Monate ab Juli verwenden neues Modell (4-Tage-Woche)
 * 4. Monatsübergreifende Auswertungen beachten den Stichtag
 */
class ModelChangeBehaviorSpec : BehaviorSpec({

    val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
    val calculateFlextime = CalculateFlextimeUseCase(calculateDayWorkTime)
    val buildPrognosisDays = BuildPrognosisDaysUseCase()

    val fourDays = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY
    )

    val baseSettings = Settings(
        dailyWorkMinutes = 426,
        monthlyWorkMinutes = 9372,
        initialFlextimeMinutes = 0,
        initialOvertimeMinutes = 0
    )

    // Altes Modell bis einschließlich Juni 2026: Vollzeit 5 Tage (Mo–Fr), 480 Min/Tag
    val ruleOld = WorkTimeRule(
        id = 1L,
        validFrom = YearMonth.of(2026, 1),
        dailyWorkMinutes = 480,
        monthlyWorkMinutes = 10560,
        workDays = DEFAULT_WORK_DAYS
    )

    // Neues Modell ab 01.07.2026: 4-Tage-Woche (Mo–Do), 533 Min/Tag
    val ruleNew = WorkTimeRule(
        id = 2L,
        validFrom = YearMonth.of(2026, 7),
        dailyWorkMinutes = 533,
        monthlyWorkMinutes = 18 * 533, // 9.594 Min
        workDays = fourDays
    )

    val rules = listOf(ruleOld, ruleNew)

    fun createDurationDay(
        date: LocalDate,
        minutes: Long,
        dayType: DayType = DayType.WORK,
        location: WorkLocation = WorkLocation.OFFICE
    ): WorkDay {
        val start = LocalTime.of(8, 0)
        return WorkDay(
            id = date.toEpochDay(),
            date = date,
            dayType = dayType,
            location = location,
            timeBlocks = listOf(
                TimeBlock(
                    id = date.toEpochDay(),
                    workDayId = date.toEpochDay(),
                    startTime = start,
                    endTime = start.plusMinutes(minutes),
                    isDuration = true,
                    location = location
                )
            )
        )
    }

    // =========================================================================
    // Szenario 1: Modell-Lookup vor und ab Juli 2026
    // =========================================================================
    Given("ein unterjähriger Modellwechsel zum 01.07.2026 (Vollzeit -> 4-Tage-Woche)") {

        When("die Arbeitszeitregel für Monate vor Juli abgefragt wird (z.B. Juni 2026)") {
            val ruleJune = rules.getRuleForMonth(YearMonth.of(2026, 6))

            Then("gilt das alte Vollzeitmodell mit 5 Arbeitstagen (Mo–Fr) und 480 Min Tagessoll") {
                ruleJune.shouldNotBeNull()
                ruleJune.dailyWorkMinutes shouldBe 480
                ruleJune.workDays shouldBe DEFAULT_WORK_DAYS
            }
        }

        When("die Arbeitszeitregel für Monate ab Juli abgefragt wird (z.B. Juli und August 2026)") {
            val ruleJuly = rules.getRuleForMonth(YearMonth.of(2026, 7))
            val ruleAugust = rules.getRuleForMonth(YearMonth.of(2026, 8))

            Then("gilt ab Juli das neue Modell mit 4 Arbeitstagen (Mo–Do) und 533 Min Tagessoll") {
                ruleJuly.shouldNotBeNull()
                ruleJuly.dailyWorkMinutes shouldBe 533
                ruleJuly.workDays shouldBe fourDays

                ruleAugust.shouldNotBeNull()
                ruleAugust.dailyWorkMinutes shouldBe 533
                ruleAugust.workDays shouldBe fourDays
            }
        }
    }

    // =========================================================================
    // Szenario 2: Tagesgenaue Anwendung der Arbeitszeitregeln an der Stichtagsgrenze
    // =========================================================================
    Given("die tagesgenaue Anwendung der Arbeitszeitregeln an der Stichtagsgrenze (30.06. vs. 01.07.2026)") {

        When("am letzten Tag vor dem Wechsel (Dienstag, 30.06.2026) 480 Minuten gearbeitet werden") {
            val dayJune = createDurationDay(LocalDate.of(2026, 6, 30), 480)
            val balance = calculateFlextime(listOf(dayJune), baseSettings, workTimeRules = rules)

            Then("wird das alte Tagessoll von 480 Minuten angewendet (0 Min Delta)") {
                balance.earnedMinutes shouldBe 0L
                balance.totalMinutes shouldBe 0L
            }
        }

        When("am ersten Tag nach dem Wechsel (Mittwoch, 01.07.2026) 480 Minuten gearbeitet werden") {
            val dayJuly = createDurationDay(LocalDate.of(2026, 7, 1), 480)
            val balance = calculateFlextime(listOf(dayJuly), baseSettings, workTimeRules = rules)

            Then("wird das neue Tagessoll von 533 Minuten angewendet (-53 Min Unterzeit)") {
                balance.earnedMinutes shouldBe -53L // 480 - 533
                balance.totalMinutes shouldBe -53L
            }
        }

        When("am ersten Tag nach dem Wechsel (Mittwoch, 01.07.2026) genau 533 Minuten gearbeitet werden") {
            val dayJuly = createDurationDay(LocalDate.of(2026, 7, 1), 533)
            val balance = calculateFlextime(listOf(dayJuly), baseSettings, workTimeRules = rules)

            Then("ist das neue Tagessoll von 533 Minuten punktgenau erfüllt (0 Min Delta)") {
                balance.earnedMinutes shouldBe 0L
                balance.totalMinutes shouldBe 0L
            }
        }

        When("an Freitagen vor und nach dem Wechsel gearbeitet wird") {
            // Freitag 26.06.2026: Wochentag im alten Modell (Tagessoll = 480m) -> 480m gearbeitet = 0 Delta
            val fridayJune = createDurationDay(LocalDate.of(2026, 6, 26), 480)
            val balanceJune = calculateFlextime(listOf(fridayJune), baseSettings, workTimeRules = rules)

            // Freitag 03.07.2026: Arbeitsfreier Tag im neuen 4-Tage-Modell -> 240m gearbeitet = +240m 100% Gleitzeit
            val fridayJuly = createDurationDay(LocalDate.of(2026, 7, 3), 240)
            val balanceJuly = calculateFlextime(listOf(fridayJuly), baseSettings, workTimeRules = rules)

            Then("zählt der Freitag im Juni mit regulärem Tagessoll-Abzug (0 Min Delta)") {
                balanceJune.earnedMinutes shouldBe 0L
            }

            Then("zählt der Freitag im Juli zu 100% als Gleitzeitgewinn (+240 Min) ohne Tagessoll-Abzug") {
                balanceJuly.earnedMinutes shouldBe 240L
            }
        }
    }

    // =========================================================================
    // Szenario 3: Monatssoll-Berechnung vor und ab dem Stichtag
    // =========================================================================
    Given("die Monatssoll-Berechnung für Monate vor und ab dem Stichtag") {

        When("das Monatssoll für Juni 2026 (vor dem Stichtag) berechnet wird") {
            // Juni 2026: 22 Werktage Mo–Fr, keine Feiertage in Hamburg -> 22 * 480 = 10.560 Min
            val juneBalance = calculateFlextime(
                workDays = emptyList(),
                settings = baseSettings,
                yearMonth = YearMonth.of(2026, 6),
                workTimeRules = rules
            )

            Then("basiert das Monatssoll auf 22 Vollzeit-Tagen à 480 Minuten (10.560 Min)") {
                juneBalance.targetMinutes shouldBe 22L * 480L
                juneBalance.targetMinutes shouldBe 10560L
            }
        }

        When("das Monatssoll für Juli 2026 (ab dem Stichtag) berechnet wird") {
            // Juli 2026: 31 Tage, Mo–Do sind 18 Tage (4 Mo, 4 Di, 5 Mi, 5 Do), Freitage frei -> 18 * 533 = 9.594 Min
            val julyBalance = calculateFlextime(
                workDays = emptyList(),
                settings = baseSettings,
                yearMonth = YearMonth.of(2026, 7),
                workTimeRules = rules
            )

            Then("basiert das Monatssoll auf 18 Mo–Do Arbeitstagen à 533 Minuten (9.594 Min)") {
                julyBalance.targetMinutes shouldBe 18L * 533L
                julyBalance.targetMinutes shouldBe 9594L
            }
        }
    }

    // =========================================================================
    // Szenario 4: Monatsübergreifende Auswertungen beachten den Stichtag
    // =========================================================================
    Given("eine monatsübergreifende Gleitzeitauswertung über den Stichtag hinweg") {

        When("Arbeitstage aus Ende Juni und Anfang Juli gemeinsam übergeben werden") {
            // Tag 1 (Mo 29.06.2026, alt): 540 Min Netto (Tagessoll 480) -> +60 Min
            // Tag 2 (Di 30.06.2026, alt): 420 Min Netto (Tagessoll 480) -> -60 Min
            // Tag 3 (Mi 01.07.2026, neu): 533 Min Netto (Tagessoll 533) -> 0 Min
            // Tag 4 (Do 02.07.2026, neu): 600 Min Netto (Tagessoll 533) -> +67 Min
            // Tag 5 (Fr 03.07.2026, neu): 180 Min Netto (freier Freitag) -> +180 Min
            val day1 = createDurationDay(LocalDate.of(2026, 6, 29), 540)
            val day2 = createDurationDay(LocalDate.of(2026, 6, 30), 420)
            val day3 = createDurationDay(LocalDate.of(2026, 7, 1), 533)
            val day4 = createDurationDay(LocalDate.of(2026, 7, 2), 600)
            val day5 = createDurationDay(LocalDate.of(2026, 7, 3), 180)

            val balance = calculateFlextime(
                workDays = listOf(day1, day2, day3, day4, day5),
                settings = baseSettings,
                workTimeRules = rules
            )

            Then("beachtet die Auswertung für jeden Tag das stichtagsbezogene Modell") {
                // (+60) + (-60) + (0) + (+67) + (+180) = +247 Min
                balance.earnedMinutes shouldBe 247L
                balance.totalMinutes shouldBe 247L
            }
        }
    }

    // =========================================================================
    // Szenario 5: Monatsprognose vor und nach dem Stichtag (BuildPrognosisDaysUseCase)
    // =========================================================================
    Given("die Prognoseerstellung vor und ab dem Stichtag") {

        When("die Prognose für Juni 2026 (vor Stichtag) erstellt wird") {
            val prognosisJune = buildPrognosisDays(
                month = YearMonth.of(2026, 6),
                existingDays = emptyList(),
                settings = baseSettings,
                workTimeRules = rules
            )

            Then("enthält die Juni-Prognose alle 22 Mo–Fr Werktage einschließlich der Freitage") {
                prognosisJune.size shouldBe 22
                prognosisJune.any { it.date.dayOfWeek == DayOfWeek.FRIDAY }.shouldBeTrue()
            }
        }

        When("die Prognose für Juli 2026 (ab Stichtag) erstellt wird") {
            val prognosisJuly = buildPrognosisDays(
                month = YearMonth.of(2026, 7),
                existingDays = emptyList(),
                settings = baseSettings,
                workTimeRules = rules
            )

            Then("enthält die Juli-Prognose genau 18 Mo–Do Arbeitstage und schließt Freitage aus") {
                prognosisJuly.size shouldBe 18
                prognosisJuly.none { it.date.dayOfWeek == DayOfWeek.FRIDAY }.shouldBeTrue()
            }
        }
    }

    // =========================================================================
    // Szenario 6: Exportdaten-Aufbereitung über den Stichtag mit MockK
    // =========================================================================
    Given("Exportdaten-Aufbereitung für Juni und Juli über PrepareExportDataUseCase") {
        val workDayRepository = mockk<WorkDayRepository>()
        val settingsRepository = mockk<SettingsRepository>()
        val prepareExportData = PrepareExportDataUseCase(workDayRepository, settingsRepository, calculateDayWorkTime)

        coEvery { workDayRepository.getWorkDaysForMonth(any()) } returns flowOf(emptyList())
        coEvery { settingsRepository.getSettings() } returns flowOf(baseSettings)
        coEvery { settingsRepository.getWorkTimeRules() } returns flowOf(rules)
        coEvery { settingsRepository.getWorkTimeRuleForDate(any(), any()) } answers {
            val date = firstArg<LocalDate>()
            rules.getRuleForDate(date)
        }

        When("Exportdaten für Juni und Juli generiert werden") {
            val juneExport = prepareExportData(YearMonth.of(2026, 6))
            val julyExport = prepareExportData(YearMonth.of(2026, 7))

            Then("verwendet der Juni-Export an Werktagen das alte Tagessoll von 480 Minuten") {
                val weekdayJune = juneExport.rows.first { it.date == LocalDate.of(2026, 6, 1) }
                weekdayJune.targetMinutes shouldBe 480
            }

            Then("verwendet der Juli-Export an Werktagen das neue Tagessoll von 533 Minuten") {
                val weekdayJuly = julyExport.rows.first { it.date == LocalDate.of(2026, 7, 1) }
                weekdayJuly.targetMinutes shouldBe 533
            }
        }
    }
})
