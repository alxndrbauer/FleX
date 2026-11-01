package com.flex.bdd.feature.flextime

import com.flex.domain.model.FlextimeBalance
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class FlextimeThresholdWarningSpec : BehaviorSpec({

    Given("die Companion-Helfer der Gleitzeit-Schwelle") {
        When("die Schwelle als Konstante abgefragt wird") {
            Then("beträgt sie 1500 Minuten") {
                FlextimeBalance.WARNING_THRESHOLD_MINUTES shouldBe 1500L
            }
        }

        When("die Companion-Helfer mit Grenz- und Extremwerten aufgerufen werden") {
            Then("entscheiden sie die Schwellen exakt, auch bei Long.MIN_VALUE") {
                FlextimeBalance.exceedsPositiveThreshold(1500L) shouldBe false
                FlextimeBalance.exceedsPositiveThreshold(1501L) shouldBe true
                FlextimeBalance.exceedsNegativeThreshold(-1500L) shouldBe false
                FlextimeBalance.exceedsNegativeThreshold(-1501L) shouldBe true
                FlextimeBalance.isOutsideThreshold(0L) shouldBe false
                FlextimeBalance.isOutsideThreshold(Long.MIN_VALUE) shouldBe true
                FlextimeBalance.isOutsideThreshold(Long.MAX_VALUE) shouldBe true
            }

            Then("liefert die Warnmeldung exakte Texte in beide Richtungen") {
                FlextimeBalance.warningMessage(1560L) shouldBe "Hinweis: Gleitzeitsaldo liegt über +25h (+1h 0m darüber)"
                FlextimeBalance.warningMessage(-1830L) shouldBe "Warnung: Gleitzeitsaldo liegt unter -25h (-5h 30m darunter)"
            }
        }
    }

    Given("ein Gleitzeitsaldo innerhalb der ±25h-Grenze") {
        listOf(0L, 1500L, -1500L).forEach { minutes ->
            When("der Saldo $minutes Minuten beträgt") {
                val balance = FlextimeBalance(totalMinutes = minutes)

                Then("liegt er nicht außerhalb der Schwelle") {
                    balance.isOutsideThreshold shouldBe false
                }

                Then("wird weder ein Überschreiten nach oben noch nach unten erkannt") {
                    balance.exceedsPositiveThreshold shouldBe false
                    balance.exceedsNegativeThreshold shouldBe false
                }

                Then("gibt es weder Warnmeldung noch Kurz-Badge") {
                    balance.thresholdWarningMessage shouldBe null
                    balance.shortWarningBadge shouldBe null
                }
            }
        }
    }

    Given("ein Gleitzeitsaldo über +25h") {
        listOf(
            1501L to "1m",
            1560L to "1h 0m",
            1830L to "5h 30m"
        ).forEach { (minutes, excess) ->
            When("der Saldo $minutes Minuten beträgt") {
                val balance = FlextimeBalance(totalMinutes = minutes)

                Then("wird das Überschreiten nach oben erkannt und ist außerhalb der Schwelle") {
                    balance.exceedsPositiveThreshold shouldBe true
                    balance.exceedsNegativeThreshold shouldBe false
                    balance.isOutsideThreshold shouldBe true
                }

                Then("zeigt das Kurz-Badge '⚠ > +25h'") {
                    balance.shortWarningBadge shouldBe "⚠ > +25h"
                }

                Then("nennt die Warnmeldung die 25h-Grenze und die exakte Überschreitung ($excess)") {
                    val message = balance.thresholdWarningMessage
                    message shouldContain "+25h"
                    message shouldContain "(+$excess darüber)"
                    message shouldBe "Hinweis: Gleitzeitsaldo liegt über +25h (+$excess darüber)"
                }
            }
        }
    }

    Given("ein Gleitzeitsaldo unter -25h") {
        listOf(
            -1501L to "1m",
            -1560L to "1h 0m",
            -1830L to "5h 30m"
        ).forEach { (minutes, deficit) ->
            When("der Saldo $minutes Minuten beträgt") {
                val balance = FlextimeBalance(totalMinutes = minutes)

                Then("wird das Unterschreiten nach unten erkannt und ist außerhalb der Schwelle") {
                    balance.exceedsNegativeThreshold shouldBe true
                    balance.exceedsPositiveThreshold shouldBe false
                    balance.isOutsideThreshold shouldBe true
                }

                Then("zeigt das Kurz-Badge '⚠ < -25h'") {
                    balance.shortWarningBadge shouldBe "⚠ < -25h"
                }

                Then("nennt die Warnmeldung die -25h-Grenze und das exakte Defizit ($deficit)") {
                    val message = balance.thresholdWarningMessage
                    message shouldContain "-25h"
                    message shouldContain "(-$deficit darunter)"
                    message shouldBe "Warnung: Gleitzeitsaldo liegt unter -25h (-$deficit darunter)"
                }
            }
        }
    }
})
