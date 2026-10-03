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

class SaturdayBonusBehaviorSpec : BehaviorSpec({

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
        dayType: DayType
    ): WorkDay {
        val start = LocalTime.of(8, 0)
        return WorkDay(
            id = date.toEpochDay(),
            date = date,
            dayType = dayType,
            timeBlocks = if (minutes > 0) listOf(
                TimeBlock(
                    id = date.toEpochDay(),
                    workDayId = date.toEpochDay(),
                    startTime = start,
                    endTime = start.plusMinutes(minutes),
                    isDuration = true
                )
            ) else emptyList()
        )
    }

    Given("Samstagsarbeit mit Bonusregelung (DayType.SATURDAY_BONUS)") {
        val saturday = LocalDate.of(2026, 9, 5) // Samstag

        When("480 Minuten (8 Stunden) gearbeitet werden") {
            val day = createDurationDay(saturday, 480, DayType.SATURDAY_BONUS)
            val balance = calculateFlextime(listOf(day), defaultSettings)

            Then("gehen 100% der Arbeitszeit (+480 Min) in das Gleitzeitkonto") {
                balance.earnedMinutes shouldBe 480L
                balance.totalMinutes shouldBe 480L
            }

            Then("geht ein 50% Bonus (+240 Min) in das Überstundenkonto") {
                balance.earnedOvertimeMinutes shouldBe 240L
                balance.overtimeMinutes shouldBe 240L
            }
        }

        When("eine ungerade Minutenzahl von 330 Minuten (5,5 Stunden) gearbeitet wird") {
            val day = createDurationDay(saturday, 330, DayType.SATURDAY_BONUS)
            val balance = calculateFlextime(listOf(day), defaultSettings)

            Then("gehen 330 Minuten in das Gleitzeitkonto") {
                balance.earnedMinutes shouldBe 330L
            }

            Then("wird der 50% Bonus korrekt gerundet (+165 Min) auf das Überstundenkonto verbucht") {
                balance.earnedOvertimeMinutes shouldBe 165L
                balance.overtimeMinutes shouldBe 165L
            }
        }

        When("0 Minuten am Samstag mit SATURDAY_BONUS erfasst sind") {
            val day = createDurationDay(saturday, 0, DayType.SATURDAY_BONUS)
            val balance = calculateFlextime(listOf(day), defaultSettings)

            Then("beträgt das Gleitzeit-Delta 0 Minuten") {
                balance.earnedMinutes shouldBe 0L
            }

            Then("beträgt das Überstunden-Delta 0 Minuten") {
                balance.earnedOvertimeMinutes shouldBe 0L
            }
        }

        When("bereits Startsalden vorhanden sind (initialFlextime=60, initialOvertime=120)") {
            val settingsWithInitial = defaultSettings.copy(
                initialFlextimeMinutes = 60,
                initialOvertimeMinutes = 120
            )
            val day = createDurationDay(saturday, 480, DayType.SATURDAY_BONUS)
            val balance = calculateFlextime(listOf(day), settingsWithInitial)

            Then("werden beide Salden zu ihren Startsalden addiert") {
                balance.totalMinutes shouldBe 540L // 60 + 480
                balance.overtimeMinutes shouldBe 360L // 120 + 240
            }
        }
    }

    Given("der Unterschied zwischen regulärem Samstag (DayType.WORK) und Bonus-Samstag (DayType.SATURDAY_BONUS)") {
        val saturday = LocalDate.of(2026, 9, 5)

        When("an einem Samstag DayType.WORK mit 240 Min erfasst wird") {
            val day = createDurationDay(saturday, 240, DayType.WORK)
            val balance = calculateFlextime(listOf(day), defaultSettings)

            Then("zählen 100% (240 Min) als Gleitzeit, da Samstag ein freier Tag ist") {
                balance.earnedMinutes shouldBe 240L
            }

            Then("entsteht kein Überstundenbonus (0 Min)") {
                balance.earnedOvertimeMinutes shouldBe 0L
            }
        }

        When("an einem Samstag DayType.SATURDAY_BONUS mit 240 Min erfasst wird") {
            val day = createDurationDay(saturday, 240, DayType.SATURDAY_BONUS)
            val balance = calculateFlextime(listOf(day), defaultSettings)

            Then("zählen 100% (240 Min) als Gleitzeit") {
                balance.earnedMinutes shouldBe 240L
            }

            Then("entsteht zusätzlich der 50% Bonus (+120 Min) auf dem Überstundenkonto") {
                balance.earnedOvertimeMinutes shouldBe 120L
            }
        }
    }
})
