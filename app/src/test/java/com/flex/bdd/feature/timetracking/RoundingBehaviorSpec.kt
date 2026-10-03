package com.flex.bdd.feature.timetracking

import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkLocation
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.LocalTime

class RoundingBehaviorSpec : BehaviorSpec({

    val calculateDayWorkTime = CalculateDayWorkTimeUseCase()

    Given("ein Zeiterfassungsblock mit krummer Startzeit") {
        val block = TimeBlock(
            workDayId = 1,
            startTime = LocalTime.of(9, 12),
            endTime = LocalTime.of(17, 0),
            location = WorkLocation.OFFICE
        )

        When("die 5-Minuten-Rundung angewendet wird") {
            val adjusted = CalculateDayWorkTimeUseCase.adjustTimeBlocks(listOf(block))

            Then("wird die Startzeit auf die vorherige 5-Minuten-Grenze abgerundet (09:10)") {
                adjusted.first().startTime shouldBe LocalTime.of(9, 10)
            }
        }
    }

    Given("ein Zeiterfassungsblock mit krummer Endzeit") {
        val block = TimeBlock(
            workDayId = 1,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(17, 1),
            location = WorkLocation.OFFICE
        )

        When("die 5-Minuten-Rundung angewendet wird") {
            val adjusted = CalculateDayWorkTimeUseCase.adjustTimeBlocks(listOf(block))

            Then("wird die Endzeit auf die nächste 5-Minuten-Grenze aufgerundet (17:05)") {
                adjusted.first().endTime shouldBe LocalTime.of(17, 5)
            }
        }
    }

    Given("mehrere Blöcke an einem Arbeitstag") {
        val blocks = listOf(
            TimeBlock(workDayId = 1, startTime = LocalTime.of(8, 51), endTime = LocalTime.of(12, 1), location = WorkLocation.OFFICE),
            TimeBlock(workDayId = 1, startTime = LocalTime.of(13, 2), endTime = LocalTime.of(16, 38), location = WorkLocation.OFFICE)
        )

        When("die Blöcke angepasst werden") {
            val adjusted = CalculateDayWorkTimeUseCase.adjustTimeBlocks(blocks)

            Then("wird nur der allererste Start abgerundet (08:50)") {
                adjusted[0].startTime shouldBe LocalTime.of(8, 50)
                adjusted[0].endTime shouldBe LocalTime.of(12, 1) // Zwischenende ungerundet
            }

            Then("wird nur das allerletzte Ende aufgerundet (16:40)") {
                adjusted[1].startTime shouldBe LocalTime.of(13, 2) // Zwischenstart ungerundet
                adjusted[1].endTime shouldBe LocalTime.of(16, 40)
            }
        }
    }

    Given("ein Arbeitstag im Rahmen einer Dienstreise") {
        val tripBlocks = listOf(
            TimeBlock(workDayId = 1, startTime = LocalTime.of(7, 13), endTime = LocalTime.of(18, 47), location = WorkLocation.OFFICE)
        )

        When("die Arbeitszeitberechnung mit isBusinessTrip=true ausgeführt wird") {
            val result = calculateDayWorkTime(tripBlocks, isBusinessTrip = true)

            Then("erfolgt keine 5-Minuten-Rundung, sondern eine minutengenaue Erfassung") {
                // Brutto: 7:13 bis 18:47 = 11h 34m = 694 Minuten
                result.grossMinutes shouldBe 694L
            }
        }
    }

    Given("ein Dauereintrag ohne Von-Bis-Zeit") {
        val durationBlock = TimeBlock(
            workDayId = 1,
            startTime = LocalTime.of(0, 0),
            endTime = LocalTime.of(8, 12),
            isDuration = true,
            location = WorkLocation.HOME_OFFICE
        )

        When("die Blöcke angepasst werden") {
            val adjusted = CalculateDayWorkTimeUseCase.adjustTimeBlocks(listOf(durationBlock))

            Then("bleibt der Duration-Block exakt unverändert") {
                adjusted.first().startTime shouldBe LocalTime.of(0, 0)
                adjusted.first().endTime shouldBe LocalTime.of(8, 12)
            }
        }
    }
})
