package com.flex.bdd.feature.timetracking

import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CheckTimeBlockOverlapUseCase
import com.flex.domain.usecase.OverlappingBlockPair
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.LocalTime

class TimeBlockManagementBehaviorSpec : BehaviorSpec({

    val checkOverlap = CheckTimeBlockOverlapUseCase()
    val calculateDayWorkTime = CalculateDayWorkTimeUseCase()

    Given("die Überlappungsprüfung mit CheckTimeBlockOverlapUseCase") {

        When("zwei Zeitblöcke sich zeitlich überschneiden") {
            val block1 = TimeBlock(
                id = 1,
                workDayId = 1,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(12, 0),
                location = WorkLocation.OFFICE
            )
            val block2 = TimeBlock(
                id = 2,
                workDayId = 1,
                startTime = LocalTime.of(11, 30),
                endTime = LocalTime.of(15, 0),
                location = WorkLocation.OFFICE
            )
            val blocks = listOf(block1, block2)

            Then("wird eine Kollision erkannt") {
                checkOverlap(blocks).shouldBeTrue()
            }

            Then("liefert findOverlaps exakt das kollidierende Paar") {
                val overlaps = checkOverlap.findOverlaps(blocks)
                overlaps shouldHaveSize 1
                overlaps.first() shouldBe OverlappingBlockPair(block1, block2)
            }
        }

        When("zwei Zeitblöcke nahtlos aneinandergrenzen (Back-to-back)") {
            val block1 = TimeBlock(
                id = 1,
                workDayId = 1,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(12, 0),
                location = WorkLocation.OFFICE
            )
            val block2 = TimeBlock(
                id = 2,
                workDayId = 1,
                startTime = LocalTime.of(12, 0),
                endTime = LocalTime.of(16, 0),
                location = WorkLocation.OFFICE
            )
            val blocks = listOf(block1, block2)

            Then("wird keine Kollision erkannt") {
                checkOverlap(blocks).shouldBeFalse()
            }

            Then("ist die Liste der Überlappungen leer") {
                checkOverlap.findOverlaps(blocks).shouldBeEmpty()
            }
        }

        When("ein offener Zeitblock gegen einen neuen Block geprüft wird") {
            val now = LocalTime.of(12, 0)
            val runningBlock = TimeBlock(
                id = 1,
                workDayId = 1,
                startTime = LocalTime.of(8, 0),
                endTime = null,
                location = WorkLocation.OFFICE
            )
            val newBlock = TimeBlock(
                id = 2,
                workDayId = 1,
                startTime = LocalTime.of(11, 0),
                endTime = LocalTime.of(14, 0),
                location = WorkLocation.OFFICE
            )

            Then("wird eine Kollision relativ zur aktuellen Uhrzeit erkannt") {
                checkOverlap(listOf(runningBlock, newBlock), now = now).shouldBeTrue()
            }
        }

        When("ein Block mit excludeBlockId geprüft wird (Bearbeiten des eigenen Blocks)") {
            val existingBlock = TimeBlock(
                id = 42,
                workDayId = 1,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(12, 0),
                location = WorkLocation.OFFICE
            )

            Then("wird mit excludeBlockId keine Kollision mit sich selbst gemeldet") {
                val conflicting = checkOverlap.findOverlap(
                    start = LocalTime.of(8, 0),
                    end = LocalTime.of(11, 30),
                    existingBlocks = listOf(existingBlock),
                    excludeBlockId = 42L
                )
                conflicting.shouldBeNull()
            }

            Then("wird ohne excludeBlockId die Kollision gefunden") {
                val conflicting = checkOverlap.findOverlap(
                    start = LocalTime.of(10, 0),
                    end = LocalTime.of(14, 0),
                    existingBlocks = listOf(existingBlock)
                )
                conflicting shouldBe existingBlock
            }
        }
    }

    Given("das Aktualisieren und Anpassen von Zeitblöcken") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        coEvery { workDayRepository.saveTimeBlock(any()) } returns 10L

        When("ein bestehender Zeitblock neue Start- und Endzeiten erhält") {
            val originalBlock = TimeBlock(
                id = 10L,
                workDayId = 2L,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(12, 0),
                location = WorkLocation.OFFICE
            )
            val updatedBlock = originalBlock.copy(
                startTime = LocalTime.of(8, 30),
                endTime = LocalTime.of(13, 0),
                location = WorkLocation.HOME_OFFICE
            )

            val savedId = workDayRepository.saveTimeBlock(updatedBlock)

            Then("wird der Block mit unveränderter ID und den neuen Zeiten gespeichert") {
                savedId shouldBe 10L
                val blockSlot = slot<TimeBlock>()
                coVerify { workDayRepository.saveTimeBlock(capture(blockSlot)) }
                blockSlot.captured.id shouldBe 10L
                blockSlot.captured.startTime shouldBe LocalTime.of(8, 30)
                blockSlot.captured.endTime shouldBe LocalTime.of(13, 0)
                blockSlot.captured.location shouldBe WorkLocation.HOME_OFFICE
            }
        }

        When("Zeitblöcke mit ungerundeten Zeiten für den Arbeitstag angepasst werden") {
            val rawBlocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 52), endTime = LocalTime.of(12, 1), location = WorkLocation.OFFICE),
                TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(13, 3), endTime = LocalTime.of(16, 38), location = WorkLocation.OFFICE)
            )

            val adjusted = CalculateDayWorkTimeUseCase.adjustTimeBlocks(rawBlocks)

            Then("wird der erste Start auf 08:50 abgerundet") {
                adjusted[0].startTime shouldBe LocalTime.of(8, 50)
                adjusted[0].endTime shouldBe LocalTime.of(12, 1)
            }

            Then("wird das letzte Ende auf 16:40 aufgerundet") {
                adjusted[1].startTime shouldBe LocalTime.of(13, 3)
                adjusted[1].endTime shouldBe LocalTime.of(16, 40)
            }
        }
    }

    Given("Dauereinträge vs. Von-Bis-Einträge") {

        When("ein Dauereintrag parallel zu einem regulären Von-Bis-Block existiert") {
            val normalBlock = TimeBlock(
                id = 1,
                workDayId = 1,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(12, 0),
                isDuration = false,
                location = WorkLocation.OFFICE
            )
            val durationBlock = TimeBlock(
                id = 2,
                workDayId = 1,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(14, 0),
                isDuration = true,
                location = WorkLocation.HOME_OFFICE
            )

            Then("ignoriert die Überlappungsprüfung den Dauereintrag") {
                checkOverlap(listOf(normalBlock, durationBlock)).shouldBeFalse()
                checkOverlap.findOverlaps(listOf(normalBlock, durationBlock)).shouldBeEmpty()
            }
        }

        When("zwei Dauereinträge mit identischer Zeitspanne vorliegen") {
            val d1 = TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0), isDuration = true, location = WorkLocation.OFFICE)
            val d2 = TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0), isDuration = true, location = WorkLocation.OFFICE)

            Then("wird keine Kollision festgestellt") {
                checkOverlap(listOf(d1, d2)).shouldBeFalse()
            }
        }

        When("die Arbeitszeit eines reinen Dauereintrags von 7 Stunden berechnet wird") {
            val durationBlock = TimeBlock(
                id = 1,
                workDayId = 1,
                startTime = LocalTime.of(0, 0),
                endTime = LocalTime.of(7, 0),
                isDuration = true,
                location = WorkLocation.HOME_OFFICE
            )
            val result = calculateDayWorkTime(listOf(durationBlock))

            Then("erfolgt kein gesetzlicher Pausenabzug (Brutto = Netto = 420 Min)") {
                result.grossMinutes shouldBe 420L
                result.breakMinutes shouldBe 0L
                result.netMinutes shouldBe 420L
            }
        }

        When("die Arbeitszeit eines regulären Von-Bis-Eintrags von 7 Stunden berechnet wird") {
            val regularBlock = TimeBlock(
                id = 1,
                workDayId = 1,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(15, 0),
                isDuration = false,
                location = WorkLocation.OFFICE
            )
            val result = calculateDayWorkTime(listOf(regularBlock))

            Then("wird die gesetzliche 30-Minuten-Mindestpause abgezogen") {
                result.grossMinutes shouldBe 420L
                result.breakMinutes shouldBe 30L
                result.netMinutes shouldBe 390L
            }
        }
    }
})
