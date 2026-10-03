package com.flex.bdd.feature.timetracking

import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkLocation
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import java.time.LocalTime

class MaxWorkHoursBehaviorSpec : BehaviorSpec({

    val calculateDayWorkTime = CalculateDayWorkTimeUseCase()

    Given("ein regulärer Arbeitstag mit einer Arbeitszeit von über 10 Stunden") {
        val blocks = listOf(
            TimeBlock(
                id = 1,
                workDayId = 1,
                startTime = LocalTime.of(7, 0),
                endTime = LocalTime.of(18, 0),
                location = WorkLocation.OFFICE
            )
        )

        When("die Arbeitszeitberechnung für einen regulären Tag durchgeführt wird (isBusinessTrip = false)") {
            val result = calculateDayWorkTime(blocks, isBusinessTrip = false)

            Then("beträgt die Bruttoarbeitszeit 660 Minuten") {
                result.grossMinutes shouldBe 660L
            }

            Then("wird die gesetzliche Mindestpause von 45 Minuten ermittelt") {
                result.breakMinutes shouldBe 45L
            }

            Then("wird das Netto strikt auf die gesetzliche Höchstarbeitszeit von 600 Minuten gekappt") {
                result.netMinutes shouldBe 600L
            }

            Then("wird exceedsMaxHours auf true gesetzt") {
                result.exceedsMaxHours.shouldBeTrue()
            }
        }
    }

    Given("ein regulärer Arbeitstag mit zwei Blöcken und über 10 Stunden Gesamtarbeitszeit") {
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(7, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.OFFICE),
            TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(13, 0), endTime = LocalTime.of(19, 30), location = WorkLocation.OFFICE)
        )

        When("die Arbeitszeit berechnet wird") {
            val result = calculateDayWorkTime(blocks, isBusinessTrip = false)

            Then("beträgt die Bruttoarbeitszeit 690 Minuten bei 60 Minuten Pause") {
                result.grossMinutes shouldBe 690L
                result.breakMinutes shouldBe 60L
            }

            Then("wird das Netto auf 600 Minuten gekappt und exceedsMaxHours ist true") {
                result.netMinutes shouldBe 600L
                result.exceedsMaxHours.shouldBeTrue()
            }
        }
    }

    Given("ein Arbeitstag im Rahmen einer Dienstreise mit über 10 Stunden Arbeitszeit") {
        val tripBlocks = listOf(
            TimeBlock(
                id = 1,
                workDayId = 1,
                startTime = LocalTime.of(7, 0),
                endTime = LocalTime.of(18, 0),
                location = WorkLocation.OFFICE
            )
        )

        When("die Arbeitszeitberechnung mit isBusinessTrip = true ausgeführt wird") {
            val result = calculateDayWorkTime(tripBlocks, isBusinessTrip = true)

            Then("beträgt die Bruttoarbeitszeit 660 Minuten") {
                result.grossMinutes shouldBe 660L
            }

            Then("wird das Netto NICHT auf 600 Minuten gekappt, sondern ungekappt berechnet (615 Minuten)") {
                result.breakMinutes shouldBe 45L
                result.netMinutes shouldBe 615L
            }

            Then("bleibt exceedsMaxHours auf false") {
                result.exceedsMaxHours.shouldBeFalse()
            }
        }
    }

    Given("eine Dienstreise mit einem Dauereintrag über 11 Stunden") {
        val durationBlocks = listOf(
            TimeBlock(
                id = 1,
                workDayId = 1,
                startTime = LocalTime.of(0, 0),
                endTime = LocalTime.of(11, 0),
                isDuration = true,
                location = WorkLocation.OFFICE
            )
        )

        When("die Arbeitszeitberechnung mit isBusinessTrip = true ausgeführt wird") {
            val result = calculateDayWorkTime(durationBlocks, isBusinessTrip = true)

            Then("wird das volle Netto von 660 Minuten ohne Kappung angerechnet") {
                result.grossMinutes shouldBe 660L
                result.netMinutes shouldBe 660L
                result.breakMinutes shouldBe 0L
            }

            Then("ist exceedsMaxHours false") {
                result.exceedsMaxHours.shouldBeFalse()
            }
        }
    }
})
