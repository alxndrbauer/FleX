package com.flex.bdd.feature.flextime

import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalTime

class OvertimeAccountBehaviorSpec : BehaviorSpec({

    val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
    val calculateFlextime = CalculateFlextimeUseCase(calculateDayWorkTime)

    val defaultSettings = Settings(
        dailyWorkMinutes = 426, // 7h 06min
        monthlyWorkMinutes = 8520,
        initialFlextimeMinutes = 0,
        initialOvertimeMinutes = 0
    )

    fun createDurationDay(
        date: LocalDate,
        minutes: Long,
        dayType: DayType = DayType.WORK
    ): WorkDay {
        val start = LocalTime.of(8, 0)
        return WorkDay(
            id = date.toEpochDay(),
            date = date,
            dayType = dayType,
            timeBlocks = listOf(
                TimeBlock(
                    id = date.toEpochDay(),
                    workDayId = date.toEpochDay(),
                    startTime = start,
                    endTime = start.plusMinutes(minutes),
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
            dayType = dayType,
            timeBlocks = emptyList()
        )
    }

    Given("die Konfiguration des initialen Überstundensaldos (initialOvertimeMinutes)") {

        When("ein positiver initialer Überstundensaldo von 300 Minuten in den Settings hinterlegt ist") {
            val settings = defaultSettings.copy(initialOvertimeMinutes = 300)
            val balance = calculateFlextime(emptyList(), settings)

            Then("wird der initiale Überstundensaldo im Überstundenkonto ausgewiesen") {
                balance.overtimeMinutes shouldBe 300L
            }

            Then("beträgt der zusätzlich erarbeitete Überstundenwert 0 Minuten") {
                balance.earnedOvertimeMinutes shouldBe 0L
            }
        }

        When("ein negativer initialer Überstundensaldo von -120 Minuten hinterlegt ist") {
            val settings = defaultSettings.copy(initialOvertimeMinutes = -120)
            val balance = calculateFlextime(emptyList(), settings)

            Then("wird der negative Saldo im Überstundenkonto korrekt abgebildet") {
                balance.overtimeMinutes shouldBe -120L
                balance.earnedOvertimeMinutes shouldBe 0L
            }
        }
    }

    Given("die strikte Trennung zwischen Gleitzeitkonto und Überstundenkonto") {
        val weekday = LocalDate.of(2026, 9, 2) // Mittwoch
        val initialSettings = defaultSettings.copy(
            initialFlextimeMinutes = 100,
            initialOvertimeMinutes = 200
        )

        When("an einem regulären Arbeitstag Überstunden geleistet werden (480 Min gearbeitet bei 426 Min Soll)") {
            // 480 - 426 = +54 Min Mehrarbeit
            val day = createDurationDay(weekday, 480, DayType.WORK)
            val balance = calculateFlextime(listOf(day), initialSettings)

            Then("wird die Mehrarbeit ausschließlich dem Gleitzeitkonto gutgeschrieben (+54 Min)") {
                balance.earnedMinutes shouldBe 54L
                balance.totalMinutes shouldBe 154L // 100 + 54
            }

            Then("bleibt das Überstundenkonto unberührt (0 Min Veränderung)") {
                balance.earnedOvertimeMinutes shouldBe 0L
                balance.overtimeMinutes shouldBe 200L // unverändert
            }
        }

        When("ein Überstunden-Abbau-Tag (DayType.OVERTIME_DAY) bei 200 Min Überstunden genommen wird") {
            val overtimeDay = createNeutralDay(weekday, DayType.OVERTIME_DAY)
            val balance = calculateFlextime(listOf(overtimeDay), initialSettings)

            Then("wird das Überstundenkonto vollständig aufgebraucht (-200 Min)") {
                balance.earnedOvertimeMinutes shouldBe -200L
                balance.overtimeMinutes shouldBe 0L
            }

            Then("wird der nicht gedeckte Rest (-226 Min = 426 - 200) vom Gleitzeitkonto abgezogen") {
                balance.earnedMinutes shouldBe -226L
                balance.totalMinutes shouldBe -126L // 100 - 226
            }
        }

        When("ein Gleittag (DayType.FLEX_DAY) genommen wird") {
            val flexDay = createNeutralDay(weekday, DayType.FLEX_DAY)
            val balance = calculateFlextime(listOf(flexDay), initialSettings)

            Then("wird das Tagessoll (-426 Min) vom Gleitzeitkonto abgezogen") {
                balance.earnedMinutes shouldBe -426L
                balance.totalMinutes shouldBe -326L // 100 - 426 = -326
            }

            Then("bleibt das Überstundenkonto unberührt") {
                balance.earnedOvertimeMinutes shouldBe 0L
                balance.overtimeMinutes shouldBe 200L // unverändert
            }
        }

        When("ein Überstundentag auf einen Tag ohne Tagessoll fällt (Samstag)") {
            val saturday = LocalDate.of(2026, 9, 5)
            val overtimeDay = createNeutralDay(saturday, DayType.OVERTIME_DAY)
            val balance = calculateFlextime(listOf(overtimeDay), initialSettings)

            Then("bleiben Gleitzeit- und Überstundenkonto unverändert") {
                balance.earnedMinutes shouldBe 0L
                balance.totalMinutes shouldBe 100L
                balance.earnedOvertimeMinutes shouldBe 0L
                balance.overtimeMinutes shouldBe 200L
            }
        }

        When("ein Überstundentag auf einen Feiertag fällt (Christi Himmelfahrt)") {
            val holiday = LocalDate.of(2026, 5, 14)
            val overtimeDay = createNeutralDay(holiday, DayType.OVERTIME_DAY)
            val balance = calculateFlextime(listOf(overtimeDay), initialSettings)

            Then("bleiben Gleitzeit- und Überstundenkonto unverändert") {
                balance.earnedMinutes shouldBe 0L
                balance.totalMinutes shouldBe 100L
                balance.earnedOvertimeMinutes shouldBe 0L
                balance.overtimeMinutes shouldBe 200L
            }
        }

        When("verschiedene Tage kombiniert werden (Mehrarbeit am Arbeitstag und Überstunden-Abbau)") {
            val day1 = createDurationDay(LocalDate.of(2026, 9, 2), 480, DayType.WORK) // +54 Gleitzeit, 0 ÜS
            val day2 = createNeutralDay(LocalDate.of(2026, 9, 3), DayType.OVERTIME_DAY) // 0 Gleitzeit, -426 ÜS
            val balance = calculateFlextime(listOf(day1, day2), initialSettings)

            Then("verbraucht der Überstundentag die 200 Min Überstunden, der Rest (-226) belastet das Gleitzeitkonto") {
                balance.earnedMinutes shouldBe -172L // 54 - 226
                balance.totalMinutes shouldBe -72L // 100 - 172
                balance.earnedOvertimeMinutes shouldBe -200L
                balance.overtimeMinutes shouldBe 0L
            }
        }
    }
})
