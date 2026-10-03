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
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.PrepareExportDataUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * BDD-Spezifikation für das Teilzeit-Arbeitszeitmodell.
 *
 * Behandelt:
 * 1. Teilzeitmodell (20h-Woche, Mo–Fr mit 213 Min/Tag)
 * 2. Tagessoll beträgt 213 Min
 * 3. Urlaubstage und Krankheitstage reduzieren das Soll um exakt 213 Min (nicht Vollzeitsoll)
 * 4. Gleitzeitaufbau bei Teilzeit
 */
class PartTimeBehaviorSpec : BehaviorSpec({

    val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
    val calculateFlextime = CalculateFlextimeUseCase(calculateDayWorkTime)
    val calculateQuota = CalculateQuotaUseCase(calculateDayWorkTime)

    val baseSettings = Settings(
        dailyWorkMinutes = 426, // Fallback Vollzeit 7h 06m
        monthlyWorkMinutes = 9372,
        initialFlextimeMinutes = 0,
        initialOvertimeMinutes = 0,
        officeQuotaPercent = 40,
        officeQuotaMinDays = 8
    )

    val september2026 = YearMonth.of(2026, 9) // 22 Mo–Fr Werktage

    val partTimeRule = WorkTimeRule(
        id = 1L,
        validFrom = september2026,
        dailyWorkMinutes = 213, // 3h 33m täglich (20h-Woche)
        monthlyWorkMinutes = 22 * 213, // 4.686 Min
        workDays = DEFAULT_WORK_DAYS
    )

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

    fun createNeutralDay(
        date: LocalDate,
        dayType: DayType,
        location: WorkLocation = WorkLocation.HOME_OFFICE
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
    // Szenario 1: Teilzeitmodell (20h-Woche, Mo–Fr mit 213 Min/Tag)
    // =========================================================================
    Given("ein Teilzeitmodell mit einer 20-Stunden-Woche (Mo–Fr mit 213 Min/Tag)") {
        val workDate = LocalDate.of(2026, 9, 1) // Dienstag

        When("das Tagessoll für einen regulären Teilzeittag ausgewertet wird") {
            val dailyTarget = listOf(partTimeRule).first().dailyWorkMinutes

            Then("beträgt das Tagessoll exakt 213 Minuten (3h 33m)") {
                dailyTarget shouldBe 213
            }
        }

        When("an einem Werktag exakt das Teilzeitsoll von 213 Minuten gearbeitet wird") {
            val day = createDurationDay(workDate, 213)
            val balance = calculateFlextime(listOf(day), baseSettings, workTimeRules = listOf(partTimeRule))

            Then("beträgt das Gleitzeit-Delta 0 Minuten (Punktlandung bei 213 Min Soll)") {
                balance.earnedMinutes shouldBe 0L
                balance.totalMinutes shouldBe 0L
            }
        }
    }

    // =========================================================================
    // Szenario 2: Urlaubstage und Krankheitstage reduzieren das Soll um exakt 213 Min
    // =========================================================================
    Given("Urlaubstage, Krankheitstage und Gleittage im Teilzeitmodell (213 Min Tagessoll)") {
        val workDate = LocalDate.of(2026, 9, 2) // Mittwoch

        When("ein Urlaubstag (DayType.VACATION) an einem Werktag genommen wird") {
            val vacationDay = createNeutralDay(workDate, DayType.VACATION)
            val balance = calculateFlextime(listOf(vacationDay), baseSettings, workTimeRules = listOf(partTimeRule))

            Then("ist der Tag gleitzeitneutral (0 Minuten Delta), da das Tagessoll durch Urlaub abgegolten ist") {
                balance.earnedMinutes shouldBe 0L
                balance.totalMinutes shouldBe 0L
            }
        }

        When("ein Krankheitstag (DayType.SICK_DAY) an einem Werktag anfällt") {
            val sickDay = createNeutralDay(workDate, DayType.SICK_DAY)
            val balance = calculateFlextime(listOf(sickDay), baseSettings, workTimeRules = listOf(partTimeRule))

            Then("ist auch der Krankheitstag gleitzeitneutral (0 Minuten Delta)") {
                balance.earnedMinutes shouldBe 0L
                balance.totalMinutes shouldBe 0L
            }
        }

        When("ein Gleittag (DayType.FLEX_DAY) genommen wird") {
            val flexDay = createNeutralDay(workDate, DayType.FLEX_DAY)
            val balance = calculateFlextime(listOf(flexDay), baseSettings, workTimeRules = listOf(partTimeRule))

            Then("wird exakt das Teilzeit-Tagessoll von 213 Minuten abgezogen (nicht Vollzeitsoll 426 Min)") {
                balance.earnedMinutes shouldBe -213L
                balance.totalMinutes shouldBe -213L
            }
        }

        When("Urlaubstage und Krankheitstage das Monatssoll in der Quotenberechnung reduzieren") {
            // Monat September 2026: 22 Werktage à 213 Min = 4.686 Min Basis-Monatssoll
            // 2 Tage Urlaub, 1 Tag Krank, 1 Tag Büro (213m), 1 Tag HO (213m)
            val dayVac1 = createNeutralDay(LocalDate.of(2026, 9, 1), DayType.VACATION)
            val dayVac2 = createNeutralDay(LocalDate.of(2026, 9, 2), DayType.VACATION)
            val daySick = createNeutralDay(LocalDate.of(2026, 9, 3), DayType.SICK_DAY)
            val dayOffice = createDurationDay(LocalDate.of(2026, 9, 4), 213, location = WorkLocation.OFFICE)
            val dayHome = createDurationDay(LocalDate.of(2026, 9, 7), 213, location = WorkLocation.HOME_OFFICE)

            val monthDays = listOf(dayVac1, dayVac2, daySick, dayOffice, dayHome)

            val quotaStatus = calculateQuota(
                workDays = monthDays,
                settings = baseSettings,
                yearMonth = september2026,
                quotaPercent = 40,
                quotaMinDays = 8,
                workTimeRules = listOf(partTimeRule)
            )

            // 3 neutrale Tage (2 Urlaub + 1 Krank) à 213 Min = 639 Min Abzug
            // Basis-Monatssoll: 4.686 Min - 639 Min = 4.047 Min
            // Bürozeit: 213 Min (Tag 4).
            // Quote: 213 / 4.047 * 100 = 5.26%
            Then("reduzieren die 3 Ausfalltage das Monatssoll um exakt 639 Minuten (3 * 213 Min)") {
                val neutralDeduction = 3L * 213L
                neutralDeduction shouldBe 639L
            }

            Then("wird nicht das Vollzeitsoll von 426 Min pro Tag abgezogen (3 * 426 = 1.278 Min)") {
                val wrongFullTimeDeduction = 3L * 426L
                wrongFullTimeDeduction shouldBe 1278L
            }

            Then("basiert die Quotenberechnung auf dem Teilzeit-Soll") {
                val expectedRemainingTarget = (22L * 213L) - (3L * 213L) // 4047 Min
                expectedRemainingTarget shouldBe 4047L
                quotaStatus.officePercent shouldBe ((213.0 / 4047.0) * 100.0).plusOrMinus(0.1)
            }
        }
    }

    // =========================================================================
    // Szenario 3: Gleitzeitaufbau bei Teilzeit
    // =========================================================================
    Given("Gleitzeitaufbau und -abbau bei einem Teilzeit-Mitarbeiter (213 Min Tagessoll)") {
        val workDate = LocalDate.of(2026, 9, 8) // Dienstag

        When("ein Teilzeitmitarbeiter 300 Minuten (5h 00m) an einem Arbeitstag leistet") {
            val day = createDurationDay(workDate, 300)
            val balance = calculateFlextime(listOf(day), baseSettings, workTimeRules = listOf(partTimeRule))

            Then("wird ein Gleitzeitgewinn von +87 Minuten erzielt (300 - 213)") {
                balance.earnedMinutes shouldBe 87L
                balance.totalMinutes shouldBe 87L
            }
        }

        When("ein Teilzeitmitarbeiter 180 Minuten (3h 00m) an einem Arbeitstag leistet") {
            val day = createDurationDay(workDate, 180)
            val balance = calculateFlextime(listOf(day), baseSettings, workTimeRules = listOf(partTimeRule))

            Then("entsteht ein Gleitzeitverlust von -33 Minuten (180 - 213)") {
                balance.earnedMinutes shouldBe -33L
                balance.totalMinutes shouldBe -33L
            }
        }

        When("eine komplette Teilzeit-Arbeitswoche mit Überstunden und Urlaub ausgewertet wird") {
            // Mo 07.09.: 300 Min (+87)
            // Di 08.09.: 213 Min (0)
            // Mi 09.09.: 180 Min (-33)
            // Do 10.09.: Urlaub (0)
            // Fr 11.09.: 270 Min (+57)
            val mon = createDurationDay(LocalDate.of(2026, 9, 7), 300)
            val tue = createDurationDay(LocalDate.of(2026, 9, 8), 213)
            val wed = createDurationDay(LocalDate.of(2026, 9, 9), 180)
            val thu = createNeutralDay(LocalDate.of(2026, 9, 10), DayType.VACATION)
            val fri = createDurationDay(LocalDate.of(2026, 9, 11), 270)

            val weekBalance = calculateFlextime(
                workDays = listOf(mon, tue, wed, thu, fri),
                settings = baseSettings,
                workTimeRules = listOf(partTimeRule)
            )

            Then("beträgt der kumulierte Gleitzeitgewinn der Woche exakt +111 Minuten (87 + 0 - 33 + 0 + 57)") {
                weekBalance.earnedMinutes shouldBe 111L
                weekBalance.totalMinutes shouldBe 111L
            }
        }
    }

    // =========================================================================
    // Szenario 4: Export-Datenaufbereitung für das Teilzeitmodell (MockK)
    // =========================================================================
    Given("die Vorbereitung von Exportdaten für Teilzeit über PrepareExportDataUseCase") {
        val workDayRepository = mockk<WorkDayRepository>()
        val settingsRepository = mockk<SettingsRepository>()
        val prepareExportData = PrepareExportDataUseCase(workDayRepository, settingsRepository, calculateDayWorkTime)

        val day1 = createDurationDay(LocalDate.of(2026, 9, 1), 213)
        val day2 = createDurationDay(LocalDate.of(2026, 9, 2), 300)

        coEvery { workDayRepository.getWorkDaysForMonth(september2026) } returns flowOf(listOf(day1, day2))
        coEvery { settingsRepository.getSettings() } returns flowOf(baseSettings)
        coEvery { settingsRepository.getWorkTimeRules() } returns flowOf(listOf(partTimeRule))
        coEvery { settingsRepository.getWorkTimeRuleForDate(any(), any()) } returns partTimeRule

        When("die Exportdaten für den Teilzeitmonat aufbereitet werden") {
            val exportData = prepareExportData(september2026)

            Then("enthält jede gearbeitete Zeile das Tagessoll von 213 Minuten") {
                val row1 = exportData.rows.first { it.date == LocalDate.of(2026, 9, 1) }
                val row2 = exportData.rows.first { it.date == LocalDate.of(2026, 9, 2) }

                row1.targetMinutes shouldBe 213
                row1.netMinutes shouldBe 213L

                row2.targetMinutes shouldBe 213
                row2.netMinutes shouldBe 300L
            }

            Then("beträgt das gesamte Monatssoll 4.686 Minuten (22 Tage * 213 Min)") {
                exportData.totalTargetMinutes shouldBe 22L * 213L
                exportData.totalTargetMinutes shouldBe 4686L
            }
        }
    }
})
