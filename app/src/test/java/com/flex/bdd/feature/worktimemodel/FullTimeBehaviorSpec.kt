package com.flex.bdd.feature.worktimemodel

import com.flex.domain.model.DEFAULT_WORK_DAYS
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.PrepareExportDataUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * BDD-Spezifikation für das Vollzeit-Arbeitszeitmodell.
 *
 * Behandelt:
 * 1. 40h-Woche (Mo–Fr, 426 Min/Tag bzw. 480 Min/Tag je nach Settings)
 * 2. Monatssoll-Berechnung über Werktage (unter Berücksichtigung von Feiertagen)
 * 3. Überstunden und Gleitzeit bei Vollzeit
 */
class FullTimeBehaviorSpec : BehaviorSpec({

    val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
    val calculateFlextime = CalculateFlextimeUseCase(calculateDayWorkTime)

    fun createDurationDay(
        date: LocalDate,
        minutes: Long,
        dayType: DayType = DayType.WORK,
        location: WorkLocation = WorkLocation.OFFICE
    ): WorkDay {
        val start = LocalTime.of(8, 0)
        return WorkDay(
            id = date.toEpochDay(),
            date = date,
            dayType = dayType,
            location = location,
            timeBlocks = listOf(
                TimeBlock(
                    id = date.toEpochDay(),
                    workDayId = date.toEpochDay(),
                    startTime = start,
                    endTime = start.plusMinutes(minutes),
                    isDuration = true,
                    location = location
                )
            )
        )
    }

    fun createClockDay(
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        dayType: DayType = DayType.WORK,
        location: WorkLocation = WorkLocation.OFFICE
    ): WorkDay {
        return WorkDay(
            id = date.toEpochDay(),
            date = date,
            dayType = dayType,
            location = location,
            timeBlocks = listOf(
                TimeBlock(
                    id = date.toEpochDay(),
                    workDayId = date.toEpochDay(),
                    startTime = startTime,
                    endTime = endTime,
                    isDuration = false,
                    location = location
                )
            )
        )
    }

    fun createNeutralDay(
        date: LocalDate,
        dayType: DayType,
        location: WorkLocation = WorkLocation.OFFICE
    ): WorkDay {
        return WorkDay(
            id = date.toEpochDay(),
            date = date,
            dayType = dayType,
            location = location,
            timeBlocks = emptyList()
        )
    }

    // =========================================================================
    // Szenario 1: 40h-Woche (Mo–Fr, 426 Min/Tag bzw. 480 Min/Tag je nach Settings)
    // =========================================================================
    Given("ein Vollzeit-Arbeitszeitmodell mit Standard-Einstellungen (426 Min/Tag bzw. 7h 06min)") {
        val standardSettings = Settings(
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = 9372, // 22 Tage * 426 Min
            initialFlextimeMinutes = 0,
            initialOvertimeMinutes = 0
        )
        val workDate = LocalDate.of(2026, 9, 1) // Dienstag

        When("an einem Werktag exakt das Tagessoll von 426 Minuten gearbeitet wird") {
            val day = createDurationDay(workDate, 426)
            val balance = calculateFlextime(listOf(day), standardSettings)

            Then("ist das Gleitzeitsaldo ausgeglichen (0 Minuten Delta)") {
                balance.earnedMinutes shouldBe 0L
                balance.totalMinutes shouldBe 0L
            }
        }

        When("an einem Werktag 480 Minuten (8:00h) gearbeitet werden") {
            val day = createDurationDay(workDate, 480)
            val balance = calculateFlextime(listOf(day), standardSettings)

            Then("entsteht ein Gleitzeitgewinn von +54 Minuten (480 - 426)") {
                balance.earnedMinutes shouldBe 54L
                balance.totalMinutes shouldBe 54L
            }
        }

        When("an einem Werktag 360 Minuten (6:00h) gearbeitet werden") {
            val day = createDurationDay(workDate, 360)
            val balance = calculateFlextime(listOf(day), standardSettings)

            Then("entsteht ein Gleitzeitverlust von -66 Minuten (360 - 426)") {
                balance.earnedMinutes shouldBe -66L
                balance.totalMinutes shouldBe -66L
            }
        }
    }

    Given("ein Vollzeit-Arbeitszeitmodell mit 40-Stunden-Woche (480 Min/Tag bzw. 8:00h täglich)") {
        val fortyHourSettings = Settings(
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 10560, // 22 Tage * 480 Min
            initialFlextimeMinutes = 0,
            initialOvertimeMinutes = 0
        )
        val fortyHourRule = WorkTimeRule(
            id = 1L,
            validFrom = YearMonth.of(2026, 9),
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 10560,
            workDays = DEFAULT_WORK_DAYS
        )
        val workDate = LocalDate.of(2026, 9, 2) // Mittwoch

        When("an einem Werktag genau 480 Minuten (8:00h Netto) gearbeitet werden") {
            val day = createDurationDay(workDate, 480)
            val balance = calculateFlextime(listOf(day), fortyHourSettings, workTimeRules = listOf(fortyHourRule))

            Then("beträgt das Gleitzeit-Delta 0 Minuten (Punktlandung bei 8:00h Soll)") {
                balance.earnedMinutes shouldBe 0L
                balance.totalMinutes shouldBe 0L
            }
        }

        When("an einem Werktag mit Einstempeln 8:30h Netto gearbeitet wird (08:30 bis 17:30 mit 30 Min Pause)") {
            val day = createClockDay(workDate, LocalTime.of(8, 30), LocalTime.of(17, 30))
            val balance = calculateFlextime(listOf(day), fortyHourSettings, workTimeRules = listOf(fortyHourRule))

            Then("wird ein Gleitzeitgewinn von +30 Minuten erzielt (510 Netto - 480 Soll)") {
                balance.earnedMinutes shouldBe 30L
                balance.totalMinutes shouldBe 30L
            }
        }

        When("an einem Werktag mit Einstempeln 7:00h Netto gearbeitet wird (09:00 bis 16:30 mit 30 Min Pause)") {
            val day = createClockDay(workDate, LocalTime.of(9, 0), LocalTime.of(16, 30))
            val balance = calculateFlextime(listOf(day), fortyHourSettings, workTimeRules = listOf(fortyHourRule))

            Then("entsteht ein Gleitzeitverlust von -60 Minuten (420 Netto - 480 Soll)") {
                balance.earnedMinutes shouldBe -60L
                balance.totalMinutes shouldBe -60L
            }
        }
    }

    // =========================================================================
    // Szenario 2: Monatssoll-Berechnung über Werktage
    // =========================================================================
    Given("die Berechnung des dynamischen Monatssolls über Werktage (Mo–Fr)") {
        val september2026 = YearMonth.of(2026, 9) // 30 Tage, 22 Mo–Fr Werktage, 0 Feiertage
        val may2026 = YearMonth.of(2026, 5) // 31 Tage, 21 Mo–Fr Werktage, 3 gesetzliche Feiertage (1. Mai, Himmelfahrt, Pfingstmontag)

        When("das Monatssoll für September 2026 mit Standard-Settings (426 Min/Tag) berechnet wird") {
            val settings = Settings(dailyWorkMinutes = 426, monthlyWorkMinutes = 9372)
            val balance = calculateFlextime(emptyList(), settings, yearMonth = september2026)

            Then("beträgt das Monatssoll exakt 9.372 Minuten (22 Werktage * 426 Min = 156h 12m)") {
                balance.targetMinutes shouldBe 22L * 426L
                balance.targetMinutes shouldBe 9372L
            }
        }

        When("das Monatssoll für September 2026 mit 40h-Woche (480 Min/Tag) berechnet wird") {
            val rule40h = WorkTimeRule(
                id = 1L,
                validFrom = september2026,
                dailyWorkMinutes = 480,
                monthlyWorkMinutes = 10560,
                workDays = DEFAULT_WORK_DAYS
            )
            val balance = calculateFlextime(
                workDays = emptyList(),
                settings = Settings(dailyWorkMinutes = 480, monthlyWorkMinutes = 10560),
                yearMonth = september2026,
                workTimeRules = listOf(rule40h)
            )

            Then("beträgt das Monatssoll exakt 10.560 Minuten (22 Werktage * 480 Min = 176h 00m)") {
                balance.targetMinutes shouldBe 22L * 480L
                balance.targetMinutes shouldBe 10560L
            }
        }

        When("das Monatssoll für Mai 2026 mit gesetzlichen Feiertagen bei 480 Min/Tag berechnet wird") {
            val rule40hMay = WorkTimeRule(
                id = 2L,
                validFrom = YearMonth.of(2026, 1),
                dailyWorkMinutes = 480,
                monthlyWorkMinutes = 0,
                workDays = DEFAULT_WORK_DAYS
            )
            val balance = calculateFlextime(
                workDays = emptyList(),
                settings = Settings(dailyWorkMinutes = 480, monthlyWorkMinutes = 0),
                yearMonth = may2026,
                workTimeRules = listOf(rule40hMay)
            )

            Then("reduzieren die 3 Feiertage an Werktagen das Soll von 21 auf 18 Arbeitstage (18 * 480 = 8.640 Min)") {
                // Mai 2026: 21 Mo-Fr Tage - 3 Feiertage = 18 Netto-Tage
                balance.targetMinutes shouldBe 18L * 480L
                balance.targetMinutes shouldBe 8640L
            }
        }
    }

    // =========================================================================
    // Szenario 3: Überstunden und Gleitzeit bei Vollzeit
    // =========================================================================
    Given("Überstunden- und Gleitzeitbuchungen im 40h-Vollzeitmodell (480 Min/Tag)") {
        val settings40h = Settings(
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 10560,
            initialFlextimeMinutes = 100,
            initialOvertimeMinutes = 500
        )
        val rule40h = WorkTimeRule(
            id = 1L,
            validFrom = YearMonth.of(2026, 9),
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 10560,
            workDays = DEFAULT_WORK_DAYS
        )

        When("an regulären Wochentagen Mehrarbeit geleistet wird (z.B. Tag 1: +60 Min, Tag 2: +20 Min)") {
            val day1 = createDurationDay(LocalDate.of(2026, 9, 1), 540) // 540 - 480 = +60 Min
            val day2 = createDurationDay(LocalDate.of(2026, 9, 2), 500) // 500 - 480 = +20 Min
            val balance = calculateFlextime(listOf(day1, day2), settings40h, workTimeRules = listOf(rule40h))

            Then("wird die gesamte Mehrarbeit auf das Gleitzeitkonto gebucht (+80 Min)") {
                balance.earnedMinutes shouldBe 80L
                balance.totalMinutes shouldBe 180L // 100 initial + 80
            }

            Then("bleibt das Überstundenkonto unverändert") {
                balance.earnedOvertimeMinutes shouldBe 0L
                balance.overtimeMinutes shouldBe 500L
            }
        }

        When("am Samstag gearbeitet wird mit Samstagsbonus (DayType.SATURDAY_BONUS) für 480 Minuten") {
            val saturday = LocalDate.of(2026, 9, 5) // Samstag
            val saturdayBonusDay = createDurationDay(saturday, 480, dayType = DayType.SATURDAY_BONUS)
            val balance = calculateFlextime(listOf(saturdayBonusDay), settings40h, workTimeRules = listOf(rule40h))

            Then("wird die volle Arbeitszeit (480 Min) der Gleitzeit gutgeschrieben") {
                balance.earnedMinutes shouldBe 480L
                balance.totalMinutes shouldBe 580L // 100 initial + 480
            }

            Then("werden 50 Prozent Bonus (240 Min) als Überstunden verbucht") {
                balance.earnedOvertimeMinutes shouldBe 240L
                balance.overtimeMinutes shouldBe 740L // 500 initial + 240
            }
        }

        When("ein ganztägiger Überstundentag (DayType.OVERTIME_DAY) genommen wird") {
            val overtimeDay = createNeutralDay(LocalDate.of(2026, 9, 3), DayType.OVERTIME_DAY)
            val balance = calculateFlextime(listOf(overtimeDay), settings40h, workTimeRules = listOf(rule40h))

            Then("wird das Überstundenkonto um das volle Tagessoll (-480 Min) reduziert") {
                balance.earnedOvertimeMinutes shouldBe -480L
                balance.overtimeMinutes shouldBe 20L // 500 initial - 480
            }

            Then("bleibt das Gleitzeitkonto unberührt (0 Min Abzug)") {
                balance.earnedMinutes shouldBe 0L
                balance.totalMinutes shouldBe 100L
            }
        }

        When("ein Gleittag (DayType.FLEX_DAY) genommen wird") {
            val flexDay = createNeutralDay(LocalDate.of(2026, 9, 4), DayType.FLEX_DAY)
            val balance = calculateFlextime(listOf(flexDay), settings40h, workTimeRules = listOf(rule40h))

            Then("wird das Gleitzeitkonto um das volle Tagessoll (-480 Min) reduziert") {
                balance.earnedMinutes shouldBe -480L
                balance.totalMinutes shouldBe -380L // 100 initial - 480
            }

            Then("bleibt das Überstundenkonto unberührt") {
                balance.earnedOvertimeMinutes shouldBe 0L
                balance.overtimeMinutes shouldBe 500L
            }
        }
    }
})
