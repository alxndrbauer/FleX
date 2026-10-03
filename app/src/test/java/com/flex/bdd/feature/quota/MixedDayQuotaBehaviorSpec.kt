package com.flex.bdd.feature.quota

import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.DayWorkTimeResult
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class MixedDayQuotaBehaviorSpec : BehaviorSpec({

    val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
    val calculateQuota = CalculateQuotaUseCase(calculateDayWorkTime)

    val defaultSettings = Settings(
        dailyWorkMinutes = 426,
        monthlyWorkMinutes = 8520,
        officeQuotaPercent = 40,
        officeQuotaMinDays = 8
    )
    val testMonth = YearMonth.of(2026, 9)

    Given("ein Mischtag mit Arbeitsblöcken im Büro und im Home-Office") {

        When("am Vormittag 3 Stunden Home-Office und am Nachmittag 5 Stunden Büro mit 30 Minuten Pause gearbeitet werden") {
            // Block 1: 08:00–11:00 (180 min) Home-Office
            // Pause:   11:00–11:30 (30 min)
            // Block 2: 11:30–16:30 (300 min) Büro
            // Brutto = 480 min. Pausenpflicht für 8h erfüllt -> Netto = 480 min.
            val mixedDay = WorkDay(
                id = 1L,
                date = LocalDate.of(2026, 9, 1),
                location = WorkLocation.OFFICE,
                dayType = DayType.WORK,
                timeBlocks = listOf(
                    TimeBlock(
                        id = 1L,
                        workDayId = 1L,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(11, 0),
                        location = WorkLocation.HOME_OFFICE
                    ),
                    TimeBlock(
                        id = 2L,
                        workDayId = 1L,
                        startTime = LocalTime.of(11, 30),
                        endTime = LocalTime.of(16, 30),
                        location = WorkLocation.OFFICE
                    )
                )
            )

            val status = calculateQuota(listOf(mixedDay), defaultSettings, testMonth)

            Then("zählt der Mischtag als Bürotag, da die Bürozeit (300 Min) die HO-Zeit (180 Min) überwiegt") {
                status.officeDays shouldBe 1
                status.homeOfficeDays shouldBe 0
            }

            Then("wird die Nettozeit proportional zur Bruttozeit verteilt (300 Min Büro, 180 Min Home-Office)") {
                status.officeMinutes shouldBe 300L
                status.homeOfficeMinutes shouldBe 180L
            }
        }

        When("am Vormittag 5 Stunden Home-Office und am Nachmittag 3 Stunden Büro mit 30 Minuten Pause gearbeitet werden") {
            // Block 1: 08:00–13:00 (300 min) Home-Office
            // Pause:   13:00–13:30 (30 min)
            // Block 2: 13:30–16:30 (180 min) Büro
            val mixedDay = WorkDay(
                id = 1L,
                date = LocalDate.of(2026, 9, 1),
                location = WorkLocation.HOME_OFFICE,
                dayType = DayType.WORK,
                timeBlocks = listOf(
                    TimeBlock(
                        id = 1L,
                        workDayId = 1L,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(13, 0),
                        location = WorkLocation.HOME_OFFICE
                    ),
                    TimeBlock(
                        id = 2L,
                        workDayId = 1L,
                        startTime = LocalTime.of(13, 30),
                        endTime = LocalTime.of(16, 30),
                        location = WorkLocation.OFFICE
                    )
                )
            )

            val status = calculateQuota(listOf(mixedDay), defaultSettings, testMonth)

            Then("zählt der Mischtag als Home-Office-Tag, da HO-Bruttozeit (300 Min) größer ist als Bürozeit (180 Min)") {
                status.officeDays shouldBe 0
                status.homeOfficeDays shouldBe 1
            }

            Then("wird die Nettoarbeitszeit dennoch proportional auf Büro und HO aufgeteilt") {
                status.officeMinutes shouldBe 180L
                status.homeOfficeMinutes shouldBe 300L
            }
        }

        When("exakt gleich viel Bürozeit und Home-Office-Zeit anfällt (je 4 Stunden)") {
            // 4h HO (08:00–12:00) und 4h Büro (12:30–16:30)
            val mixedDay = WorkDay(
                id = 1L,
                date = LocalDate.of(2026, 9, 1),
                location = WorkLocation.OFFICE,
                dayType = DayType.WORK,
                timeBlocks = listOf(
                    TimeBlock(
                        id = 1L,
                        workDayId = 1L,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(12, 0),
                        location = WorkLocation.HOME_OFFICE
                    ),
                    TimeBlock(
                        id = 2L,
                        workDayId = 1L,
                        startTime = LocalTime.of(12, 30),
                        endTime = LocalTime.of(16, 30),
                        location = WorkLocation.OFFICE
                    )
                )
            )

            val status = calculateQuota(listOf(mixedDay), defaultSettings, testMonth)

            Then("erhält bei Zeitgleichstand das Büro den Vorrang (officeDays = 1, homeOfficeDays = 0)") {
                status.officeDays shouldBe 1
                status.homeOfficeDays shouldBe 0
            }

            Then("werden die Minuten exakt hälftig verteilt (240 Min Büro, 240 Min Home-Office)") {
                status.officeMinutes shouldBe 240L
                status.homeOfficeMinutes shouldBe 240L
            }
        }

        When("ein automatischer gesetzlicher Pausenabzug die Nettozeit reduziert") {
            // Vormittags 3h (180m) HO, Nachmittags 5h (300m) Büro direkt anschließend ohne Pause
            // Brutto = 480m. Gesetzliche Pause (>6h) = 30m -> Netto = 450m
            // Büro-Netto: 300 * 450 / 480 = 281 min (5/8 von 450)
            // HO-Netto:   180 * 450 / 480 = 168 min (3/8 von 450)
            val mixedDay = WorkDay(
                id = 1L,
                date = LocalDate.of(2026, 9, 1),
                location = WorkLocation.OFFICE,
                dayType = DayType.WORK,
                timeBlocks = listOf(
                    TimeBlock(
                        id = 1L,
                        workDayId = 1L,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(11, 0),
                        location = WorkLocation.HOME_OFFICE
                    ),
                    TimeBlock(
                        id = 2L,
                        workDayId = 1L,
                        startTime = LocalTime.of(11, 0),
                        endTime = LocalTime.of(16, 0),
                        location = WorkLocation.OFFICE
                    )
                )
            )

            val status = calculateQuota(listOf(mixedDay), defaultSettings, testMonth)

            Then("zählt der Tag als Bürotag") {
                status.officeDays shouldBe 1
                status.homeOfficeDays shouldBe 0
            }

            Then("wird die verminderte Nettozeit exakt im Verhältnis der Bruttozeiten 5 zu 3 aufgeteilt") {
                status.officeMinutes shouldBe 281L
                status.homeOfficeMinutes shouldBe 168L
            }
        }
    }

    Given("ein Arbeitstag als Dienstreise (DayType.BUSINESS_TRIP)") {

        When("eine Dienstreise mit ungeraden Zeiten stattfindet (z.B. 09:00 bis 17:32)") {
            // 09:00 bis 17:32 = 8h 32m = 512 min brutto, 30 min Pause -> 482 min netto (8:02h)
            // Ohne 5-Min-Rundung bei Dienstreisen (sonst wäre 17:32 auf 17:35 gerundet worden)
            val tripDay = WorkDay(
                id = 1L,
                date = LocalDate.of(2026, 7, 21),
                location = WorkLocation.OFFICE,
                dayType = DayType.BUSINESS_TRIP,
                timeBlocks = listOf(
                    TimeBlock(
                        id = 1L,
                        workDayId = 1L,
                        startTime = LocalTime.of(9, 0),
                        endTime = LocalTime.of(17, 32),
                        location = WorkLocation.OFFICE,
                        isDuration = false
                    )
                )
            )

            val status = calculateQuota(listOf(tripDay), defaultSettings, YearMonth.of(2026, 7))

            Then("zählt der gesamte Tag als Bürotag (officeDays == 1, homeOfficeDays == 0)") {
                status.officeDays shouldBe 1
                status.homeOfficeDays shouldBe 0
            }

            Then("zählt die gesamte Nettozeit vollständig als Bürozeit ohne Abzug für Home-Office") {
                status.officeMinutes shouldBe 482L
                status.homeOfficeMinutes shouldBe 0L
            }
        }

        When("eine Dienstreise Blöcke mit unterschiedlichen Ortskennzeichnungen enthält") {
            // Auch wenn z.B. fälschlicherweise HOME_OFFICE an einem Block hinterlegt ist,
            // zählt eine Dienstreise vollständig als Bürozeit
            val tripDayWithHomeOfficeBlock = WorkDay(
                id = 1L,
                date = LocalDate.of(2026, 7, 22),
                location = WorkLocation.OFFICE,
                dayType = DayType.BUSINESS_TRIP,
                timeBlocks = listOf(
                    TimeBlock(
                        id = 1L,
                        workDayId = 1L,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(12, 0),
                        location = WorkLocation.HOME_OFFICE
                    ),
                    TimeBlock(
                        id = 2L,
                        workDayId = 1L,
                        startTime = LocalTime.of(12, 30),
                        endTime = LocalTime.of(16, 30),
                        location = WorkLocation.OFFICE
                    )
                )
            )

            val status = calculateQuota(listOf(tripDayWithHomeOfficeBlock), defaultSettings, YearMonth.of(2026, 7))

            Then("wird der gesamte Tag dennoch vollständig als Bürotag und Bürozeit angerechnet") {
                status.officeDays shouldBe 1
                status.homeOfficeDays shouldBe 0
                status.officeMinutes shouldBe 480L
                status.homeOfficeMinutes shouldBe 0L
            }
        }

        When("die Quotenberechnung eine Dienstreise delegiert") {
            val mockCalculator = mockk<CalculateDayWorkTimeUseCase>()
            val quotaUseCaseWithMock = CalculateQuotaUseCase(mockCalculator)

            val blocks = listOf(
                TimeBlock(
                    id = 1L,
                    workDayId = 1L,
                    startTime = LocalTime.of(9, 0),
                    endTime = LocalTime.of(17, 0),
                    location = WorkLocation.OFFICE
                )
            )
            val businessTripDay = WorkDay(
                id = 1L,
                date = LocalDate.of(2026, 7, 23),
                location = WorkLocation.OFFICE,
                dayType = DayType.BUSINESS_TRIP,
                timeBlocks = blocks
            )

            every {
                mockCalculator.invoke(blocks, isBusinessTrip = true)
            } returns DayWorkTimeResult(
                grossMinutes = 480L,
                netMinutes = 450L,
                breakMinutes = 30L,
                exceedsMaxHours = false
            )

            quotaUseCaseWithMock(listOf(businessTripDay), defaultSettings, YearMonth.of(2026, 7))

            Then("wird CalculateDayWorkTimeUseCase explizit mit isBusinessTrip=true aufgerufen") {
                verify(exactly = 1) {
                    mockCalculator.invoke(blocks, isBusinessTrip = true)
                }
            }
        }
    }
})
