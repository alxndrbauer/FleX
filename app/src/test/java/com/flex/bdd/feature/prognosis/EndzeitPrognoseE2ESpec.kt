package com.flex.bdd.feature.prognosis

import com.flex.domain.model.ProjectedEndTime
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkLocation
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateProjectedEndTimeUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalTime

/**
 * End-to-End BDD Specification for FleX Endzeit-Prognose (Feierabend-Uhrzeit).
 *
 * Covers 4 Tiers:
 * - Tier 1: Feature Coverage (F1: Model & Formatting, F2: ArbZG UseCase Calculation, F3: Edge Cases & Nullability)
 * - Tier 2: Boundary & Corner Cases (Exact 6h, 6h+1m, 9h, 9h+1m, 1m remaining, midnight wrap-around, break boundaries)
 * - Tier 3: Cross-Feature Combinations (Multi-block gaps, business trips minutengenau, pause mode vs resume)
 * - Tier 4: Real-World Scenarios (Full workday lifecycle: 08:00 start, coffee break, lunch break, target 7h 06m)
 */
class EndzeitPrognoseE2ESpec : BehaviorSpec({

    val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
    val calculateProjectedEndTime = CalculateProjectedEndTimeUseCase(calculateDayWorkTime)

    // =========================================================================
    // TIER 1: FEATURE COVERAGE
    // =========================================================================

    // -------------------------------------------------------------------------
    // F1: Domain Calculation Model (ProjectedEndTime)
    // -------------------------------------------------------------------------
    Given("Tier 1 - F1: Domain Calculation Model ProjectedEndTime") {

        When("eine Prognose mit ausstehender Pflichtpause vorliegt") {
            val prognosis = ProjectedEndTime(
                projectedEndTime = LocalTime.of(16, 30),
                projectedEndTimeWithoutBreak = LocalTime.of(16, 0),
                pendingBreakMinutes = 30L,
                takenBreakMinutes = 0L,
                requiredBreakMinutes = 30L,
                remainingNetMinutes = 240L
            )

            Then("ist hasPendingBreak true") {
                prognosis.hasPendingBreak.shouldBeTrue()
            }

            Then("formatiert formatWithBreakHint die Hauptzeit mit Netto-Hinweis in Klammern") {
                prognosis.formatWithBreakHint() shouldBe "16:30 (ohne Pause 16:00)"
            }

            Then("formatiert formatCompact nur die primäre Feierabend-Uhrzeit") {
                prognosis.formatCompact() shouldBe "16:30"
            }
        }

        When("eine Prognose ohne ausstehende Pflichtpause vorliegt") {
            val prognosis = ProjectedEndTime(
                projectedEndTime = LocalTime.of(16, 0),
                projectedEndTimeWithoutBreak = LocalTime.of(16, 0),
                pendingBreakMinutes = 0L,
                takenBreakMinutes = 30L,
                requiredBreakMinutes = 30L,
                remainingNetMinutes = 180L
            )

            Then("ist hasPendingBreak false") {
                prognosis.hasPendingBreak.shouldBeFalse()
            }

            Then("formatiert formatWithBreakHint nur die reine Uhrzeit ohne Klammerzusatz") {
                prognosis.formatWithBreakHint() shouldBe "16:00"
            }

            Then("formatiert formatCompact die Uhrzeit identisch") {
                prognosis.formatCompact() shouldBe "16:00"
            }
        }

        When("die Hilfsfunktion formatTime aufgerufen wird") {
            Then("werden einstellige Stunden und Minuten mit führender Null formatiert") {
                ProjectedEndTime.formatTime(LocalTime.of(8, 5)) shouldBe "08:05"
                ProjectedEndTime.formatTime(LocalTime.of(0, 0)) shouldBe "00:00"
                ProjectedEndTime.formatTime(LocalTime.of(23, 59)) shouldBe "23:59"
            }
        }

        When("die Eigenschaften des Models abgefragt werden") {
            val prognosis = ProjectedEndTime(
                projectedEndTime = LocalTime.of(17, 15),
                projectedEndTimeWithoutBreak = LocalTime.of(16, 30),
                pendingBreakMinutes = 45L,
                takenBreakMinutes = 0L,
                requiredBreakMinutes = 45L,
                remainingNetMinutes = 540L
            )

            Then("halten alle Felder die exakt übergebenen Werte") {
                prognosis.projectedEndTime shouldBe LocalTime.of(17, 15)
                prognosis.projectedEndTimeWithoutBreak shouldBe LocalTime.of(16, 30)
                prognosis.pendingBreakMinutes shouldBe 45L
                prognosis.takenBreakMinutes shouldBe 0L
                prognosis.requiredBreakMinutes shouldBe 45L
                prognosis.remainingNetMinutes shouldBe 540L
            }
        }
    }

    // -------------------------------------------------------------------------
    // F2: Deterministic ArbZG Calculation UseCase
    // -------------------------------------------------------------------------
    Given("Tier 1 - F2: Deterministic ArbZG UseCase CalculateProjectedEndTimeUseCase") {

        When("ein regulärer Arbeitstag über 6h (z. B. 7h 06m = 426 Min) ohne bisherige Pause gestartet wird") {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = null, location = WorkLocation.OFFICE)
            )
            val result = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = 426,
                now = LocalTime.of(8, 0)
            )

            Then("wird die gesetzliche 30-Minuten-Pause gefordert und als ausstehend berechnet") {
                result.shouldNotBeNull()
                result.requiredBreakMinutes shouldBe 30L
                result.takenBreakMinutes shouldBe 0L
                result.pendingBreakMinutes shouldBe 30L
            }

            Then("beträgt die prognostizierte Endzeit 15:36 (ohne Pause 15:06)") {
                result.shouldNotBeNull()
                result.projectedEndTime shouldBe LocalTime.of(15, 36)
                result.projectedEndTimeWithoutBreak shouldBe LocalTime.of(15, 6)
                result.formatWithBreakHint() shouldBe "15:36 (ohne Pause 15:06)"
            }
        }

        When("ein Teilzeittag bis einschließlich 6h (z. B. 5h = 300 Min) erfasst wird") {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(9, 0), endTime = null, location = WorkLocation.HOME_OFFICE)
            )
            val result = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = 300,
                now = LocalTime.of(9, 0)
            )

            Then("ist keine gesetzliche Pause erforderlich (0 Minuten)") {
                result.shouldNotBeNull()
                result.requiredBreakMinutes shouldBe 0L
                result.pendingBreakMinutes shouldBe 0L
                result.hasPendingBreak.shouldBeFalse()
            }

            Then("entspricht die prognostizierte Endzeit exakt der Netto-Endzeit (14:00)") {
                result.shouldNotBeNull()
                result.projectedEndTime shouldBe LocalTime.of(14, 0)
                result.projectedEndTimeWithoutBreak shouldBe LocalTime.of(14, 0)
                result.formatWithBreakHint() shouldBe "14:00"
            }
        }

        When("ein langer Arbeitstag über 9h (z. B. 9h 30m = 570 Min) ohne Pause erfasst wird") {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(7, 0), endTime = null, location = WorkLocation.OFFICE)
            )
            val result = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = 570,
                now = LocalTime.of(7, 0)
            )

            Then("wird die gesetzliche 45-Minuten-Pause gefordert und als ausstehend berechnet") {
                result.shouldNotBeNull()
                result.requiredBreakMinutes shouldBe 45L
                result.pendingBreakMinutes shouldBe 45L
            }

            Then("beträgt die prognostizierte Endzeit 17:15 (ohne Pause 16:30)") {
                result.shouldNotBeNull()
                result.projectedEndTime shouldBe LocalTime.of(17, 15)
                result.projectedEndTimeWithoutBreak shouldBe LocalTime.of(16, 30)
                result.formatWithBreakHint() shouldBe "17:15 (ohne Pause 16:30)"
            }
        }

        When("die gesetzliche 30-minütige Pause bereits vollständig genommen wurde") {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.OFFICE),
                // 30 Min Pause: 12:00 bis 12:30
                TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(12, 30), endTime = null, location = WorkLocation.OFFICE)
            )
            val result = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = 426,
                now = LocalTime.of(13, 0)
            )

            Then("wird die genommene Pause von 30 Minuten vollständig angerechnet") {
                result.shouldNotBeNull()
                result.takenBreakMinutes shouldBe 30L
                result.pendingBreakMinutes shouldBe 0L
                result.hasPendingBreak.shouldBeFalse()
            }

            Then("wird kein zusätzlicher Pausenaufschlag berechnet (Ende 15:36)") {
                result.shouldNotBeNull()
                result.projectedEndTime shouldBe LocalTime.of(15, 36)
                result.projectedEndTimeWithoutBreak shouldBe LocalTime.of(15, 36)
                result.formatWithBreakHint() shouldBe "15:36"
            }
        }

        When("eine Teilpause von 15 Minuten genommen wurde") {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.OFFICE),
                // 15 Min Pause: 12:00 bis 12:15
                TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(12, 15), endTime = null, location = WorkLocation.OFFICE)
            )
            val result = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = 426,
                now = LocalTime.of(13, 0)
            )

            Then("wird die genommene Pause erfasst und die Restpause beträgt 15 Minuten") {
                result.shouldNotBeNull()
                result.takenBreakMinutes shouldBe 15L
                result.pendingBreakMinutes shouldBe 15L
                result.hasPendingBreak.shouldBeTrue()
            }

            Then("unterscheiden sich Endzeit und Netto-Endzeit um exakt die restlichen 15 Minuten") {
                result.shouldNotBeNull()
                result.projectedEndTime shouldBe LocalTime.of(15, 36)
                result.projectedEndTimeWithoutBreak shouldBe LocalTime.of(15, 21)
                result.formatWithBreakHint() shouldBe "15:36 (ohne Pause 15:21)"
            }
        }

        When("bei einem >9h-Tag bereits 30 Minuten Pause vorliegen") {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(7, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.OFFICE),
                TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(12, 30), endTime = null, location = WorkLocation.OFFICE)
            )
            val result = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = 570,
                now = LocalTime.of(13, 0)
            )

            Then("beträgt die verbleibende Pflichtpause 15 Minuten") {
                result.shouldNotBeNull()
                result.requiredBreakMinutes shouldBe 45L
                result.takenBreakMinutes shouldBe 30L
                result.pendingBreakMinutes shouldBe 15L
                result.projectedEndTime shouldBe LocalTime.of(17, 15)
                result.projectedEndTimeWithoutBreak shouldBe LocalTime.of(17, 0)
                result.formatWithBreakHint() shouldBe "17:15 (ohne Pause 17:00)"
            }
        }
    }

    // -------------------------------------------------------------------------
    // F3: Edge Case Handling & Nullability
    // -------------------------------------------------------------------------
    Given("Tier 1 - F3: Edge Case Handling und Nullability") {

        When("die Zeiterfassung nicht aktiv ist (alle Blöcke abgeschlossen)") {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.OFFICE)
            )
            val result = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = 426,
                now = LocalTime.of(12, 30)
            )

            Then("liefert der UseCase null zurück") {
                result.shouldBeNull()
            }
        }

        When("die Liste der Zeitblöcke leer ist") {
            val result = calculateProjectedEndTime(
                timeBlocks = emptyList(),
                dailyTargetMinutes = 426,
                now = LocalTime.of(8, 0)
            )

            Then("liefert der UseCase null zurück") {
                result.shouldBeNull()
            }
        }

        When("das Tagessoll 0 Minuten beträgt (z. B. arbeitsfreier Tag)") {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = null, location = WorkLocation.HOME_OFFICE)
            )
            val result = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = 0,
                now = LocalTime.of(8, 30)
            )

            Then("liefert der UseCase null zurück") {
                result.shouldBeNull()
            }
        }

        When("das Tagessoll negativ ist") {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = null, location = WorkLocation.HOME_OFFICE)
            )
            val result = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = -60,
                now = LocalTime.of(8, 30)
            )

            Then("liefert der UseCase null zurück") {
                result.shouldBeNull()
            }
        }

        When("das Tagessoll bereits exakt erreicht wurde") {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.OFFICE),
                TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(12, 30), endTime = null, location = WorkLocation.OFFICE)
            )
            // 240m + 186m = 426m net
            val result = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = 426,
                now = LocalTime.of(15, 36)
            )

            Then("liefert der UseCase null zurück (Status Soll erreicht)") {
                result.shouldBeNull()
            }
        }

        When("das Tagessoll bereits überschritten wurde (Überstunden)") {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = null, location = WorkLocation.OFFICE)
            )
            // 8:00 bis 16:30 = 8h 30m brutto - 30m Pause = 8h (480m) net > 426m
            val result = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = 426,
                now = LocalTime.of(16, 30)
            )

            Then("liefert der UseCase null zurück") {
                result.shouldBeNull()
            }
        }
    }

    // =========================================================================
    // TIER 2: BOUNDARY & CORNER CASES
    // =========================================================================
    Given("Tier 2: Grenzwerte und Randfälle (Boundary & Corner Cases)") {

        When("das Tagessoll exakt der 6-Stunden-Grenze entspricht (360 Minuten)") {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = null, location = WorkLocation.OFFICE)
            )
            val result = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = 360,
                now = LocalTime.of(8, 0)
            )

            Then("ist keine gesetzliche Pause erforderlich (ArbZG §4 erst ab mehr als 6 Stunden)") {
                result.shouldNotBeNull()
                result.requiredBreakMinutes shouldBe 0L
                result.pendingBreakMinutes shouldBe 0L
                result.hasPendingBreak.shouldBeFalse()
            }

            Then("fällt die Feierabendzeit exakt auf 14:00 ohne Pausenabzug") {
                result.shouldNotBeNull()
                result.projectedEndTime shouldBe LocalTime.of(14, 0)
                result.formatWithBreakHint() shouldBe "14:00"
            }
        }

        When("das Tagessoll die 6-Stunden-Grenze um genau 1 Minute überschreitet (361 Minuten)") {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = null, location = WorkLocation.OFFICE)
            )
            val result = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = 361,
                now = LocalTime.of(8, 0)
            )

            Then("greift sofort die volle 30-minütige gesetzliche Pause") {
                result.shouldNotBeNull()
                result.requiredBreakMinutes shouldBe 30L
                result.pendingBreakMinutes shouldBe 30L
                result.hasPendingBreak.shouldBeTrue()
            }

            Then("beträgt die Feierabendzeit 14:31 (ohne Pause 14:01)") {
                result.shouldNotBeNull()
                result.projectedEndTime shouldBe LocalTime.of(14, 31)
                result.projectedEndTimeWithoutBreak shouldBe LocalTime.of(14, 1)
                result.formatWithBreakHint() shouldBe "14:31 (ohne Pause 14:01)"
            }
        }

        When("das Tagessoll exakt der 9-Stunden-Grenze entspricht (540 Minuten)") {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = null, location = WorkLocation.OFFICE)
            )
            val result = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = 540,
                now = LocalTime.of(8, 0)
            )

            Then("ist weiterhin nur die 30-Minuten-Pause fällig (45 Minuten erst ab mehr als 9 Stunden)") {
                result.shouldNotBeNull()
                result.requiredBreakMinutes shouldBe 30L
                result.pendingBreakMinutes shouldBe 30L
            }

            Then("beträgt die Feierabendzeit 17:30 (ohne Pause 17:00)") {
                result.shouldNotBeNull()
                result.projectedEndTime shouldBe LocalTime.of(17, 30)
                result.projectedEndTimeWithoutBreak shouldBe LocalTime.of(17, 0)
                result.formatWithBreakHint() shouldBe "17:30 (ohne Pause 17:00)"
            }
        }

        When("das Tagessoll die 9-Stunden-Grenze um genau 1 Minute überschreitet (541 Minuten)") {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = null, location = WorkLocation.OFFICE)
            )
            val result = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = 541,
                now = LocalTime.of(8, 0)
            )

            Then("greift die 45-minütige gesetzliche Pause") {
                result.shouldNotBeNull()
                result.requiredBreakMinutes shouldBe 45L
                result.pendingBreakMinutes shouldBe 45L
            }

            Then("beträgt die Feierabendzeit 17:46 (ohne Pause 17:01)") {
                result.shouldNotBeNull()
                result.projectedEndTime shouldBe LocalTime.of(17, 46)
                result.projectedEndTimeWithoutBreak shouldBe LocalTime.of(17, 1)
                result.formatWithBreakHint() shouldBe "17:46 (ohne Pause 17:01)"
            }
        }

        When("genau 1 Minute bis zum Tagessoll verbleibt") {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.OFFICE),
                TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(12, 30), endTime = null, location = WorkLocation.OFFICE)
            )
            // 240m + 185m = 425m net. Target = 426m -> 1m remaining.
            val result = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = 426,
                now = LocalTime.of(15, 35)
            )

            Then("wird die Feierabendzeit exakt 1 Minute in der Zukunft berechnet") {
                result.shouldNotBeNull()
                result.remainingNetMinutes shouldBe 1L
                result.projectedEndTime shouldBe LocalTime.of(15, 36)
                result.formatWithBreakHint() shouldBe "15:36"
            }
        }

        When("die Arbeitszeit über Mitternacht hinausreicht (Midnight Wrap-Around)") {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(20, 0), endTime = null, location = WorkLocation.HOME_OFFICE)
            )
            val result = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = 300, // 5 hours -> ends at 01:00
                now = LocalTime.of(20, 0)
            )

            Then("berechnet der UseCase die Uhrzeit des nächsten Tages ohne Fehler") {
                result.shouldNotBeNull()
                result.projectedEndTime shouldBe LocalTime.of(1, 0)
                result.formatWithBreakHint() shouldBe "01:00"
            }
        }

        When("die genommene Pause genau die Grenzen 29 Min, 30 Min und 35 Min berührt") {
            // 29 Min Pause: 12:00 bis 12:29
            val blocks29 = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.OFFICE),
                TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(12, 29), endTime = null, location = WorkLocation.OFFICE)
            )
            val result29 = calculateProjectedEndTime(blocks29, 426, LocalTime.of(12, 29))

            // 35 Min Pause: 12:00 bis 12:35 (mehr als 30 Min erforderlich)
            val blocks35 = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.OFFICE),
                TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(12, 35), endTime = null, location = WorkLocation.OFFICE)
            )
            val result35 = calculateProjectedEndTime(blocks35, 426, LocalTime.of(12, 35))

            Then("verbleibt bei 29 Min Pause genau 1 Minute Restpause") {
                result29.shouldNotBeNull()
                result29.takenBreakMinutes shouldBe 29L
                result29.pendingBreakMinutes shouldBe 1L
                result29.hasPendingBreak.shouldBeTrue()
            }

            Then("ist bei 35 Min Pause die Restpause 0 und wird nicht negativ") {
                result35.shouldNotBeNull()
                result35.takenBreakMinutes shouldBe 35L
                result35.pendingBreakMinutes shouldBe 0L
                result35.hasPendingBreak.shouldBeFalse()
            }
        }

        When("der aktive Block gerade erst gestartet wurde (Dauer 0 Minuten)") {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 30), endTime = null, location = WorkLocation.OFFICE)
            )
            val result = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = 426,
                now = LocalTime.of(8, 30)
            )

            Then("berechnet der UseCase die verbleibende Bruttozeit ab der Startzeit") {
                result.shouldNotBeNull()
                result.remainingNetMinutes shouldBe 426L
                result.projectedEndTime shouldBe LocalTime.of(16, 6)
                result.projectedEndTimeWithoutBreak shouldBe LocalTime.of(15, 36)
            }
        }
    }

    // =========================================================================
    // TIER 3: CROSS-FEATURE COMBINATIONS
    // =========================================================================
    Given("Tier 3: Feature-Kombinationen (Cross-Feature Combinations)") {

        When("mehrere Blöcke mit gestückelten Pausen vorliegen") {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(10, 0), location = WorkLocation.OFFICE),
                // Lücke 1: 10:00 bis 10:10 = 10 Min
                TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(10, 10), endTime = LocalTime.of(12, 0), location = WorkLocation.OFFICE),
                // Lücke 2: 12:00 bis 12:15 = 15 Min
                TimeBlock(id = 3, workDayId = 1, startTime = LocalTime.of(12, 15), endTime = null, location = WorkLocation.OFFICE)
            )
            val result = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = 426,
                now = LocalTime.of(13, 0)
            )

            Then("werden alle Lücken summiert (25 Min Pause genommen)") {
                result.shouldNotBeNull()
                result.takenBreakMinutes shouldBe 25L
            }

            Then("beträgt die noch fällige Restpause exakt 5 Minuten") {
                result.shouldNotBeNull()
                result.pendingBreakMinutes shouldBe 5L
                result.hasPendingBreak.shouldBeTrue()
                result.projectedEndTime shouldBe LocalTime.of(15, 36)
                result.projectedEndTimeWithoutBreak shouldBe LocalTime.of(15, 31)
                result.formatWithBreakHint() shouldBe "15:36 (ohne Pause 15:31)"
            }
        }

        When("eine Dienstreise (isBusinessTrip = true) erfasst wird") {
            val tripBlocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(7, 13), endTime = null, location = WorkLocation.OFFICE)
            )
            val result = calculateProjectedEndTime(
                timeBlocks = tripBlocks,
                dailyTargetMinutes = 300,
                now = LocalTime.of(9, 13),
                isBusinessTrip = true
            )

            Then("erfolgt keine 5-Minuten-Rundung, sondern eine minutengenaue Prognose") {
                // Bei 300 Min Tagessoll und Start um 07:13 endet der Arbeitstag exakt um 12:13
                result.shouldNotBeNull()
                result.projectedEndTime shouldBe LocalTime.of(12, 13)
                result.formatWithBreakHint() shouldBe "12:13"
            }
        }

        When("der Nutzer in den Pausenmodus wechselt und wieder fortsetzt") {
            // Zustand 1: Während der Pause (letzter Block beendet, kein aktiver Block)
            val pausedBlocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(11, 30), location = WorkLocation.OFFICE)
            )
            val pausedResult = calculateProjectedEndTime(pausedBlocks, 426, LocalTime.of(11, 45))

            // Zustand 2: Nach Fortsetzen um 12:00 (neuer Block mit endTime = null)
            val resumedBlocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(11, 30), location = WorkLocation.OFFICE),
                TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(12, 0), endTime = null, location = WorkLocation.OFFICE)
            )
            val resumedResult = calculateProjectedEndTime(resumedBlocks, 426, LocalTime.of(12, 0))

            Then("liefert der UseCase während der Pause null zurück") {
                pausedResult.shouldBeNull()
            }

            Then("wird nach dem Fortsetzen die 30-minütige Pause angerechnet und die Prognose sofort aktualisiert") {
                resumedResult.shouldNotBeNull()
                resumedResult.takenBreakMinutes shouldBe 30L
                resumedResult.pendingBreakMinutes shouldBe 0L
                resumedResult.projectedEndTime shouldBe LocalTime.of(15, 36)
                resumedResult.formatWithBreakHint() shouldBe "15:36"
            }
        }

        When("während aktiver Zeiterfassung das Tagessoll nach einer Pause erreicht wird") {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.OFFICE),
                TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(12, 30), endTime = null, location = WorkLocation.OFFICE)
            )
            val result = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = 420, // 7h Soll
                now = LocalTime.of(16, 0) // 240 Min + 210 Min = 450 Min Netto > 420 Min
            )

            Then("liefert der UseCase null zurück") {
                result.shouldBeNull()
            }
        }
    }

    // =========================================================================
    // TIER 4: REAL-WORLD SCENARIOS
    // =========================================================================
    Given("Tier 4: Reales Arbeitstag-Szenario (08:00 Start, Kaffeepause, Mittagspause, 7h 06m Soll)") {
        val targetMinutes = 426 // 7h 06m Standard-Tagessoll

        When("der Tagesablauf chronologisch durchschritten wird") {

            // Phase 1: 08:00 Einstempeln
            val blocksPhase1 = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = null, location = WorkLocation.OFFICE)
            )
            val prognosisPhase1 = calculateProjectedEndTime(blocksPhase1, targetMinutes, LocalTime.of(8, 0))

            // Phase 2: 10:00 Nach 2 Stunden ununterbrochener Arbeit
            val prognosisPhase2 = calculateProjectedEndTime(blocksPhase1, targetMinutes, LocalTime.of(10, 0))

            // Phase 3: 10:00 bis 10:15 Kaffeepause (Pausenmodus aktiv)
            val blocksPhase3 = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(10, 0), location = WorkLocation.OFFICE)
            )
            val prognosisPhase3 = calculateProjectedEndTime(blocksPhase3, targetMinutes, LocalTime.of(10, 10))

            // Phase 4: 10:15 Fortsetzen nach Kaffeepause (15 Min Pause genommen)
            val blocksPhase4 = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(10, 0), location = WorkLocation.OFFICE),
                TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(10, 15), endTime = null, location = WorkLocation.OFFICE)
            )
            val prognosisPhase4 = calculateProjectedEndTime(blocksPhase4, targetMinutes, LocalTime.of(10, 15))

            // Phase 5: 12:30 bis 13:00 Mittagspause (Pausenmodus)
            val blocksPhase5 = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(10, 0), location = WorkLocation.OFFICE),
                TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(10, 15), endTime = LocalTime.of(12, 30), location = WorkLocation.OFFICE)
            )
            val prognosisPhase5 = calculateProjectedEndTime(blocksPhase5, targetMinutes, LocalTime.of(12, 45))

            // Phase 6: 13:00 Fortsetzen nach Mittagspause (insgesamt 45 Min Pause genommen >= 30 Min erforderlich)
            val blocksPhase6 = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(10, 0), location = WorkLocation.OFFICE),
                TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(10, 15), endTime = LocalTime.of(12, 30), location = WorkLocation.OFFICE),
                TimeBlock(id = 3, workDayId = 1, startTime = LocalTime.of(13, 0), endTime = null, location = WorkLocation.OFFICE)
            )
            val prognosisPhase6 = calculateProjectedEndTime(blocksPhase6, targetMinutes, LocalTime.of(13, 0))

            // Phase 7: 15:51 Tagessoll erreicht
            val prognosisPhase7 = calculateProjectedEndTime(blocksPhase6, targetMinutes, LocalTime.of(15, 51))

            Then("zeigt Phase 1 (08:00) 15:36 (ohne Pause 15:06)") {
                prognosisPhase1.shouldNotBeNull()
                prognosisPhase1.projectedEndTime shouldBe LocalTime.of(15, 36)
                prognosisPhase1.formatWithBreakHint() shouldBe "15:36 (ohne Pause 15:06)"
            }

            Then("bleibt Phase 2 (10:00) stabil auf 15:36 (ohne Pause 15:06)") {
                prognosisPhase2.shouldNotBeNull()
                prognosisPhase2.projectedEndTime shouldBe LocalTime.of(15, 36)
                prognosisPhase2.formatWithBreakHint() shouldBe "15:36 (ohne Pause 15:06)"
            }

            Then("liefert Phase 3 während der Kaffeepause null") {
                prognosisPhase3.shouldBeNull()
            }

            Then("berücksichtigt Phase 4 (10:15) die 15-minütige Kaffeepause und zeigt 15:36 (ohne Pause 15:21)") {
                prognosisPhase4.shouldNotBeNull()
                prognosisPhase4.takenBreakMinutes shouldBe 15L
                prognosisPhase4.pendingBreakMinutes shouldBe 15L
                prognosisPhase4.projectedEndTime shouldBe LocalTime.of(15, 36)
                prognosisPhase4.projectedEndTimeWithoutBreak shouldBe LocalTime.of(15, 21)
                prognosisPhase4.formatWithBreakHint() shouldBe "15:36 (ohne Pause 15:21)"
            }

            Then("liefert Phase 5 während der Mittagspause null") {
                prognosisPhase5.shouldBeNull()
            }

            Then("erkennt Phase 6 (13:00) die vollständige Pflichterfüllung der Pause und zeigt 15:51 ohne Pausenhinweis") {
                prognosisPhase6.shouldNotBeNull()
                prognosisPhase6.takenBreakMinutes shouldBe 45L
                prognosisPhase6.pendingBreakMinutes shouldBe 0L
                prognosisPhase6.hasPendingBreak.shouldBeFalse()
                prognosisPhase6.projectedEndTime shouldBe LocalTime.of(15, 51)
                prognosisPhase6.formatWithBreakHint() shouldBe "15:51"
            }

            Then("liefert Phase 7 bei Feierabend (15:51) null") {
                prognosisPhase7.shouldBeNull()
            }
        }
    }
})
