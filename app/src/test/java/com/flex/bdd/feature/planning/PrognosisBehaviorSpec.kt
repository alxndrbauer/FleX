package com.flex.bdd.feature.planning

import com.flex.domain.model.DayType
import com.flex.domain.model.PublicHolidays
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.usecase.BuildPrognosisDaysUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class PrognosisBehaviorSpec : BehaviorSpec({

    val buildPrognosisDays = BuildPrognosisDaysUseCase()

    val defaultSettings = Settings(
        dailyWorkMinutes = 426,
        monthlyWorkMinutes = 8520,
        defaultStartTime = LocalTime.of(8, 0)
    )

    Given("ein leerer Monat ohne bestehende Buchungen") {
        // November 2026: 30 Tage.
        // Startet an einem Sonntag (1. Nov = Sonntag).
        // Werktage (Mo-Fr):
        // 2.-6. Nov (5), 9.-13. Nov (5), 16.-20. Nov (5), 23.-27. Nov (5), 30. Nov (1) = 21 Werktage.
        // Keine gesetzlichen Feiertage in Hamburg im November.
        val testMonth = YearMonth.of(2026, 11)

        When("die Prognose mit Standard-Arbeitszeitregel (Mo–Fr) erstellt wird") {
            val prognosisDays = buildPrognosisDays(
                month = testMonth,
                existingDays = emptyList(),
                settings = defaultSettings,
                workTimeRules = emptyList()
            )

            Then("füllt der UseCase fehlende Werktage des Monats mit genau 21 Prognosetagen auf") {
                prognosisDays shouldHaveSize 21
            }

            Then("sind alle Prognosetage als geplante Home-Office-Arbeitstage markiert") {
                prognosisDays.all { it.isPlanned }.shouldBeTrue()
                prognosisDays.all { it.dayType == DayType.WORK }.shouldBeTrue()
                prognosisDays.all { it.location == WorkLocation.HOME_OFFICE }.shouldBeTrue()
            }

            Then("werden Wochenendtage (Samstage und Sonntage) nicht mit Prognosen befüllt") {
                val weekendDays = prognosisDays.filter {
                    it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY
                }
                weekendDays.shouldBeEmpty()
            }

            Then("besitzt jeder Prognosetag einen Duration-TimeBlock mit Standard-Startzeit und Tagessoll") {
                for (day in prognosisDays) {
                    day.timeBlocks shouldHaveSize 1
                    val block = day.timeBlocks.first()
                    block.isDuration.shouldBeTrue()
                    block.startTime shouldBe LocalTime.of(8, 0)
                    block.endTime shouldBe LocalTime.of(8, 0).plusMinutes(426)
                    block.location shouldBe WorkLocation.HOME_OFFICE
                }
            }
        }
    }

    Given("eine individuelle Arbeitszeitregel mit abweichenden Arbeitstagen (z. B. 4-Tage-Woche Mo–Do)") {
        val testMonth = YearMonth.of(2026, 11)
        val fourDayRule = WorkTimeRule(
            id = 1L,
            validFrom = YearMonth.of(2026, 1),
            dailyWorkMinutes = 480, // 8h
            monthlyWorkMinutes = 8640,
            workDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)
        )

        When("die Prognosetage gemäß der aktiven WorkTimeRule befüllt werden") {
            val prognosisDays = buildPrognosisDays(
                month = testMonth,
                existingDays = emptyList(),
                settings = defaultSettings,
                workTimeRules = listOf(fourDayRule)
            )

            Then("werden nur Montage bis Donnerstage befüllt (17 Tage)") {
                // November 2026 hat 4 volle Wochen Mo-Do (16) + Mo 30. Nov (1) = 17 Tage
                prognosisDays shouldHaveSize 17
                prognosisDays.all { it.date.dayOfWeek in fourDayRule.workDays }.shouldBeTrue()
            }

            Then("werden Freitage, Samstage und Sonntage vollständig ignoriert") {
                val nonWorkDays = prognosisDays.filter {
                    it.date.dayOfWeek !in fourDayRule.workDays
                }
                nonWorkDays.shouldBeEmpty()
            }

            Then("basiert die Blockdauer auf dem dailyWorkMinutes der Regel (480 Minuten)") {
                prognosisDays.all { day ->
                    val block = day.timeBlocks.first()
                    block.endTime == block.startTime.plusMinutes(480)
                }.shouldBeTrue()
            }
        }
    }

    Given("bereits vorhandene Tage (echte Buchungen und manuell geplante Tage)") {
        val testMonth = YearMonth.of(2026, 11)

        // 1. Echter Buchungstag am 10.11.2026 (Dienstag) im Büro mit Zeiterfassung
        val existingWorkDay = WorkDay(
            id = 101L,
            date = LocalDate.of(2026, 11, 10),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = listOf(
                TimeBlock(
                    id = 1L,
                    workDayId = 101L,
                    startTime = LocalTime.of(8, 15),
                    endTime = LocalTime.of(16, 45),
                    location = WorkLocation.OFFICE
                )
            )
        )

        // 2. Geplanter Urlaubstag am 12.11.2026 (Donnerstag)
        val plannedVacationDay = WorkDay(
            id = 102L,
            date = LocalDate.of(2026, 11, 12),
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.VACATION,
            isPlanned = true,
            timeBlocks = emptyList()
        )

        // 3. Manuell geplanter Bürotag am 17.11.2026 (Dienstag)
        val plannedOfficeDay = WorkDay(
            id = 103L,
            date = LocalDate.of(2026, 11, 17),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = true,
            timeBlocks = listOf(
                TimeBlock(
                    id = 2L,
                    workDayId = 103L,
                    startTime = LocalTime.of(9, 0),
                    endTime = LocalTime.of(17, 30),
                    location = WorkLocation.OFFICE
                )
            )
        )

        val existingDays = listOf(existingWorkDay, plannedVacationDay, plannedOfficeDay)

        When("die Prognose für den Monat mit vorhandenen Tagen berechnet wird") {
            val result = buildPrognosisDays(
                month = testMonth,
                existingDays = existingDays,
                settings = defaultSettings
            )

            Then("werden die vorhandenen Tage nicht überschrieben") {
                // Echter Arbeitstag bleibt unberührt
                val day10 = result.first { it.date == LocalDate.of(2026, 11, 10) }
                day10.id shouldBe 101L
                day10.isPlanned.shouldBeFalse()
                day10.location shouldBe WorkLocation.OFFICE
                day10.timeBlocks.first().startTime shouldBe LocalTime.of(8, 15)

                // Geplanter Urlaubstag bleibt erhalten
                val day12 = result.first { it.date == LocalDate.of(2026, 11, 12) }
                day12.id shouldBe 102L
                day12.dayType shouldBe DayType.VACATION
                day12.isPlanned.shouldBeTrue()

                // Geplanter Bürotag behält Standort Büro und Zeiten
                val day17 = result.first { it.date == LocalDate.of(2026, 11, 17) }
                day17.id shouldBe 103L
                day17.location shouldBe WorkLocation.OFFICE
                day17.isPlanned.shouldBeTrue()
            }

            Then("beträgt die Gesamtzahl der Tage weiterhin 21 (3 vorhandene + 18 generierte Prognosetage)") {
                result shouldHaveSize 21
                result.count { it.id == 0L } shouldBe 18 // genau 18 neu generierte
            }
        }
    }

    Given("gesetzliche Feiertage im Zielmonat") {
        // Mai 2026:
        // 1. Mai 2026: Tag der Arbeit (Freitag, gesetzlicher Feiertag)
        // 14. Mai 2026: Christi Himmelfahrt (Donnerstag, gesetzlicher Feiertag)
        // 25. Mai 2026: Pfingstmontag (Montag, gesetzlicher Feiertag)
        val may2026 = YearMonth.of(2026, 5)

        When("die Prognose für Mai 2026 berechnet wird") {
            val prognosisDays = buildPrognosisDays(
                month = may2026,
                existingDays = emptyList(),
                settings = defaultSettings
            )

            Then("wird der 1. Mai (Tag der Arbeit) als Feiertag berücksichtigt und erhält keinen Prognosetag") {
                val laborDay = LocalDate.of(2026, 5, 1)
                PublicHolidays.isHoliday(laborDay).shouldBeTrue()
                prognosisDays.none { it.date == laborDay }.shouldBeTrue()
            }

            Then("wird Christi Himmelfahrt (14. Mai) berücksichtigt und erhält keinen Prognosetag") {
                val ascensionDay = LocalDate.of(2026, 5, 14)
                PublicHolidays.isHoliday(ascensionDay).shouldBeTrue()
                prognosisDays.none { it.date == ascensionDay }.shouldBeTrue()
            }

            Then("wird Pfingstmontag (25. Mai) berücksichtigt und erhält keinen Prognosetag") {
                val whitMonday = LocalDate.of(2026, 5, 25)
                PublicHolidays.isHoliday(whitMonday).shouldBeTrue()
                prognosisDays.none { it.date == whitMonday }.shouldBeTrue()
            }

            Then("werden alle regulären Nicht-Feiertags-Werktage als Prognose erzeugt") {
                // Mai 2026: 31 Tage
                // Reguläre Mo-Fr Tage = 21 Tage.
                // Abzüglich 3 Feiertage (1., 14., 25. Mai) = 18 Prognosetage.
                prognosisDays shouldHaveSize 18
            }
        }
    }
})
