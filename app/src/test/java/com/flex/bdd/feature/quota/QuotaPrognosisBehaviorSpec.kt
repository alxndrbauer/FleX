package com.flex.bdd.feature.quota

import com.flex.domain.model.DEFAULT_WORK_DAYS
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class QuotaPrognosisBehaviorSpec : BehaviorSpec({

    val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
    val calculateQuota = CalculateQuotaUseCase(calculateDayWorkTime)

    val defaultSettings = Settings(
        dailyWorkMinutes = 426,
        monthlyWorkMinutes = 8520,
        officeQuotaPercent = 40,
        officeQuotaMinDays = 8
    )

    fun createOfficeWorkDay(date: LocalDate): WorkDay {
        val start = LocalTime.of(8, 0)
        return WorkDay(
            id = date.toEpochDay(),
            date = date,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(
                TimeBlock(
                    id = date.toEpochDay(),
                    workDayId = date.toEpochDay(),
                    startTime = start,
                    endTime = start.plusMinutes(426),
                    location = WorkLocation.OFFICE,
                    isDuration = true
                )
            )
        )
    }

    Given("die Berechnung verbleibender Arbeitstage im Monat") {

        When("ein bereits vergangener Monat betrachtet wird") {
            val pastMonth = YearMonth.now().minusMonths(1)
            val status = calculateQuota(
                workDays = emptyList(),
                settings = defaultSettings,
                yearMonth = pastMonth
            )

            Then("beträgt die Anzahl verbleibender Arbeitstage exakt 0") {
                status.remainingWorkDays shouldBe 0
            }
        }

        When("ein zukünftiger voller Kalendermonat mit Standard-Arbeitstagen (Mo-Fr) betrachtet wird") {
            // Mai 2030: 31 Tage, startet am Mittwoch
            // Werktage (Mo–Fr): 1.–3. Mai (3) + 4 volle Wochen (20) = 23 Tage
            val futureMonth = YearMonth.of(2030, 5)
            val status = calculateQuota(
                workDays = emptyList(),
                settings = defaultSettings,
                yearMonth = futureMonth
            )

            Then("werden alle Werktage (Montag bis Freitag) des Monats gezählt (23 Tage)") {
                status.remainingWorkDays shouldBe 23
            }
        }

        When("ein zukünftiger Monat mit individueller WorkTimeRule für eine 4-Tage-Woche (Mo-Do) vorliegt") {
            // Mai 2030 mit Mo–Do:
            // 1.–2. Mai (2) + 4 Wochen à 4 Tage (16) = 18 Tage
            val futureMonth = YearMonth.of(2030, 5)
            val fourDayWeekRule = WorkTimeRule(
                id = 1L,
                validFrom = YearMonth.of(2030, 1),
                dailyWorkMinutes = 480,
                monthlyWorkMinutes = 8640,
                workDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)
            )

            val status = calculateQuota(
                workDays = emptyList(),
                settings = defaultSettings,
                yearMonth = futureMonth,
                workTimeRules = listOf(fourDayWeekRule)
            )

            Then("werden nur die aktiven Arbeitstage der Regel gezählt (18 Tage)") {
                status.remainingWorkDays shouldBe 18
            }
        }

        When("der aktuelle laufende Monat betrachtet wird") {
            val currentMonth = YearMonth.now()
            val today = LocalDate.now()

            val status = calculateQuota(
                workDays = emptyList(),
                settings = defaultSettings,
                yearMonth = currentMonth
            )

            val expectedRemainingDays = (today.dayOfMonth + 1..currentMonth.lengthOfMonth()).count { day ->
                currentMonth.atDay(day).dayOfWeek in DEFAULT_WORK_DAYS
            }

            Then("werden nur noch die Arbeitstage ab dem Folgetag bis zum Monatsende gezählt") {
                status.remainingWorkDays shouldBe expectedRemainingDays
            }
        }
    }

    Given("die Berechnung erforderlicher Bürotage zur Erfüllung der Mindesttage-Quote bis Monatsende") {

        When("noch kein einziger Bürotag im Monat geleistet wurde") {
            val status = calculateQuota(
                workDays = emptyList(),
                settings = defaultSettings, // min 8 Tage
                yearMonth = YearMonth.of(2030, 5)
            )

            Then("entspricht der Fehlbestand der vollen Vorgabe (8 Tage)") {
                status.officeDays shouldBe 0
                status.requiredOfficeDaysForQuota shouldBe 8
            }
        }

        When("bereits 5 von 8 erforderlichen Bürotagen absolviert wurden") {
            val workDays = (1..5).map { day ->
                createOfficeWorkDay(LocalDate.of(2030, 5, day))
            }

            val status = calculateQuota(
                workDays = workDays,
                settings = defaultSettings,
                yearMonth = YearMonth.of(2030, 5)
            )

            Then("werden noch genau 3 weitere Bürotage benötigt (8 - 5 = 3)") {
                status.officeDays shouldBe 5
                status.requiredOfficeDaysForQuota shouldBe 3
            }
        }

        When("die Mindestanzahl von 8 Bürotagen exakt erreicht ist") {
            val workDays = (1..8).map { day ->
                createOfficeWorkDay(LocalDate.of(2030, 5, day))
            }

            val status = calculateQuota(
                workDays = workDays,
                settings = defaultSettings,
                yearMonth = YearMonth.of(2030, 5)
            )

            Then("sind keine weiteren Bürotage mehr notwendig (requiredOfficeDaysForQuota == 0)") {
                status.officeDays shouldBe 8
                status.requiredOfficeDaysForQuota shouldBe 0
            }
        }

        When("mehr Bürotage als das Mindestsoll geleistet wurden (z.B. 11 bei 8 Tagen Ziel)") {
            val workDays = (1..11).map { day ->
                createOfficeWorkDay(LocalDate.of(2030, 5, day))
            }

            val status = calculateQuota(
                workDays = workDays,
                settings = defaultSettings,
                yearMonth = YearMonth.of(2030, 5)
            )

            Then("bleibt der Wert bei 0 und wird nicht negativ") {
                status.officeDays shouldBe 11
                status.requiredOfficeDaysForQuota shouldBe 0
            }
        }
    }

    Given("die Machbarkeitsprognose für die Restlaufzeit des Monats") {

        When("die noch erforderlichen Bürotage kleiner oder gleich den verbleibenden Arbeitstagen sind") {
            // Mai 2030 hat 23 verbleibende Arbeitstage
            // 5 Bürotage geleistet -> 3 erforderlich
            val workDays = (1..5).map { day ->
                createOfficeWorkDay(LocalDate.of(2030, 5, day))
            }

            val status = calculateQuota(
                workDays = workDays,
                settings = defaultSettings,
                yearMonth = YearMonth.of(2030, 5)
            )

            Then("ist die Quote rechnerisch bis Monatsende noch problemlos erreichbar") {
                val isAchievable = status.requiredOfficeDaysForQuota <= status.remainingWorkDays
                isAchievable shouldBe true
            }
        }

        When("die noch erforderlichen Bürotage die verbleibenden Arbeitstage übersteigen") {
            // Angenommen ein Zukunftsmonat hätte nur noch 2 verbleibende Tage, aber 5 Tage werden benötigt
            // In einem vergangenen Monat ist remainingWorkDays = 0, required = 8
            val pastMonth = YearMonth.now().minusMonths(1)
            val status = calculateQuota(
                workDays = emptyList(),
                settings = defaultSettings,
                yearMonth = pastMonth
            )

            Then("ist die Quote im Monat rechnerisch nicht mehr erreichbar") {
                val isAchievable = status.requiredOfficeDaysForQuota <= status.remainingWorkDays
                isAchievable shouldBe false
            }
        }
    }
})
