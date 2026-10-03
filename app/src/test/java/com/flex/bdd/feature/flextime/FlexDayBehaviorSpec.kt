package com.flex.bdd.feature.flextime

import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
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

        When("die Konten für den Überstunden-Abbau berechnet werden") {
            val balance = calculateFlextime(
                workDays = listOf(overtimeDay),
                settings = defaultSettings,
                yearMonth = testMonth
            )

            Then("wird das volle Tagessoll (-426 Min) vom Überstundenkonto abgezogen") {
                balance.earnedOvertimeMinutes shouldBe -426L
                balance.overtimeMinutes shouldBe -426L
            }

            Then("bleibt das Gleitzeitkonto völlig neutral (0 Min Delta)") {
                balance.earnedMinutes shouldBe 0L
                balance.totalMinutes shouldBe 0L
            }

            Then("bleibt das Monatssoll (targetMinutes) unverändert") {
                balance.targetMinutes shouldBe 22L * 426L
            }
        }

        When("ein Überstunden-Abbau-Tag bei bestehendem Überstundenguthaben (+600 Min) genommen wird") {
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
    }
})
