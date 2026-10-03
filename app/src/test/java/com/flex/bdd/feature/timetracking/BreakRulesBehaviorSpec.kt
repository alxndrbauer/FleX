package com.flex.bdd.feature.timetracking

import com.flex.domain.model.BreakViolationType
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkLocation
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CheckBreakViolationUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.time.LocalTime

class BreakRulesBehaviorSpec : BehaviorSpec({

    val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
    val checkBreakViolation = CheckBreakViolationUseCase()

    Given("ein Arbeitstag mit einer Gesamtarbeitszeit unter 6 Stunden") {
        val blocks = listOf(
            TimeBlock(
                id = 1,
                workDayId = 1,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(14, 0),
                location = WorkLocation.OFFICE
            )
        )

        When("die Arbeitszeit berechnet wird") {
            val result = calculateDayWorkTime(blocks)

            Then("beträgt die gesetzliche Mindestpause 0 Minuten") {
                result.breakMinutes shouldBe 0L
            }

            Then("entspricht das Netto exakt dem Brutto (300 Minuten)") {
                result.grossMinutes shouldBe 300L
                result.netMinutes shouldBe 300L
            }
        }
    }

    Given("ein Arbeitstag mit einer Gesamtarbeitszeit ab 6 Stunden") {
        val blocks = listOf(
            TimeBlock(
                id = 1,
                workDayId = 1,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(16, 0),
                location = WorkLocation.OFFICE
            )
        )

        When("die Arbeitszeit berechnet wird") {
            val result = calculateDayWorkTime(blocks)

            Then("wird die gesetzliche Mindestpause von 30 Minuten abgezogen") {
                result.breakMinutes shouldBe 30L
            }

            Then("beträgt das Netto 450 Minuten (480 Brutto minus 30 Pause)") {
                result.grossMinutes shouldBe 480L
                result.netMinutes shouldBe 450L
            }
        }
    }

    Given("ein Arbeitstag mit einer Gesamtarbeitszeit über 9 Stunden") {
        val blocks = listOf(
            TimeBlock(
                id = 1,
                workDayId = 1,
                startTime = LocalTime.of(7, 0),
                endTime = LocalTime.of(17, 0),
                location = WorkLocation.OFFICE
            )
        )

        When("die Arbeitszeit berechnet wird") {
            val result = calculateDayWorkTime(blocks)

            Then("wird die gesetzliche Mindestpause von 45 Minuten abgezogen") {
                result.breakMinutes shouldBe 45L
            }

            Then("beträgt das Netto 555 Minuten") {
                result.grossMinutes shouldBe 600L
                result.netMinutes shouldBe 555L
            }
        }
    }

    Given("ein Arbeitstag mit ausreichender realer Pause") {

        When("zwei Blöcke 130 Minuten Pause dazwischen aufweisen") {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 50), endTime = LocalTime.of(12, 0), location = WorkLocation.HOME_OFFICE),
                TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(14, 10), endTime = LocalTime.of(16, 40), location = WorkLocation.HOME_OFFICE)
            )
            val result = calculateDayWorkTime(blocks)

            Then("wird die reale Pause von 130 Minuten erfasst") {
                result.breakMinutes shouldBe 130L
            }

            Then("erfolgt kein zusätzlicher Pausenabzug (Netto = Brutto = 340 Min)") {
                result.grossMinutes shouldBe 340L
                result.netMinutes shouldBe 340L
            }
        }

        When("bei 7 Stunden Arbeit eine echte Pause von 60 Minuten vorliegt") {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.OFFICE),
                TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(13, 0), endTime = LocalTime.of(16, 0), location = WorkLocation.OFFICE)
            )
            val result = calculateDayWorkTime(blocks)

            Then("deckt die Pause (60 Min) die geforderten 30 Min voll ab ohne Abzug") {
                result.grossMinutes shouldBe 420L
                result.breakMinutes shouldBe 60L
                result.netMinutes shouldBe 420L
            }
        }
    }

    Given("ein Arbeitstag über 6 Stunden mit unzureichender realer Pause") {
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.OFFICE),
            TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(12, 10), endTime = LocalTime.of(15, 30), location = WorkLocation.OFFICE)
        )

        When("die Arbeitszeit berechnet wird") {
            val result = calculateDayWorkTime(blocks)

            Then("beträgt die effektive Pause 30 Minuten") {
                result.breakMinutes shouldBe 30L
            }

            Then("wird die Differenz auf 30 Minuten (20 Min zusätzlich) vom Netto abgezogen") {
                result.grossMinutes shouldBe 440L
                result.netMinutes shouldBe 420L
            }
        }
    }

    Given("kontinuierliche Arbeitszeit über 6 Stunden ohne qualifizierende Pause") {

        When("ein einzelner Block über 6 Stunden (06:00 bis 12:15 = 375 Min) vorliegt") {
            val blocks = listOf(
                TimeBlock(1, 1, LocalTime.of(6, 0), LocalTime.of(12, 15)),
                TimeBlock(2, 1, LocalTime.of(13, 0), LocalTime.of(15, 0))
            )
            val result = checkBreakViolation(blocks)

            Then("löst CheckBreakViolationUseCase den Verstoß CONTINUOUS_WORK_EXCEEDS_6H aus") {
                result.skipped.shouldBeFalse()
                result.violations.map { it.type } shouldContain BreakViolationType.CONTINUOUS_WORK_EXCEEDS_6H
                val violation = result.violations.first { it.type == BreakViolationType.CONTINUOUS_WORK_EXCEEDS_6H }
                violation.continuousWorkMinutes shouldBe 375L
            }
        }

        When("zwei Blöcke durch eine nicht qualifizierende Pause von nur 14 Minuten getrennt sind") {
            val blocks = listOf(
                TimeBlock(1, 1, LocalTime.of(8, 0), LocalTime.of(14, 1)),
                TimeBlock(2, 1, LocalTime.of(14, 15), LocalTime.of(16, 0))
            )
            val result = checkBreakViolation(blocks)

            Then("werden die Blöcke zusammenhängend bewertet und lösen CONTINUOUS_WORK_EXCEEDS_6H aus") {
                result.skipped.shouldBeFalse()
                result.violations.map { it.type } shouldContain BreakViolationType.CONTINUOUS_WORK_EXCEEDS_6H
            }
        }
    }

    Given("unzureichende Gesamtpause bei >= 6h bzw. > 9h") {

        When("die Gesamtarbeit >= 6h ist, aber die qualifizierende Pause < 30 Minuten beträgt") {
            val blocks = listOf(
                TimeBlock(1, 1, LocalTime.of(8, 0), LocalTime.of(12, 0)),
                TimeBlock(2, 1, LocalTime.of(12, 20), LocalTime.of(15, 0))
            )
            val result = checkBreakViolation(blocks)

            Then("wird der Verstoß INSUFFICIENT_TOTAL_BREAK ausgelöst") {
                result.skipped.shouldBeFalse()
                result.violations.map { it.type } shouldContain BreakViolationType.INSUFFICIENT_TOTAL_BREAK
                val violation = result.violations.first { it.type == BreakViolationType.INSUFFICIENT_TOTAL_BREAK }
                violation.actualBreakMinutes shouldBe 20L
                violation.requiredBreakMinutes shouldBe 30L
            }
        }

        When("die Gesamtarbeit > 9h ist, aber die qualifizierende Pause < 45 Minuten beträgt") {
            val blocks = listOf(
                TimeBlock(1, 1, LocalTime.of(5, 0), LocalTime.of(13, 0)),
                TimeBlock(2, 1, LocalTime.of(13, 30), LocalTime.of(15, 30))
            )
            val result = checkBreakViolation(blocks)

            Then("wird der Verstoß INSUFFICIENT_TOTAL_BREAK_9H ausgelöst") {
                result.skipped.shouldBeFalse()
                result.violations.map { it.type } shouldContain BreakViolationType.INSUFFICIENT_TOTAL_BREAK_9H
                val violation = result.violations.first { it.type == BreakViolationType.INSUFFICIENT_TOTAL_BREAK_9H }
                violation.actualBreakMinutes shouldBe 30L
                violation.requiredBreakMinutes shouldBe 45L
            }
        }
    }
})
