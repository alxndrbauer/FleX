package com.flex.bdd.feature.planning

import com.flex.domain.model.DayType
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.ui.month.getMonthDayCellColor
import com.flex.ui.planning.getPlanningDayCellColor
import com.flex.ui.theme.HomeOfficeColor
import com.flex.ui.theme.OfficeColor
import com.flex.ui.theme.SickDayColor
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalTime

class MixedDayColorBehaviorSpec : BehaviorSpec({

    Given("ein bereits gebuchter Mischtag mit überwiegender Arbeitszeit im Büro (6h Büro, 2h Home-Office)") {
        // Tag hat 2h Home-Office (08:00 - 10:00) und 6h Büro (10:30 - 16:30)
        // workDay.location steht z. B. auf HOME_OFFICE (weil der erste Block Home-Office war)
        val date = LocalDate.of(2026, 11, 4) // Mittwoch, kein Feiertag
        val mixedDayOfficeDominant = WorkDay(
            id = 1L,
            date = date,
            dayType = DayType.WORK,
            location = WorkLocation.HOME_OFFICE,
            isPlanned = false,
            timeBlocks = listOf(
                TimeBlock(
                    id = 1L,
                    workDayId = 1L,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(10, 0),
                    location = WorkLocation.HOME_OFFICE
                ),
                TimeBlock(
                    id = 2L,
                    workDayId = 1L,
                    startTime = LocalTime.of(10, 30),
                    endTime = LocalTime.of(16, 30),
                    location = WorkLocation.OFFICE
                )
            )
        )

        When("die Farbe in der Monatsansicht (MonthView) bestimmt wird") {
            val monthColor = getMonthDayCellColor(date, mixedDayOfficeDominant)

            Then("ist die Farbe Büro (OfficeColor), da die Büroarbeitszeit überwiegt") {
                monthColor shouldBe OfficeColor.copy(alpha = 0.3f)
            }
        }

        When("die Farbe in der Planungsansicht (PlanView) bestimmt wird") {
            val planColor = getPlanningDayCellColor(date, mixedDayOfficeDominant, isWeekend = false)

            Then("sollte die Farbe wie in der Monatsansicht Büro (OfficeColor) sein") {
                planColor shouldBe OfficeColor.copy(alpha = 0.3f)
            }
        }
    }

    Given("ein bereits gebuchter Mischtag mit überwiegender Arbeitszeit im Home-Office (6h Home-Office, 2h Büro)") {
        // Tag hat 2h Büro (08:00 - 10:00) und 6h Home-Office (10:30 - 16:30)
        // workDay.location steht auf OFFICE (weil der erste Block Büro war)
        val date = LocalDate.of(2026, 11, 5) // Donnerstag
        val mixedDayHoDominant = WorkDay(
            id = 2L,
            date = date,
            dayType = DayType.WORK,
            location = WorkLocation.OFFICE,
            isPlanned = false,
            timeBlocks = listOf(
                TimeBlock(
                    id = 3L,
                    workDayId = 2L,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(10, 0),
                    location = WorkLocation.OFFICE
                ),
                TimeBlock(
                    id = 4L,
                    workDayId = 2L,
                    startTime = LocalTime.of(10, 30),
                    endTime = LocalTime.of(16, 30),
                    location = WorkLocation.HOME_OFFICE
                )
            )
        )

        When("die Farbe in der Monatsansicht (MonthView) bestimmt wird") {
            val monthColor = getMonthDayCellColor(date, mixedDayHoDominant)

            Then("ist die Farbe Home-Office (HomeOfficeColor), da Home-Office überwiegt") {
                monthColor shouldBe HomeOfficeColor.copy(alpha = 0.3f)
            }
        }

        When("die Farbe in der Planungsansicht (PlanView) bestimmt wird") {
            val planColor = getPlanningDayCellColor(date, mixedDayHoDominant, isWeekend = false)

            Then("sollte die Farbe wie in der Monatsansicht Home-Office (HomeOfficeColor) sein") {
                planColor shouldBe HomeOfficeColor.copy(alpha = 0.3f)
            }
        }
    }

    Given("ein gebuchter Krankheitstag (SICK_DAY) in der Planungsansicht") {
        val date = LocalDate.of(2026, 11, 6) // Freitag
        val sickDay = WorkDay(
            id = 3L,
            date = date,
            dayType = DayType.SICK_DAY,
            location = WorkLocation.OFFICE,
            isPlanned = false,
            timeBlocks = emptyList()
        )

        When("die Farbe in der Monatsansicht bestimmt wird") {
            val monthColor = getMonthDayCellColor(date, sickDay)

            Then("ist die Farbe Krankenstand (SickDayColor)") {
                monthColor shouldBe SickDayColor.copy(alpha = 0.3f)
            }
        }

        When("die Farbe in der Planungsansicht bestimmt wird") {
            val planColor = getPlanningDayCellColor(date, sickDay, isWeekend = false)

            Then("sollte die Farbe wie in der Monatsansicht Krankenstand (SickDayColor) sein") {
                planColor shouldBe SickDayColor.copy(alpha = 0.3f)
            }
        }
    }
})
