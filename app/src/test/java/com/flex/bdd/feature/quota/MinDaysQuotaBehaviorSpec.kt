package com.flex.bdd.feature.quota

import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class MinDaysQuotaBehaviorSpec : BehaviorSpec({

    val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
    val calculateQuota = CalculateQuotaUseCase(calculateDayWorkTime)

    val defaultSettings = Settings(
        dailyWorkMinutes = 426, // 7h 06m
        monthlyWorkMinutes = 8520, // 20 Tage * 426 min
        officeQuotaPercent = 40,
        officeQuotaMinDays = 8 // 8 Tage Mindest-Bürotage
    )
    val testMonth = YearMonth.of(2026, 9)

    fun createWorkDay(
        date: LocalDate,
        location: WorkLocation,
        hasEnd: Boolean = true
    ): WorkDay {
        val start = LocalTime.of(8, 0)
        return WorkDay(
            id = date.toEpochDay(),
            date = date,
            location = location,
            dayType = DayType.WORK,
            timeBlocks = listOf(
                TimeBlock(
                    id = date.toEpochDay(),
                    workDayId = date.toEpochDay(),
                    startTime = start,
                    endTime = if (hasEnd) start.plusMinutes(426) else null,
                    location = location,
                    isDuration = true
                )
            )
        )
    }

    Given("eine Mindesttage-Quote von 8 Bürotagen pro Monat") {

        When("10 Bürotage im Monat absolviert wurden (Übererfüllung)") {
            val workDays = (1..10).map { day ->
                createWorkDay(LocalDate.of(2026, 9, day), WorkLocation.OFFICE)
            }

            val status = calculateQuota(
                workDays = workDays,
                settings = defaultSettings,
                yearMonth = testMonth
            )

            Then("werden 10 Bürotage erfasst") {
                status.officeDays shouldBe 10
            }

            Then("ist die Mindesttage-Quote erfüllt (daysQuotaMet == true)") {
                status.daysQuotaMet.shouldBeTrue()
            }

            Then("sind keine weiteren Bürotage erforderlich (requiredOfficeDaysForQuota == 0)") {
                status.requiredOfficeDaysForQuota shouldBe 0
            }

            Then("gilt die Gesamtquote als erfüllt") {
                status.quotaMet.shouldBeTrue()
            }
        }

        When("exakt 8 Bürotage absolviert wurden (Punktlandung)") {
            val workDays = (1..8).map { day ->
                createWorkDay(LocalDate.of(2026, 9, day), WorkLocation.OFFICE)
            }

            val status = calculateQuota(
                workDays = workDays,
                settings = defaultSettings,
                yearMonth = testMonth
            )

            Then("ist die Mindesttage-Quote exakt erreicht") {
                status.officeDays shouldBe 8
                status.daysQuotaMet.shouldBeTrue()
                status.requiredOfficeDaysForQuota shouldBe 0
            }
        }

        When("nur 6 Bürotage absolviert wurden und 10 Home-Office-Tage anfallen (Untererfüllung)") {
            val workDays = (1..6).map { day ->
                createWorkDay(LocalDate.of(2026, 9, day), WorkLocation.OFFICE)
            } + (7..16).map { day ->
                createWorkDay(LocalDate.of(2026, 9, day), WorkLocation.HOME_OFFICE)
            }

            val status = calculateQuota(
                workDays = workDays,
                settings = defaultSettings,
                yearMonth = testMonth
            )

            Then("werden 6 Bürotage und 10 Home-Office-Tage gezählt") {
                status.officeDays shouldBe 6
                status.homeOfficeDays shouldBe 10
            }

            Then("ist die Mindesttage-Quote nicht erfüllt (daysQuotaMet == false)") {
                status.daysQuotaMet.shouldBeFalse()
            }

            Then("zeigt requiredOfficeDaysForQuota die Differenz von 2 Tagen an") {
                // 8 verlangt - 6 absolviert = 2 fehlend
                status.requiredOfficeDaysForQuota shouldBe 2
            }
        }

        When("ein Tag nur einen laufenden Zeiterfassungsblock ohne Endzeit hat") {
            val runningBlockDay = createWorkDay(
                date = LocalDate.of(2026, 9, 1),
                location = WorkLocation.OFFICE,
                hasEnd = false
            )

            val status = calculateQuota(
                workDays = listOf(runningBlockDay),
                settings = defaultSettings,
                yearMonth = testMonth
            )

            Then("zählt der Tag noch nicht als vollständiger Bürotag") {
                status.officeDays shouldBe 0
                status.daysQuotaMet.shouldBeFalse()
                status.requiredOfficeDaysForQuota shouldBe 8
            }
        }
    }

    Given("ein abweichendes individuelles Mindesttage-Ziel über Methoden-Parameter") {

        When("das Mindesttage-Ziel auf 12 Tage angehoben wird und 8 Bürotage vorliegen") {
            val workDays = (1..8).map { day ->
                createWorkDay(LocalDate.of(2026, 9, day), WorkLocation.OFFICE)
            }

            val status = calculateQuota(
                workDays = workDays,
                settings = defaultSettings,
                yearMonth = testMonth,
                quotaMinDays = 12
            )

            Then("gilt die Quote bei 8 Bürotagen als nicht erfüllt") {
                status.officeDays shouldBe 8
                status.daysQuotaMet.shouldBeFalse()
            }

            Then("beträgt die Differenz 4 Tage (12 - 8)") {
                status.requiredOfficeDaysForQuota shouldBe 4
            }
        }
    }
})
