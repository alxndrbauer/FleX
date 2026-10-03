package com.flex.bdd.feature.quota

import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class OfficeQuotaBehaviorSpec : BehaviorSpec({

    val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
    val calculateQuota = CalculateQuotaUseCase(calculateDayWorkTime)

    val defaultSettings = Settings(
        dailyWorkMinutes = 426, // 7h 06m
        monthlyWorkMinutes = 8520, // 20 Tage * 426 min
        officeQuotaPercent = 40, // 40% Büroquote
        officeQuotaMinDays = 8
    )
    val testMonth = YearMonth.of(2026, 9)

    fun createFullDay(
        date: LocalDate,
        location: WorkLocation,
        minutes: Long,
        dayType: DayType = DayType.WORK
    ): WorkDay {
        val start = LocalTime.of(8, 0)
        return WorkDay(
            id = date.toEpochDay(),
            date = date,
            location = location,
            dayType = dayType,
            timeBlocks = listOf(
                TimeBlock(
                    id = date.toEpochDay(),
                    workDayId = date.toEpochDay(),
                    startTime = start,
                    endTime = start.plusMinutes(minutes),
                    location = location,
                    isDuration = true
                )
            )
        )
    }

    fun createNeutralDay(
        date: LocalDate,
        dayType: DayType
    ): WorkDay {
        return WorkDay(
            id = date.toEpochDay(),
            date = date,
            location = WorkLocation.OFFICE,
            dayType = dayType,
            timeBlocks = emptyList()
        )
    }

    Given("ein Arbeitsmonat mit einer Ziel-Büroquote von 40 Prozent") {

        When("die Bürozeit mit 50 Prozent über dem Ziel liegt (Übererfüllung)") {
            // 10 Bürotage à 426 min = 4260 min (50% von 8520 min Soll)
            // 10 Home-Office Tage à 426 min = 4260 min
            val workDays = (1..10).map { day ->
                createFullDay(LocalDate.of(2026, 9, day), WorkLocation.OFFICE, 426)
            } + (11..20).map { day ->
                createFullDay(LocalDate.of(2026, 9, day), WorkLocation.HOME_OFFICE, 426)
            }

            val status = calculateQuota(
                workDays = workDays,
                settings = defaultSettings,
                yearMonth = testMonth
            )

            Then("beträgt der erfasste Büroanteil 50 Prozent") {
                status.officeMinutes shouldBe 4260L
                status.homeOfficeMinutes shouldBe 4260L
                status.officePercent shouldBe (50.0 plusOrMinus 0.01)
            }

            Then("ist die Prozentquote als erfüllt markiert (percentQuotaMet == true)") {
                status.percentQuotaMet.shouldBeTrue()
            }

            Then("ist auch der Gesamtstatus der Quote positiv") {
                status.quotaMet.shouldBeTrue()
            }
        }

        When("die Bürozeit mit 30 Prozent unter dem Ziel liegt (Untererfüllung)") {
            // 6 Bürotage à 426 min = 2556 min (30% von 8520 min Soll)
            // 14 Home-Office Tage à 426 min = 5964 min
            val workDays = (1..6).map { day ->
                createFullDay(LocalDate.of(2026, 9, day), WorkLocation.OFFICE, 426)
            } + (7..20).map { day ->
                createFullDay(LocalDate.of(2026, 9, day), WorkLocation.HOME_OFFICE, 426)
            }

            val status = calculateQuota(
                workDays = workDays,
                settings = defaultSettings,
                yearMonth = testMonth
            )

            Then("beträgt der erfasste Büroanteil 30 Prozent") {
                status.officeMinutes shouldBe 2556L
                status.homeOfficeMinutes shouldBe 5964L
                status.officePercent shouldBe (30.0 plusOrMinus 0.01)
            }

            Then("ist die Prozentquote nicht erfüllt (percentQuotaMet == false)") {
                status.percentQuotaMet.shouldBeFalse()
            }
        }

        When("die Bürozeit exakt 40 Prozent beträgt (Punktlandung)") {
            // Soll = 10000 min, exakt 4000 min Büro (40.0%)
            val settingsWith10k = defaultSettings.copy(monthlyWorkMinutes = 10000)
            val workDays = (1..10).map { day ->
                createFullDay(LocalDate.of(2026, 9, day), WorkLocation.OFFICE, 400)
            } + (11..25).map { day ->
                createFullDay(LocalDate.of(2026, 9, day), WorkLocation.HOME_OFFICE, 400)
            }

            val status = calculateQuota(
                workDays = workDays,
                settings = settingsWith10k,
                yearMonth = testMonth
            )

            Then("beträgt der erfasste Büroanteil exakt 40.0 Prozent") {
                status.officeMinutes shouldBe 4000L
                status.officePercent shouldBe (40.0 plusOrMinus 0.01)
            }

            Then("gilt die Prozentquote als erfüllt") {
                status.percentQuotaMet.shouldBeTrue()
                status.quotaMet.shouldBeTrue()
            }
        }
    }

    Given("ein Monat mit neutralen Tagen wie Urlaub und Krankheit") {
        val settings = defaultSettings // 8520 min Soll (20 Tage à 426 min)

        When("5 Urlaubstage und 5 Krankheitstage genommen werden und 4 Bürotage anfallen") {
            // 5 Tage Urlaub + 5 Tage Krankheit = 10 neutrale Tage
            // Bereinigte Basis: 8520 min - (10 * 426 min) = 4260 min
            // 4 Bürotage à 426 min = 1704 min
            // Quote auf bereinigter Basis: 1704 / 4260 = 40.0%
            val vacationDays = (1..5).map { day ->
                createNeutralDay(LocalDate.of(2026, 9, day), DayType.VACATION)
            }
            val sickDays = (6..10).map { day ->
                createNeutralDay(LocalDate.of(2026, 9, day), DayType.SICK_DAY)
            }
            val officeDays = (11..14).map { day ->
                createFullDay(LocalDate.of(2026, 9, day), WorkLocation.OFFICE, 426)
            }
            val allDays = vacationDays + sickDays + officeDays

            val status = calculateQuota(
                workDays = allDays,
                settings = settings,
                yearMonth = testMonth
            )

            Then("reduzieren die neutralen Tage die Sollbasis auf 4260 Minuten") {
                status.officeMinutes shouldBe 1704L
                status.officePercent shouldBe (40.0 plusOrMinus 0.01)
            }

            Then("wird die Prozentquote auf der bereinigten Basis als erfüllt gewertet") {
                // Ohne Bereinigung läge der Anteil bei 1704 / 8520 = 20%, was nicht genügen würde
                status.percentQuotaMet.shouldBeTrue()
            }

            Then("zählen neutrale Tage weder als Büro- noch als Home-Office-Tage") {
                status.officeDays shouldBe 4
                status.homeOfficeDays shouldBe 0
            }
        }

        When("Urlaub, Gleittage und Sonderurlaub kombiniert werden") {
            // 2 Tage Urlaub, 2 Tage Gleittag, 1 Tag Sonderurlaub = 5 neutrale Tage
            // Bereinigtes Soll: 8520 - (5 * 426) = 6390 min
            // 6 Bürotage à 426 min = 2556 min -> 2556 / 6390 = 40.0%
            val neutralDays = listOf(
                createNeutralDay(LocalDate.of(2026, 9, 1), DayType.VACATION),
                createNeutralDay(LocalDate.of(2026, 9, 2), DayType.VACATION),
                createNeutralDay(LocalDate.of(2026, 9, 3), DayType.FLEX_DAY),
                createNeutralDay(LocalDate.of(2026, 9, 4), DayType.FLEX_DAY),
                createNeutralDay(LocalDate.of(2026, 9, 5), DayType.SPECIAL_VACATION)
            )
            val officeDays = (6..11).map { day ->
                createFullDay(LocalDate.of(2026, 9, day), WorkLocation.OFFICE, 426)
            }

            val status = calculateQuota(
                workDays = neutralDays + officeDays,
                settings = settings,
                yearMonth = testMonth
            )

            Then("wird die Quote auf dem reduzierten Soll berechnet und erreicht 40 Prozent") {
                status.officeMinutes shouldBe 2556L
                status.officePercent shouldBe (40.0 plusOrMinus 0.01)
                status.percentQuotaMet.shouldBeTrue()
            }
        }

        When("abweichende Arbeitszeitregeln für neutrale Tage gelten") {
            // Teilzeitregel: 300 min Tagessoll, 6000 min Monatssoll
            val rule = WorkTimeRule(
                id = 1L,
                validFrom = YearMonth.of(2026, 1),
                dailyWorkMinutes = 300,
                monthlyWorkMinutes = 6000
            )
            // 2 Urlaubstage à 300 min Abzug = 600 min -> Bereinigtes Soll = 5400 min
            // 4 Bürotage à 540 min = 2160 min Bürozeit -> 2160 / 5400 = 40.0%
            val workDays = listOf(
                createNeutralDay(LocalDate.of(2026, 9, 1), DayType.VACATION),
                createNeutralDay(LocalDate.of(2026, 9, 2), DayType.VACATION)
            ) + (3..6).map {
                createFullDay(LocalDate.of(2026, 9, it), WorkLocation.OFFICE, 540)
            }

            val status = calculateQuota(
                workDays = workDays,
                settings = settings,
                yearMonth = testMonth,
                workTimeRules = listOf(rule)
            )

            Then("wird der Abzug anhand des Tagessolls der WorkTimeRule berechnet") {
                status.officeMinutes shouldBe 2160L
                status.officePercent shouldBe (40.0 plusOrMinus 0.01)
                status.percentQuotaMet.shouldBeTrue()
            }
        }
    }
})
