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

class FlexDayBehaviorSpec : BehaviorSpec({

    val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
    val calculateFlextime = CalculateFlextimeUseCase(calculateDayWorkTime)

    val defaultSettings = Settings(
        dailyWorkMinutes = 426, // 7h 06min
        monthlyWorkMinutes = 8520, // 20 Tage * 426 Min
        initialFlextimeMinutes = 0,
        initialOvertimeMinutes = 0
    )
    val testMonth = YearMonth.of(2026, 9)

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

    Given("ein Gleittag (DayType.FLEX_DAY) an einem regulären Arbeitstag") {
        val flexDate = LocalDate.of(2026, 9, 8) // Dienstag
        val flexDay = createNeutralDay(flexDate, DayType.FLEX_DAY)

        When("die Gleitzeit für den Gleittag berechnet wird") {
            val balance = calculateFlextime(
                workDays = listOf(flexDay),
                settings = defaultSettings,
                yearMonth = testMonth
            )

            Then("wird das volle Tagessoll (-426 Min) vom Gleitzeitkonto abgezogen") {
                balance.earnedMinutes shouldBe -426L
                balance.totalMinutes shouldBe -426L
            }

            Then("bleibt das Überstundenkonto unberührt") {
                balance.earnedOvertimeMinutes shouldBe 0L
                balance.overtimeMinutes shouldBe 0L
            }

            Then("bleibt das reguläre Monatssoll (targetMinutes) unverändert") {
                // September 2026 hat 22 Werktage à 426 Min = 9372 Min
                balance.targetMinutes shouldBe 22L * 426L
            }
        }

        When("ein Gleittag bei bestehendem Startguthaben (+500 Min) genommen wird") {
            val settingsWithBalance = defaultSettings.copy(
                initialFlextimeMinutes = 500,
                initialOvertimeMinutes = 100
            )
            val balance = calculateFlextime(
                workDays = listOf(flexDay),
                settings = settingsWithBalance,
                yearMonth = testMonth
            )

            Then("verringert sich das Gleitzeitkonto um 426 Min auf verbleibende +74 Min") {
                balance.earnedMinutes shouldBe -426L
                balance.totalMinutes shouldBe 74L // 500 - 426 = 74
            }

            Then("bleibt das Überstundenkonto beim Anfangsstand von 100 Min") {
                balance.overtimeMinutes shouldBe 100L
                balance.earnedOvertimeMinutes shouldBe 0L
            }
        }

        When("zwei aufeinanderfolgende Gleittage genommen werden") {
            val day1 = createNeutralDay(LocalDate.of(2026, 9, 8), DayType.FLEX_DAY)
            val day2 = createNeutralDay(LocalDate.of(2026, 9, 9), DayType.FLEX_DAY)
            val balance = calculateFlextime(
                workDays = listOf(day1, day2),
                settings = defaultSettings,
                yearMonth = testMonth
            )

            Then("wird zweimal das Tagessoll (-852 Min) vom Gleitzeitkonto abgezogen") {
                balance.earnedMinutes shouldBe -852L // -2 * 426
                balance.totalMinutes shouldBe -852L
            }

            Then("bleibt das Überstundenkonto unverändert bei 0 Min") {
                balance.earnedOvertimeMinutes shouldBe 0L
            }
        }
    }

    Given("ein Überstunden-Abbau-Tag (DayType.OVERTIME_DAY) an einem Arbeitstag") {
        val overtimeDate = LocalDate.of(2026, 9, 10) // Donnerstag
        val overtimeDay = createNeutralDay(overtimeDate, DayType.OVERTIME_DAY)

        When("kein Überstundenguthaben vorhanden ist (0 Min)") {
            val balance = calculateFlextime(
                workDays = listOf(overtimeDay),
                settings = defaultSettings,
                yearMonth = testMonth
            )

            Then("wird das volle Tagessoll (-426 Min) vom Gleitzeitkonto abgezogen") {
                balance.earnedMinutes shouldBe -426L
                balance.totalMinutes shouldBe -426L
            }

            Then("bleibt das Überstundenkonto bei 0 Min") {
                balance.earnedOvertimeMinutes shouldBe 0L
                balance.overtimeMinutes shouldBe 0L
            }
        }

        When("das Überstundenguthaben das Tagessoll vollständig deckt (+600 Min)") {
            val settingsWithOvertime = defaultSettings.copy(
                initialFlextimeMinutes = 80,
                initialOvertimeMinutes = 600
            )
            val balance = calculateFlextime(
                workDays = listOf(overtimeDay),
                settings = settingsWithOvertime,
                yearMonth = testMonth
            )

            Then("sinkt das Überstundenkonto auf +174 Min (600 - 426 = 174)") {
                balance.earnedOvertimeMinutes shouldBe -426L
                balance.overtimeMinutes shouldBe 174L
            }

            Then("bleibt das Gleitzeitkonto unverändert beim Startguthaben von 80 Min") {
                balance.earnedMinutes shouldBe 0L
                balance.totalMinutes shouldBe 80L
            }
        }

        When("das Überstundenguthaben das Tagessoll nur teilweise deckt (+360 Min)") {
            val settingsWithOvertime = defaultSettings.copy(
                initialFlextimeMinutes = 80,
                initialOvertimeMinutes = 360
            )
            val balance = calculateFlextime(
                workDays = listOf(overtimeDay),
                settings = settingsWithOvertime,
                yearMonth = testMonth
            )

            Then("wird das Überstundenkonto auf 0 Min geleert") {
                balance.earnedOvertimeMinutes shouldBe -360L
                balance.overtimeMinutes shouldBe 0L
            }

            Then("wird der nicht gedeckte Rest (-66 Min = 426 - 360) vom Gleitzeitkonto abgezogen") {
                balance.earnedMinutes shouldBe -66L
                balance.totalMinutes shouldBe 14L // 80 - 66
            }
        }

        When("zwei Überstundentage hintereinander bei +600 Min Überstunden genommen werden") {
            val secondOvertimeDay = createNeutralDay(LocalDate.of(2026, 9, 11), DayType.OVERTIME_DAY)
            val balance = calculateFlextime(
                workDays = listOf(overtimeDay, secondOvertimeDay),
                settings = defaultSettings.copy(initialOvertimeMinutes = 600),
                yearMonth = testMonth
            )

            Then("deckt der erste Tag 426 Min, der zweite die restlichen 174 Min; 252 Min gehen vom Gleitzeitkonto ab") {
                balance.overtimeMinutes shouldBe 0L
                balance.earnedMinutes shouldBe -252L
                balance.totalMinutes shouldBe -252L
            }
        }

        When("das Überstundenkonto bereits negativ ist (-100 Min)") {
            val balance = calculateFlextime(
                workDays = listOf(overtimeDay),
                settings = defaultSettings.copy(initialOvertimeMinutes = -100),
                yearMonth = testMonth
            )

            Then("wird das volle Tagessoll vom Gleitzeitkonto abgezogen") {
                balance.earnedMinutes shouldBe -426L
                balance.earnedOvertimeMinutes shouldBe 0L
            }

            Then("bleibt das Überstundenkonto bei -100 Min") {
                balance.overtimeMinutes shouldBe -100L
            }
        }

        When("ein Samstagsbonus (120 Min netto, +60 Min Überstunden) später im Monat liegt und die Liste unsortiert übergeben wird") {
            val saturdayBonus = WorkDay(
                id = 1L,
                date = LocalDate.of(2026, 9, 12), // Samstag
                dayType = DayType.SATURDAY_BONUS,
                timeBlocks = listOf(TimeBlock(startTime = LocalTime.of(8, 0), endTime = LocalTime.of(10, 0)))
            )
            val balance = calculateFlextime(
                workDays = listOf(saturdayBonus, overtimeDay),
                settings = defaultSettings,
                yearMonth = testMonth
            )

            Then("deckt der spätere Samstagsbonus den früheren Überstundentag nicht: volles Tagessoll vom Gleitzeitkonto") {
                balance.earnedMinutes shouldBe -306L // -426 (Donnerstag) + 120 (Samstag)
                balance.overtimeMinutes shouldBe 60L
            }
        }
    }

    Given("Überstunden aus einem Vormonat (Samstagsbonus 120 Min netto, +60 Min Überstunden am 15.08.2026)") {
        val augustBonus = WorkDay(
            id = 2L,
            date = LocalDate.of(2026, 8, 15), // Samstag
            dayType = DayType.SATURDAY_BONUS,
            timeBlocks = listOf(TimeBlock(startTime = LocalTime.of(8, 0), endTime = LocalTime.of(10, 0)))
        )
        val septemberOvertimeDay = createNeutralDay(LocalDate.of(2026, 9, 10), DayType.OVERTIME_DAY)

        When("der Monatsanfang für September berechnet wird") {
            val startOvertime = calculateFlextime.overtimeBalanceAtStart(
                workDays = listOf(augustBonus, septemberOvertimeDay),
                settings = defaultSettings,
                yearMonth = testMonth
            )

            Then("ist das Überstundenguthaben 60 Min; Tage des Zielmonats zählen nicht dazu") {
                startOvertime shouldBe 60L
            }
        }

        When("der September mit diesem Startguthaben berechnet wird") {
            val startOvertime = calculateFlextime.overtimeBalanceAtStart(
                workDays = listOf(augustBonus, septemberOvertimeDay),
                settings = defaultSettings,
                yearMonth = testMonth
            )
            val september = calculateFlextime(
                workDays = listOf(septemberOvertimeDay),
                settings = defaultSettings.copy(initialOvertimeMinutes = startOvertime.toInt()),
                yearMonth = testMonth
            )

            Then("deckt der Überstundentag nur die 60 Min Guthaben; 366 Min gehen vom Gleitzeitkonto ab") {
                september.overtimeMinutes shouldBe 0L
                september.earnedMinutes shouldBe -366L
            }
        }
    }
})
