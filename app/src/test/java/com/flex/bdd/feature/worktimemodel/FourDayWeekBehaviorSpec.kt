package com.flex.bdd.feature.worktimemodel

import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.model.getRuleForDate
import com.flex.domain.usecase.BuildPrognosisDaysUseCase
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * BDD-Spezifikation für das 4-Tage-Woche-Arbeitszeitmodell.
 *
 * Behandelt:
 * 1. 4-Tage-Woche (Mo–Do mit workDays = {MONDAY, TUESDAY, WEDNESDAY, THURSDAY}, dailyWorkMinutes = 533)
 * 2. Freitage zählen nicht zum Monatssoll
 * 3. Arbeit an einem Freitag zählt als 100% Gleitzeitgewinn (kein Tagessoll-Abzug)
 * 4. Urlaub an einem Freitag verbraucht keinen Urlaubstag
 */
class FourDayWeekBehaviorSpec : BehaviorSpec({

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
        dailyWorkMinutes = 426, // Fallback Vollzeit
        monthlyWorkMinutes = 9372,
        initialFlextimeMinutes = 0,
        initialOvertimeMinutes = 0
    )

    val september2026 = YearMonth.of(2026, 9)

    // Im September 2026: 4 Mo, 5 Di, 5 Mi, 4 Do = 18 Mo–Do Tage. 4 Freitage.
    val fourDayRule = WorkTimeRule(
        id = 1L,
        validFrom = september2026,
        dailyWorkMinutes = 533, // 8h 53min pro Arbeitstag (35,5h auf 4 Tage)
        monthlyWorkMinutes = 18 * 533, // 9.594 Min
        workDays = fourDays
    )

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

    fun createNeutralDay(
        date: LocalDate,
        dayType: DayType,
        location: WorkLocation = WorkLocation.HOME_OFFICE
    ): WorkDay {
        return WorkDay(
            id = date.toEpochDay(),
            date = date,
            dayType = dayType,
            location = location,
            timeBlocks = emptyList()
        )
    }

    // =========================================================================
    // Szenario 1: 4-Tage-Woche (Mo–Do, Freitage zählen nicht zum Monatssoll)
    // =========================================================================
    Given("ein 4-Tage-Woche-Modell (Mo–Do Arbeitstage, Freitage frei, 533 Min/Tag)") {

        When("das Monatssoll für September 2026 berechnet wird") {
            // September 2026 hat 30 Tage:
            // 4x Mo, 5x Di, 5x Mi, 4x Do = 18 Mo–Do Tage.
            // 4x Fr (04., 11., 18., 25.09.) sind arbeitsfrei.
            val balance = calculateFlextime(
                workDays = emptyList(),
                settings = baseSettings,
                yearMonth = september2026,
                workTimeRules = listOf(fourDayRule)
            )

            Then("zählen Freitage nicht zum Monatssoll und das Soll beträgt 9.594 Minuten (18 * 533 Min)") {
                balance.targetMinutes shouldBe 18L * 533L
                balance.targetMinutes shouldBe 9594L
            }
        }

        When("die Monatsprognose für September 2026 generiert wird (BuildPrognosisDaysUseCase)") {
            val prognosis = buildPrognosisDays(
                month = september2026,
                existingDays = emptyList(),
                settings = baseSettings,
                workTimeRules = listOf(fourDayRule)
            )

            Then("enthält die Prognose exakt 18 geplante Arbeitstage") {
                prognosis.size shouldBe 18
            }

            Then("enthält die Prognose keinen einzigen Freitag, Samstag oder Sonntag") {
                prognosis.none { it.date.dayOfWeek == DayOfWeek.FRIDAY }.shouldBeTrue()
                prognosis.none { it.date.dayOfWeek == DayOfWeek.SATURDAY }.shouldBeTrue()
                prognosis.none { it.date.dayOfWeek == DayOfWeek.SUNDAY }.shouldBeTrue()
            }
        }
    }

    // =========================================================================
    // Szenario 2: Arbeit an einem Freitag zählt als 100% Gleitzeitgewinn
    // =========================================================================
    Given("Arbeitszeiterfassung an regulären Arbeitstagen vs. arbeitsfreien Freitagen") {

        When("an einem regulären Arbeitstag (Donnerstag, 03.09.2026) genau 533 Minuten gearbeitet werden") {
            val thu = createDurationDay(LocalDate.of(2026, 9, 3), 533)
            val balance = calculateFlextime(listOf(thu), baseSettings, workTimeRules = listOf(fourDayRule))

            Then("ist das Gleitzeit-Delta 0 Minuten (Tagessoll erfüllt)") {
                balance.earnedMinutes shouldBe 0L
                balance.totalMinutes shouldBe 0L
            }
        }

        When("an einem regulären Arbeitstag 600 Minuten gearbeitet werden") {
            val thu = createDurationDay(LocalDate.of(2026, 9, 3), 600)
            val balance = calculateFlextime(listOf(thu), baseSettings, workTimeRules = listOf(fourDayRule))

            Then("werden nur die über das Tagessoll hinausgehenden 67 Minuten als Gleitzeit verbucht (600 - 533)") {
                balance.earnedMinutes shouldBe 67L
                balance.totalMinutes shouldBe 67L
            }
        }

        When("an einem freien Freitag (04.09.2026) 240 Minuten (4h) gearbeitet werden") {
            val fri = createDurationDay(LocalDate.of(2026, 9, 4), 240)
            val balance = calculateFlextime(listOf(fri), baseSettings, workTimeRules = listOf(fourDayRule))

            Then("zählt die Arbeitszeit zu 100% als Gleitzeitgewinn (+240 Minuten) ohne Tagessoll-Abzug") {
                balance.earnedMinutes shouldBe 240L
                balance.totalMinutes shouldBe 240L
            }
        }

        When("an einem freien Freitag eine volle Schicht von 533 Minuten gearbeitet wird") {
            val fri = createDurationDay(LocalDate.of(2026, 9, 4), 533)
            val balance = calculateFlextime(listOf(fri), baseSettings, workTimeRules = listOf(fourDayRule))

            Then("werden die vollen 533 Minuten ohne Abzug als Gleitzeit gutgeschrieben (+533 Min)") {
                balance.earnedMinutes shouldBe 533L
                balance.totalMinutes shouldBe 533L
            }
        }
    }

    // =========================================================================
    // Szenario 3: Urlaub an einem Freitag verbraucht keinen Urlaubstag
    // =========================================================================
    Given("Urlaubsplanung in einer 4-Tage-Woche (Mo–Do Arbeitstage, Freitag arbeitsfrei)") {

        When("eine komplette Kalenderwoche Urlaub erfasst wird (Mo 07.09. bis Fr 11.09.2026)") {
            val weekVacationDays = (7..11).map { day ->
                createNeutralDay(LocalDate.of(2026, 9, day), DayType.VACATION)
            }

            // Urlaubstage, die auf reguläre Arbeitstage (Mo-Do) fallen, verbrauchen Urlaubstage
            val activeRules = listOf(fourDayRule)
            val effectiveVacationDays = weekVacationDays.filter { day ->
                val rule = activeRules.getRuleForDate(day.date)
                val activeDays = rule?.workDays ?: fourDays
                day.date.dayOfWeek in activeDays
            }

            Then("werden nur 4 Urlaubstage verbraucht (Montag bis Donnerstag)") {
                effectiveVacationDays.size shouldBe 4
            }

            Then("verbraucht der Freitag keinen Urlaubstag, da er arbeitsfrei ist") {
                effectiveVacationDays.none { it.date.dayOfWeek == DayOfWeek.FRIDAY }.shouldBeTrue()
            }
        }

        When("ein Urlaubstag explizit an einem freien Freitag eingetragen wird (04.09.2026)") {
            val fridayVacation = createNeutralDay(LocalDate.of(2026, 9, 4), DayType.VACATION)

            val dayBalance = calculateFlextime(listOf(fridayVacation), baseSettings, workTimeRules = listOf(fourDayRule))
            val monthBalance = calculateFlextime(listOf(fridayVacation), baseSettings, YearMonth.of(2026, 9), listOf(fourDayRule))

            Then("ist der Tag gleitzeitneutral (0 Minuten Delta)") {
                dayBalance.earnedMinutes shouldBe 0L
                dayBalance.totalMinutes shouldBe 0L
            }

            Then("verändert sich das Monatssoll nicht (bleibt bei 18 * 533 = 9.594 Min)") {
                monthBalance.targetMinutes shouldBe 18L * 533L
                monthBalance.targetMinutes shouldBe 9594L
            }

            Then("wird für diesen arbeitsfreien Freitag kein Urlaubstag verbraucht") {
                val activeDays = listOf(fourDayRule).getRuleForDate(fridayVacation.date)?.workDays ?: fourDays
                val consumedDays = listOf(fridayVacation).count {
                    it.dayType == DayType.VACATION && it.date.dayOfWeek in activeDays
                }
                consumedDays shouldBe 0
            }
        }
    }

    // =========================================================================
    // Szenario 4: Kombinierte Wochenauswertung in einer 4-Tage-Woche
    // =========================================================================
    Given("eine gesamte Arbeitswoche mit Mehrarbeit und zusätzlichem Freitagseinsatz") {

        When("Mo–Do gearbeitet wird und am freien Freitag zusätzliche Stunden geleistet werden") {
            // Mo 07.09.: 533 Min (0 Delta)
            // Di 08.09.: 600 Min (+67 Delta)
            // Mi 09.09.: 533 Min (0 Delta)
            // Do 10.09.: 500 Min (-33 Delta)
            // Fr 11.09. (arbeitsfrei): 240 Min (+240 Delta, da 100% Gleitzeit)
            val mon = createDurationDay(LocalDate.of(2026, 9, 7), 533)
            val tue = createDurationDay(LocalDate.of(2026, 9, 8), 600)
            val wed = createDurationDay(LocalDate.of(2026, 9, 9), 533)
            val thu = createDurationDay(LocalDate.of(2026, 9, 10), 500)
            val fri = createDurationDay(LocalDate.of(2026, 9, 11), 240)

            val weekBalance = calculateFlextime(
                workDays = listOf(mon, tue, wed, thu, fri),
                settings = baseSettings,
                workTimeRules = listOf(fourDayRule)
            )

            Then("beträgt das gesamte Gleitzeitsaldo exakt +274 Minuten (0 + 67 + 0 - 33 + 240)") {
                weekBalance.earnedMinutes shouldBe 274L
                weekBalance.totalMinutes shouldBe 274L
            }
        }
    }
})
