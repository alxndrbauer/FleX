package com.flex.bdd.feature.flextime

import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class FlextimeCalculationBehaviorSpec : BehaviorSpec({

    val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
    val calculateFlextime = CalculateFlextimeUseCase(calculateDayWorkTime)

    val defaultSettings = Settings(
        dailyWorkMinutes = 426, // 7h 06min
        monthlyWorkMinutes = 8520, // 20 Tage * 426 Min
        initialFlextimeMinutes = 0,
        initialOvertimeMinutes = 0
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
        location: WorkLocation = WorkLocation.OFFICE
    ): WorkDay {
        return WorkDay(
            id = date.toEpochDay(),
            date = date,
            dayType = dayType,
            location = location,
            timeBlocks = emptyList()
        )
    }

    Given("ein regulärer Arbeitstag an einem Werktag mit 426 Min Tagessoll") {
        val workDate = LocalDate.of(2026, 9, 2) // Mittwoch

        When("480 Minuten (8h) gearbeitet werden (Überstunden)") {
            val day = createDurationDay(workDate, 480)
            val balance = calculateFlextime(listOf(day), defaultSettings)

            Then("wird ein Gleitzeitgewinn von +54 Minuten erzielt") {
                balance.earnedMinutes shouldBe 54L
            }

            Then("beträgt der Gesamtsaldo +54 Minuten") {
                balance.totalMinutes shouldBe 54L
            }

            Then("bleibt das Überstundenkonto bei 0 Minuten") {
                balance.earnedOvertimeMinutes shouldBe 0L
                balance.overtimeMinutes shouldBe 0L
            }
        }

        When("340 Minuten (5h 40min) gearbeitet werden (Minusstunden)") {
            val day = createDurationDay(workDate, 340)
            val balance = calculateFlextime(listOf(day), defaultSettings)

            Then("entsteht ein Gleitzeitverlust von -86 Minuten") {
                balance.earnedMinutes shouldBe -86L
            }

            Then("beträgt der Gesamtsaldo -86 Minuten") {
                balance.totalMinutes shouldBe -86L
            }

            Then("bleibt das Überstundenkonto unverändert bei 0 Minuten") {
                balance.earnedOvertimeMinutes shouldBe 0L
                balance.overtimeMinutes shouldBe 0L
            }
        }
    }

    Given("ein Urlaubstag (DayType.VACATION) an einem Werktag") {
        val vacationDate = LocalDate.of(2026, 9, 2) // Mittwoch
        val vacationDay = createNeutralDay(vacationDate, DayType.VACATION)

        When("die Gleitzeit für den Urlaubstag berechnet wird") {
            val balance = calculateFlextime(listOf(vacationDay), defaultSettings)

            Then("ist der Tag gleitzeitneutral mit 0 Minuten Gleitzeitgewinn/-verlust") {
                balance.earnedMinutes shouldBe 0L
                balance.totalMinutes shouldBe 0L
            }

            Then("bleibt das Überstundenkonto unberührt") {
                balance.earnedOvertimeMinutes shouldBe 0L
                balance.overtimeMinutes shouldBe 0L
            }
        }

        When("ein Urlaubstag im Vergleich zu einem Arbeitstag ohne Zeiterfassung betrachtet wird") {
            val absentDay = createNeutralDay(vacationDate, DayType.WORK)
            val absentBalance = calculateFlextime(listOf(absentDay), defaultSettings)
            val vacationBalance = calculateFlextime(listOf(vacationDay), defaultSettings)

            Then("verursacht der Arbeitstag ohne Zeit -426 Min Minusstunden") {
                absentBalance.earnedMinutes shouldBe -426L
            }

            Then("verursacht der Urlaubstag 0 Min Abzug und neutralisiert das Tagessoll") {
                vacationBalance.earnedMinutes shouldBe 0L
            }
        }
    }

    Given("ein Krankheitstag (DayType.SICK_DAY) an einem Werktag") {
        val sickDate = LocalDate.of(2026, 9, 3) // Donnerstag
        val sickDay = createNeutralDay(sickDate, DayType.SICK_DAY)

        When("die Gleitzeit für den Krankheitstag berechnet wird") {
            val balance = calculateFlextime(listOf(sickDay), defaultSettings)

            Then("ist der Tag gleitzeitneutral mit 0 Minuten Delta") {
                balance.earnedMinutes shouldBe 0L
                balance.totalMinutes shouldBe 0L
            }

            Then("werden keine Überstunden verbucht") {
                balance.earnedOvertimeMinutes shouldBe 0L
                balance.overtimeMinutes shouldBe 0L
            }
        }
    }

    Given("ein gesetzlicher Feiertag an einem Werktag") {
        // 01. Mai 2026 ist ein Freitag (Tag der Arbeit)
        val holidayDate = LocalDate.of(2026, 5, 1) // Freitag
        val testMonth = YearMonth.of(2026, 5)

        When("das Monatssoll für Mai 2026 mit Feiertagen berechnet wird") {
            // Mai 2026: 31 Tage, 21 Mo-Fr Werktage.
            // Hamburgische Feiertage an Werktagen: 01.05. (Tag der Arbeit), 14.05. (Himmelfahrt), 25.05. (Pfingstmontag) = 3 Tage.
            // Netto-Arbeitstage: 21 - 3 = 18 Tage.
            // Ohne Feiertage: 21 * 426 = 8946 Min.
            // Mit 3 Feiertagen: 18 * 426 = 7668 Min.
            val balance = calculateFlextime(emptyList(), defaultSettings, yearMonth = testMonth)

            Then("reduzieren Feiertage an Werktagen das Monatssoll um das Tagessoll") {
                val fullTargetWithoutHolidays = 21L * 426L
                val holidayDeduction = 3L * 426L
                balance.targetMinutes shouldBe (fullTargetWithoutHolidays - holidayDeduction)
                balance.targetMinutes shouldBe 18L * 426L
            }

            Then("ist der Feiertag ohne Arbeitszeit gleitzeitneutral (0 Min Delta)") {
                balance.earnedMinutes shouldBe 0L
            }
        }
    }

    Given("ein Benutzerkonto mit initialem Startsaldo (initialFlextimeMinutes)") {
        val initialMinutes = 120 // +2h Startsaldo
        val settingsWithInitial = defaultSettings.copy(initialFlextimeMinutes = initialMinutes)
        val workDate = LocalDate.of(2026, 9, 2)

        When("ein Arbeitstag mit 480 Min (+54 Min Gleitzeit) erfasst wird") {
            val day = createDurationDay(workDate, 480)
            val balance = calculateFlextime(listOf(day), settingsWithInitial)

            Then("wird der Startsaldo korrekt im Gesamtsaldo berücksichtigt (120 + 54 = 174 Min)") {
                balance.initialMinutes shouldBe 120
                balance.earnedMinutes shouldBe 54L
                balance.totalMinutes shouldBe 174L
            }
        }

        When("ein negativer Startsaldo von -180 Minuten vorliegt") {
            val settingsNegative = defaultSettings.copy(initialFlextimeMinutes = -180)
            val day = createDurationDay(workDate, 480)
            val balance = calculateFlextime(listOf(day), settingsNegative)

            Then("wird der Gesamtsaldo korrekt mit dem negativen Startsaldo verrechnet (-180 + 54 = -126 Min)") {
                balance.initialMinutes shouldBe -180
                balance.earnedMinutes shouldBe 54L
                balance.totalMinutes shouldBe -126L
            }
        }
    }
})
